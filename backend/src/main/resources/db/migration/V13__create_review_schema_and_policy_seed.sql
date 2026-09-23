CREATE TABLE review_schedules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    graph_version_id BIGINT NOT NULL,
    knowledge_node_id BIGINT NOT NULL,
    policy_version VARCHAR(80) NOT NULL,
    interval_index INT NOT NULL DEFAULT 0,
    due_at DATETIME(6) NOT NULL,
    last_reviewed_at DATETIME(6) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_review_schedules PRIMARY KEY (id),
    CONSTRAINT uk_review_schedules_node UNIQUE (user_id, knowledge_node_id),
    CONSTRAINT fk_review_schedules_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_review_schedules_node FOREIGN KEY (graph_version_id, knowledge_node_id)
        REFERENCES knowledge_nodes (graph_version_id, id),
    CONSTRAINT ck_review_schedules_index CHECK (interval_index BETWEEN 0 AND 5),
    CONSTRAINT ck_review_schedules_status CHECK (status IN ('SCHEDULED', 'DUE', 'PAUSED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_review_schedules_due ON review_schedules (user_id, status, due_at, id);

CREATE TABLE review_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    schedule_id BIGINT NOT NULL,
    source_event_id BIGINT NOT NULL,
    score DECIMAL(5,4) NOT NULL,
    reliability DECIMAL(5,4) NOT NULL,
    prior_interval_index INT NOT NULL,
    next_interval_index INT NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_review_attempts PRIMARY KEY (id),
    CONSTRAINT uk_review_attempts_event UNIQUE (source_event_id),
    CONSTRAINT fk_review_attempts_schedule FOREIGN KEY (schedule_id) REFERENCES review_schedules (id),
    CONSTRAINT ck_review_attempts_score CHECK (score BETWEEN 0 AND 1),
    CONSTRAINT ck_review_attempts_reliability CHECK (reliability BETWEEN 0 AND 1)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE misconception_definitions (
    code VARCHAR(120) NOT NULL,
    display_name VARCHAR(240) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_misconception_definitions PRIMARY KEY (code)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE user_misconceptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    graph_version_id BIGINT NOT NULL,
    knowledge_node_id BIGINT NOT NULL,
    dimension VARCHAR(30) NOT NULL,
    misconception_code VARCHAR(120) NOT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    verification_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    first_observed_at DATETIME(6) NOT NULL,
    last_observed_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6) NULL,
    CONSTRAINT pk_user_misconceptions PRIMARY KEY (id),
    CONSTRAINT uk_user_misconceptions UNIQUE (user_id, knowledge_node_id, dimension, misconception_code),
    CONSTRAINT fk_user_misconceptions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_misconceptions_node FOREIGN KEY (graph_version_id, knowledge_node_id)
        REFERENCES knowledge_nodes (graph_version_id, id),
    CONSTRAINT fk_user_misconceptions_definition FOREIGN KEY (misconception_code)
        REFERENCES misconception_definitions (code),
    CONSTRAINT ck_user_misconceptions_confidence CHECK (confidence BETWEEN 0 AND 1),
    CONSTRAINT ck_user_misconceptions_status CHECK (status IN ('ACTIVE', 'RESOLVED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

INSERT INTO misconception_definitions (code, display_name, active) VALUES
    ('CONFUSES_HTTP_METHOD_SEMANTICS', 'Confuses HTTP method semantics', TRUE),
    ('CONFUSES_PRIMARY_AND_FOREIGN_KEYS', 'Confuses primary and foreign keys', TRUE),
    ('CONFUSES_VALUE_AND_REFERENCE_EQUALITY', 'Confuses value and reference equality', TRUE);
