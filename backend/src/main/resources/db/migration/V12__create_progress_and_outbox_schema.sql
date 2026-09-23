ALTER TABLE outbox_events DROP CHECK ck_outbox_events_status;
ALTER TABLE outbox_events
    ADD COLUMN event_version INT NOT NULL DEFAULT 1 AFTER event_type,
    ADD COLUMN available_at DATETIME(6) NULL AFTER attempt_count,
    ADD COLUMN locked_at DATETIME(6) NULL AFTER available_at,
    ADD COLUMN locked_until DATETIME(6) NULL AFTER locked_at,
    ADD COLUMN locked_by VARCHAR(120) NULL AFTER locked_until,
    ADD COLUMN last_error_code VARCHAR(120) NULL AFTER locked_by,
    ADD CONSTRAINT ck_outbox_events_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED')),
    ADD CONSTRAINT ck_outbox_events_version CHECK (event_version > 0);

UPDATE outbox_events SET available_at = occurred_at WHERE available_at IS NULL;
ALTER TABLE outbox_events MODIFY available_at DATETIME(6) NOT NULL;
CREATE INDEX ix_outbox_events_dispatch
    ON outbox_events (status, available_at, locked_until, occurred_at, id);

CREATE TABLE knowledge_evidence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source_event_id BIGINT NOT NULL,
    source_event_key VARCHAR(160) NOT NULL,
    source_type VARCHAR(80) NOT NULL,
    source_id VARCHAR(120) NOT NULL,
    user_id BIGINT NOT NULL,
    goal_id BIGINT NOT NULL,
    graph_version_id BIGINT NOT NULL,
    knowledge_node_id BIGINT NOT NULL,
    dimension VARCHAR(30) NOT NULL,
    score DECIMAL(5,4) NOT NULL,
    reliability DECIMAL(5,4) NOT NULL,
    source_policy_version VARCHAR(80) NOT NULL,
    observed_at DATETIME(6) NOT NULL,
    ingested_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_knowledge_evidence PRIMARY KEY (id),
    CONSTRAINT uk_knowledge_evidence_event UNIQUE (source_event_id),
    CONSTRAINT uk_knowledge_evidence_source UNIQUE (source_type, source_id),
    CONSTRAINT fk_knowledge_evidence_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_knowledge_evidence_goal FOREIGN KEY (goal_id) REFERENCES user_goals (id),
    CONSTRAINT fk_knowledge_evidence_node FOREIGN KEY (graph_version_id, knowledge_node_id)
        REFERENCES knowledge_nodes (graph_version_id, id),
    CONSTRAINT ck_knowledge_evidence_dimension CHECK (
        dimension IN ('RECOGNITION', 'UNDERSTANDING', 'RECALL', 'APPLICATION')),
    CONSTRAINT ck_knowledge_evidence_score CHECK (score BETWEEN 0 AND 1),
    CONSTRAINT ck_knowledge_evidence_reliability CHECK (reliability BETWEEN 0 AND 1)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_knowledge_evidence_replay
    ON knowledge_evidence (user_id, knowledge_node_id, observed_at, source_event_id);

CREATE TABLE user_knowledge (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    graph_version_id BIGINT NOT NULL,
    knowledge_node_id BIGINT NOT NULL,
    recognition_score DECIMAL(5,4) NOT NULL DEFAULT 0,
    understanding_score DECIMAL(5,4) NOT NULL DEFAULT 0,
    recall_score DECIMAL(5,4) NOT NULL DEFAULT 0,
    application_score DECIMAL(5,4) NOT NULL DEFAULT 0,
    mastery_score DECIMAL(5,4) NOT NULL DEFAULT 0,
    confidence_score DECIMAL(5,4) NOT NULL DEFAULT 0,
    evidence_count INT NOT NULL DEFAULT 0,
    first_evidence_at DATETIME(6) NULL,
    last_evidence_at DATETIME(6) NULL,
    ever_mastered_at DATETIME(6) NULL,
    next_review_at DATETIME(6) NULL,
    acquisition_status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    status VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    policy_version VARCHAR(80) NOT NULL,
    projected_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_user_knowledge PRIMARY KEY (id),
    CONSTRAINT uk_user_knowledge_node UNIQUE (user_id, knowledge_node_id),
    CONSTRAINT fk_user_knowledge_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_knowledge_node FOREIGN KEY (graph_version_id, knowledge_node_id)
        REFERENCES knowledge_nodes (graph_version_id, id),
    CONSTRAINT ck_user_knowledge_scores CHECK (
        recognition_score BETWEEN 0 AND 1 AND understanding_score BETWEEN 0 AND 1
        AND recall_score BETWEEN 0 AND 1 AND application_score BETWEEN 0 AND 1
        AND mastery_score BETWEEN 0 AND 1 AND confidence_score BETWEEN 0 AND 1),
    CONSTRAINT ck_user_knowledge_status CHECK (
        status IN ('UNKNOWN', 'LEARNING', 'PROVISIONAL', 'MASTERED', 'REVIEW_DUE')),
    CONSTRAINT ck_user_knowledge_acquisition CHECK (
        acquisition_status IN ('UNKNOWN', 'LEARNING', 'PROVISIONAL', 'MASTERED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_user_knowledge_owner_status
    ON user_knowledge (user_id, status, mastery_score, knowledge_node_id);

CREATE TABLE progress_projection_runs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    requested_by BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    projected_nodes INT NOT NULL DEFAULT 0,
    error_code VARCHAR(120) NULL,
    CONSTRAINT pk_progress_projection_runs PRIMARY KEY (id),
    CONSTRAINT fk_progress_projection_runs_user FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT ck_progress_projection_runs_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
