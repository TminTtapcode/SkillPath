-- Receipts are owned by the consumer of each event. They make a multi-evidence
-- assessment attempt visible to Review only after every evidence row projects.
CREATE TABLE progress_attempt_projections (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    attempt_kind VARCHAR(30) NOT NULL,
    attempt_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    goal_id BIGINT NOT NULL,
    graph_version_id BIGINT NOT NULL,
    expected_count INT NOT NULL,
    replan_eligible BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_progress_attempt_projection UNIQUE (attempt_kind, attempt_id),
    CONSTRAINT fk_progress_attempt_projection_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_progress_attempt_projection_goal FOREIGN KEY (goal_id) REFERENCES user_goals(id),
    CONSTRAINT fk_progress_attempt_projection_graph FOREIGN KEY (graph_version_id) REFERENCES knowledge_graph_versions(id),
    CONSTRAINT ck_progress_attempt_projection_kind CHECK (attempt_kind IN ('DIAGNOSTIC', 'TASK_CHECK')),
    CONSTRAINT ck_progress_attempt_projection_count CHECK (expected_count BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE progress_attempt_evidence_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    projection_id BIGINT NOT NULL,
    source_event_id BIGINT NOT NULL,
    evidence_id BIGINT NOT NULL,
    knowledge_node_id BIGINT NOT NULL,
    dimension VARCHAR(30) NOT NULL,
    score DECIMAL(5,4) NOT NULL,
    reliability DECIMAL(5,4) NOT NULL,
    projected_status VARCHAR(30) NOT NULL,
    projected_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_progress_attempt_evidence UNIQUE (projection_id, evidence_id),
    CONSTRAINT uk_progress_attempt_event UNIQUE (source_event_id),
    CONSTRAINT fk_progress_attempt_evidence_projection FOREIGN KEY (projection_id) REFERENCES progress_attempt_projections(id),
    CONSTRAINT ck_progress_attempt_evidence_score CHECK (score BETWEEN 0 AND 1),
    CONSTRAINT ck_progress_attempt_evidence_reliability CHECK (reliability BETWEEN 0 AND 1)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE review_processed_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    attempt_kind VARCHAR(30) NOT NULL,
    attempt_id BIGINT NOT NULL,
    source_event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    graph_version_id BIGINT NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_review_processed_attempt UNIQUE (attempt_kind, attempt_id),
    CONSTRAINT uk_review_processed_event UNIQUE (source_event_id),
    CONSTRAINT fk_review_processed_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_review_processed_graph FOREIGN KEY (graph_version_id) REFERENCES knowledge_graph_versions(id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE planner_replan_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    attempt_kind VARCHAR(30) NOT NULL,
    attempt_id BIGINT NOT NULL,
    source_event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    goal_id BIGINT NOT NULL,
    graph_version_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL,
    last_error_code VARCHAR(120) NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    CONSTRAINT uk_planner_replan_attempt UNIQUE (attempt_kind, attempt_id),
    CONSTRAINT uk_planner_replan_event UNIQUE (source_event_id),
    CONSTRAINT fk_planner_replan_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_planner_replan_goal FOREIGN KEY (goal_id) REFERENCES user_goals(id),
    CONSTRAINT fk_planner_replan_graph FOREIGN KEY (graph_version_id) REFERENCES knowledge_graph_versions(id),
    CONSTRAINT ck_planner_replan_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_planner_replan_attempts CHECK (attempt_count >= 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_planner_replan_pending ON planner_replan_requests(status, available_at, id);
