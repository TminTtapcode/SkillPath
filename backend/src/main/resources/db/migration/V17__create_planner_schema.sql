CREATE TABLE planner_policies (
    version VARCHAR(80) NOT NULL PRIMARY KEY,
    definition JSON NOT NULL,
    created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

INSERT INTO planner_policies(version,definition,created_at) VALUES
('planner-v1',JSON_OBJECT('prerequisiteThreshold',0.75,'hardStrength',0.8,
 'candidateLimit',100,'timeToleranceMinutes',0,'reviewMaxDays',30,
 'learnPrior',0.30,'practicePrior',0.35,'recallPrior',0.20),UTC_TIMESTAMP(6));

CREATE TABLE planning_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    goal_id BIGINT NOT NULL,
    graph_version_id BIGINT NOT NULL,
    policy_version VARCHAR(80) NOT NULL,
    projection_as_of DATETIME(6) NOT NULL,
    progress_digest CHAR(64) NOT NULL,
    review_digest CHAR(64) NOT NULL,
    input_hash CHAR(64) NOT NULL,
    input_payload JSON NOT NULL,
    candidate_count INT NOT NULL,
    limited_candidate_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_planner_snapshot_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT fk_planner_snapshot_goal FOREIGN KEY(goal_id) REFERENCES user_goals(id),
    CONSTRAINT fk_planner_snapshot_graph FOREIGN KEY(graph_version_id) REFERENCES knowledge_graph_versions(id),
    CONSTRAINT fk_planner_snapshot_policy FOREIGN KEY(policy_version) REFERENCES planner_policies(version),
    CONSTRAINT ck_planner_candidate_counts CHECK(candidate_count>=0 AND limited_candidate_count BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_planner_snapshot_owner ON planning_snapshots(user_id,goal_id,id);

CREATE TABLE planner_decisions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    snapshot_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    template_version_id BIGINT NOT NULL,
    priority_score DECIMAL(6,2) NOT NULL,
    signal_payload JSON NOT NULL,
    reason_payload JSON NOT NULL,
    alternatives_payload JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_planner_decision_snapshot FOREIGN KEY(snapshot_id) REFERENCES planning_snapshots(id),
    CONSTRAINT fk_planner_decision_template FOREIGN KEY(template_version_id) REFERENCES task_template_versions(id),
    CONSTRAINT ck_planner_decision_score CHECK(priority_score BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE planner_decision_candidates (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    snapshot_id BIGINT NOT NULL,
    position INT NOT NULL,
    node_id BIGINT NOT NULL,
    template_version_id BIGINT NULL,
    score DECIMAL(6,2) NULL,
    eligible BOOLEAN NOT NULL,
    explanation JSON NOT NULL,
    CONSTRAINT fk_planner_candidate_snapshot FOREIGN KEY(snapshot_id) REFERENCES planning_snapshots(id),
    CONSTRAINT uk_planner_candidate_position UNIQUE(snapshot_id,position)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE daily_plans (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    goal_id BIGINT NOT NULL,
    learning_day DATE NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    budget_minutes INT NOT NULL,
    revision INT NOT NULL,
    supersedes_plan_id BIGINT NULL,
    snapshot_id BIGINT NOT NULL,
    session_id BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    outcome_code VARCHAR(50) NOT NULL,
    current_day DATE GENERATED ALWAYS AS
      (CASE WHEN status IN ('CURRENT','NO_SAFE') THEN learning_day ELSE NULL END) STORED,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_daily_plan_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT fk_daily_plan_goal FOREIGN KEY(goal_id) REFERENCES user_goals(id),
    CONSTRAINT fk_daily_plan_snapshot FOREIGN KEY(snapshot_id) REFERENCES planning_snapshots(id),
    CONSTRAINT fk_daily_plan_previous FOREIGN KEY(supersedes_plan_id) REFERENCES daily_plans(id),
    CONSTRAINT fk_daily_plan_session FOREIGN KEY(session_id) REFERENCES learning_sessions(id),
    CONSTRAINT uk_daily_plan_revision UNIQUE(user_id,goal_id,learning_day,revision),
    CONSTRAINT uk_daily_plan_current UNIQUE(user_id,goal_id,current_day),
    CONSTRAINT ck_daily_plan_status CHECK(status IN ('CURRENT','NO_SAFE','SUPERSEDED')),
    CONSTRAINT ck_daily_plan_budget CHECK(budget_minutes BETWEEN 1 AND 360),
    CONSTRAINT ck_daily_plan_revision CHECK(revision>0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_daily_plan_owner_day ON daily_plans(user_id,goal_id,learning_day,revision);

CREATE TABLE daily_plan_items (
    plan_id BIGINT NOT NULL,
    position INT NOT NULL,
    decision_id BIGINT NOT NULL,
    learning_task_id BIGINT NOT NULL,
    estimated_minutes INT NOT NULL,
    PRIMARY KEY(plan_id,position),
    CONSTRAINT fk_daily_item_plan FOREIGN KEY(plan_id) REFERENCES daily_plans(id),
    CONSTRAINT fk_daily_item_decision FOREIGN KEY(decision_id) REFERENCES planner_decisions(id),
    CONSTRAINT fk_daily_item_task FOREIGN KEY(learning_task_id) REFERENCES learning_tasks(id),
    CONSTRAINT uk_daily_item_decision UNIQUE(decision_id),
    CONSTRAINT uk_daily_item_task UNIQUE(learning_task_id),
    CONSTRAINT ck_daily_item_minutes CHECK(estimated_minutes>0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE planner_command_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_name VARCHAR(20) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    plan_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_planner_receipt_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT fk_planner_receipt_plan FOREIGN KEY(plan_id) REFERENCES daily_plans(id),
    CONSTRAINT uk_planner_receipt_key UNIQUE(user_id,idempotency_key)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
