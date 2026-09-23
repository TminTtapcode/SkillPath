CREATE TABLE learning_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    goal_id BIGINT NOT NULL,
    sequence_id BIGINT NOT NULL,
    graph_version_id BIGINT NOT NULL,
    title_snapshot VARCHAR(180) NOT NULL,
    title_vi_snapshot VARCHAR(180) NOT NULL,
    assignment_source VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    active_goal_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN goal_id ELSE NULL END) STORED,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_learning_session_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT fk_learning_session_goal FOREIGN KEY(goal_id) REFERENCES user_goals(id),
    CONSTRAINT fk_learning_session_sequence FOREIGN KEY(sequence_id) REFERENCES learning_sequences(id),
    CONSTRAINT fk_learning_session_graph FOREIGN KEY(graph_version_id) REFERENCES knowledge_graph_versions(id),
    CONSTRAINT uk_learning_active_goal UNIQUE(active_goal_id),
    CONSTRAINT ck_learning_session_source CHECK(assignment_source IN ('LEARNER_SELECTED','PLANNER')),
    CONSTRAINT ck_learning_session_status CHECK(status IN ('ACTIVE','COMPLETED','STOPPED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_learning_session_owner_status ON learning_sessions(user_id,status,id);

CREATE TABLE learning_tasks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    goal_id BIGINT NOT NULL,
    position INT NOT NULL,
    task_template_version_id BIGINT NOT NULL,
    assignment_source VARCHAR(20) NOT NULL,
    planner_decision_id BIGINT NULL,
    payload_snapshot JSON NOT NULL,
    planned_minutes INT NOT NULL,
    actual_minutes INT NULL,
    status VARCHAR(20) NOT NULL,
    assigned_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_learning_task_session FOREIGN KEY(session_id) REFERENCES learning_sessions(id),
    CONSTRAINT fk_learning_task_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT fk_learning_task_goal FOREIGN KEY(goal_id) REFERENCES user_goals(id),
    CONSTRAINT fk_learning_execution_template FOREIGN KEY(task_template_version_id) REFERENCES task_template_versions(id),
    CONSTRAINT uk_learning_task_position UNIQUE(session_id,position),
    CONSTRAINT uk_learning_task_template_session UNIQUE(session_id,task_template_version_id),
    CONSTRAINT ck_learning_task_assignment CHECK(
        (assignment_source = 'LEARNER_SELECTED' AND planner_decision_id IS NULL) OR
        (assignment_source = 'PLANNER' AND planner_decision_id IS NOT NULL)),
    CONSTRAINT ck_learning_task_execution_status CHECK(status IN ('ASSIGNED','IN_PROGRESS','BLOCKED','COMPLETED','SKIPPED','ABANDONED','EXPIRED')),
    CONSTRAINT ck_learning_task_planned_minutes CHECK(planned_minutes > 0),
    CONSTRAINT ck_learning_task_actual_minutes CHECK(actual_minutes IS NULL OR actual_minutes BETWEEN 0 AND 360)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_learning_task_owner_session ON learning_tasks(user_id,session_id,position);

CREATE TABLE learning_task_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    command_id BIGINT NOT NULL,
    from_status VARCHAR(20) NOT NULL,
    to_status VARCHAR(20) NOT NULL,
    reason_code VARCHAR(40) NULL,
    occurred_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_learning_event_task FOREIGN KEY(task_id) REFERENCES learning_tasks(id),
    CONSTRAINT fk_learning_event_actor FOREIGN KEY(actor_user_id) REFERENCES users(id),
    CONSTRAINT uk_learning_event_command UNIQUE(command_id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_learning_event_task_time ON learning_task_events(task_id,occurred_at,id);

CREATE TABLE learning_command_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_name VARCHAR(30) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    resource_id BIGINT NOT NULL,
    outcome_status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_learning_receipt_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT uk_learning_receipt_key UNIQUE(user_id,idempotency_key)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER TABLE learning_task_events
    ADD CONSTRAINT fk_learning_event_command FOREIGN KEY(command_id) REFERENCES learning_command_receipts(id);
