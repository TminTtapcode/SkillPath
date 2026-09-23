INSERT INTO planner_policies(version, definition, created_at) VALUES
('planner-v2', JSON_OBJECT('basePolicy', 'planner-v1',
 'evaluatedFailureThreshold', 0.60, 'repeatedFailures', 2,
 'preservationPolicy', 'adaptive-replan-v1'), UTC_TIMESTAMP(6));

-- A task belongs to exactly one original decision/item. Later revisions refer
-- to that immutable origin without violating V17's unique task/decision keys.
CREATE TABLE daily_plan_carry_forwards (
    plan_id BIGINT NOT NULL,
    position INT NOT NULL,
    origin_plan_id BIGINT NOT NULL,
    origin_decision_id BIGINT NOT NULL,
    learning_session_id BIGINT NOT NULL,
    learning_task_id BIGINT NOT NULL,
    status_at_revision VARCHAR(20) NOT NULL,
    planned_minutes INT NOT NULL,
    actual_minutes_at_revision INT NULL,
    PRIMARY KEY (plan_id, learning_task_id),
    CONSTRAINT uk_daily_carry_position UNIQUE (plan_id, position),
    CONSTRAINT fk_daily_carry_plan FOREIGN KEY (plan_id) REFERENCES daily_plans(id),
    CONSTRAINT fk_daily_carry_origin FOREIGN KEY (origin_plan_id) REFERENCES daily_plans(id),
    CONSTRAINT fk_daily_carry_decision FOREIGN KEY (origin_decision_id) REFERENCES planner_decisions(id),
    CONSTRAINT fk_daily_carry_session FOREIGN KEY (learning_session_id) REFERENCES learning_sessions(id),
    CONSTRAINT fk_daily_carry_task FOREIGN KEY (learning_task_id) REFERENCES learning_tasks(id),
    CONSTRAINT ck_daily_carry_status CHECK (status_at_revision IN ('COMPLETED', 'IN_PROGRESS', 'BLOCKED')),
    CONSTRAINT ck_daily_carry_minutes CHECK (planned_minutes > 0 AND
        (actual_minutes_at_revision IS NULL OR actual_minutes_at_revision BETWEEN 0 AND 360))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE daily_budget_overrides (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    goal_id BIGINT NOT NULL,
    learning_day DATE NOT NULL,
    revision INT NOT NULL,
    available_minutes INT NOT NULL,
    supersedes_override_id BIGINT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_budget_override_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_budget_override_goal FOREIGN KEY (goal_id) REFERENCES user_goals(id),
    CONSTRAINT fk_budget_override_previous FOREIGN KEY (supersedes_override_id) REFERENCES daily_budget_overrides(id),
    CONSTRAINT uk_budget_override_day_revision UNIQUE (user_id, goal_id, learning_day, revision),
    CONSTRAINT uk_budget_override_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_budget_override_minutes CHECK (available_minutes BETWEEN 1 AND 180),
    CONSTRAINT ck_budget_override_revision CHECK (revision > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_budget_override_day ON daily_budget_overrides(user_id, goal_id, learning_day, revision);

-- Review events retain their source outbox ID. Planner-owned day commands have
-- no outbox event; their unique identity is (attempt_kind, attempt_id).
ALTER TABLE planner_replan_requests
    MODIFY COLUMN source_event_id BIGINT NULL,
    ADD CONSTRAINT ck_planner_replan_source CHECK (
        (attempt_kind IN ('DIAGNOSTIC', 'TASK_CHECK') AND source_event_id IS NOT NULL) OR
        (attempt_kind = 'TIME_OVERRIDE' AND source_event_id IS NULL)
    );
