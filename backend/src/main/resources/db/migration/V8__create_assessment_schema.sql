CREATE TABLE questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_key VARCHAR(120) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_questions PRIMARY KEY (id),
    CONSTRAINT uk_questions_key UNIQUE (question_key)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE question_versions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    type VARCHAR(30) NOT NULL,
    prompt VARCHAR(3000) NOT NULL,
    difficulty INT NOT NULL,
    estimated_seconds INT NOT NULL,
    scoring_strategy VARCHAR(30) NOT NULL,
    options JSON NOT NULL,
    answer_key JSON NOT NULL,
    rubric JSON NULL,
    status VARCHAR(20) NOT NULL,
    source VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_question_versions PRIMARY KEY (id),
    CONSTRAINT fk_question_versions_question FOREIGN KEY (question_id) REFERENCES questions (id),
    CONSTRAINT uk_question_versions_number UNIQUE (question_id, version_number),
    CONSTRAINT ck_question_versions_number CHECK (version_number > 0),
    CONSTRAINT ck_question_versions_type CHECK (
        type IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'SHORT_TEXT', 'LONG_TEXT', 'CODE', 'DEBUGGING', 'DESIGN')
    ),
    CONSTRAINT ck_question_versions_difficulty CHECK (difficulty BETWEEN 1 AND 5),
    CONSTRAINT ck_question_versions_estimated_seconds CHECK (estimated_seconds > 0),
    CONSTRAINT ck_question_versions_scoring CHECK (
        scoring_strategy IN ('EXACT', 'RUBRIC', 'TEST_CASE', 'AI_ASSISTED')
    ),
    CONSTRAINT ck_question_versions_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_question_versions_source CHECK (source IN ('HUMAN', 'AI_GENERATED', 'IMPORTED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_question_versions_active
    ON question_versions (status, scoring_strategy, type, id);

CREATE TABLE question_knowledge (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_version_id BIGINT NOT NULL,
    graph_version_id BIGINT NOT NULL,
    knowledge_node_id BIGINT NOT NULL,
    dimension VARCHAR(30) NOT NULL,
    weight DECIMAL(5,4) NOT NULL,
    max_evidence_strength DECIMAL(5,4) NOT NULL,
    rubric_criterion_key VARCHAR(120) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_question_knowledge PRIMARY KEY (id),
    CONSTRAINT fk_question_knowledge_question FOREIGN KEY (question_version_id)
        REFERENCES question_versions (id),
    CONSTRAINT fk_question_knowledge_version FOREIGN KEY (graph_version_id)
        REFERENCES knowledge_graph_versions (id),
    CONSTRAINT fk_question_knowledge_node FOREIGN KEY (graph_version_id, knowledge_node_id)
        REFERENCES knowledge_nodes (graph_version_id, id),
    CONSTRAINT uk_question_knowledge_mapping UNIQUE (
        question_version_id, knowledge_node_id, dimension
    ),
    CONSTRAINT ck_question_knowledge_dimension CHECK (
        dimension IN ('RECOGNITION', 'UNDERSTANDING', 'RECALL', 'APPLICATION')
    ),
    CONSTRAINT ck_question_knowledge_weight CHECK (weight > 0 AND weight <= 1),
    CONSTRAINT ck_question_knowledge_strength CHECK (
        max_evidence_strength >= 0 AND max_evidence_strength <= 1
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_question_knowledge_graph
    ON question_knowledge (graph_version_id, question_version_id, knowledge_node_id);

CREATE TABLE assessment_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    goal_id BIGINT NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    graph_version_id BIGINT NOT NULL,
    assessment_policy_version VARCHAR(80) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    active_diagnostic_goal_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN purpose = 'DIAGNOSTIC' AND status = 'IN_PROGRESS' THEN goal_id ELSE NULL END
    ) STORED,
    completed_diagnostic_goal_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN purpose = 'DIAGNOSTIC' AND status = 'COMPLETED' THEN goal_id ELSE NULL END
    ) STORED,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_assessment_sessions PRIMARY KEY (id),
    CONSTRAINT fk_assessment_sessions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_assessment_sessions_goal FOREIGN KEY (goal_id) REFERENCES user_goals (id),
    CONSTRAINT fk_assessment_sessions_graph FOREIGN KEY (graph_version_id)
        REFERENCES knowledge_graph_versions (id),
    CONSTRAINT uk_assessment_sessions_active_diagnostic UNIQUE (active_diagnostic_goal_id),
    CONSTRAINT uk_assessment_sessions_completed_diagnostic UNIQUE (completed_diagnostic_goal_id),
    CONSTRAINT ck_assessment_sessions_purpose CHECK (
        purpose IN ('DIAGNOSTIC', 'PRACTICE', 'MASTERY_CHECK', 'REVIEW', 'REMEDIAL')
    ),
    CONSTRAINT ck_assessment_sessions_status CHECK (
        status IN ('IN_PROGRESS', 'COMPLETED', 'EXPIRED')
    ),
    CONSTRAINT ck_assessment_sessions_time CHECK (expires_at > started_at),
    CONSTRAINT ck_assessment_sessions_completion CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND completed_at IS NULL)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_assessment_sessions_owner_status
    ON assessment_sessions (user_id, status, started_at);
CREATE INDEX ix_assessment_sessions_goal_purpose
    ON assessment_sessions (goal_id, purpose, status);

CREATE TABLE assessment_session_questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    question_version_id BIGINT NOT NULL,
    position INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_assessment_session_questions PRIMARY KEY (id),
    CONSTRAINT fk_assessment_session_questions_session FOREIGN KEY (session_id)
        REFERENCES assessment_sessions (id),
    CONSTRAINT fk_assessment_session_questions_version FOREIGN KEY (question_version_id)
        REFERENCES question_versions (id),
    CONSTRAINT uk_assessment_session_questions_position UNIQUE (session_id, position),
    CONSTRAINT uk_assessment_session_questions_version UNIQUE (session_id, question_version_id),
    CONSTRAINT uk_assessment_session_questions_identity UNIQUE (session_id, id),
    CONSTRAINT ck_assessment_session_questions_position CHECK (position > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE answer_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    session_question_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    answer_payload JSON NOT NULL,
    raw_score DECIMAL(5,4) NOT NULL,
    self_confidence DECIMAL(5,4) NULL,
    time_spent_seconds INT NOT NULL,
    evaluation_status VARCHAR(30) NOT NULL,
    evaluator_version VARCHAR(80) NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_answer_attempts PRIMARY KEY (id),
    CONSTRAINT fk_answer_attempts_session FOREIGN KEY (session_id)
        REFERENCES assessment_sessions (id),
    CONSTRAINT fk_answer_attempts_session_question FOREIGN KEY (session_id, session_question_id)
        REFERENCES assessment_session_questions (session_id, id),
    CONSTRAINT fk_answer_attempts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_answer_attempts_idempotency UNIQUE (user_id, session_id, idempotency_key),
    CONSTRAINT uk_answer_attempts_question UNIQUE (session_id, session_question_id),
    CONSTRAINT ck_answer_attempts_raw_score CHECK (raw_score BETWEEN 0 AND 1),
    CONSTRAINT ck_answer_attempts_confidence CHECK (
        self_confidence IS NULL OR self_confidence BETWEEN 0 AND 1
    ),
    CONSTRAINT ck_answer_attempts_time CHECK (time_spent_seconds BETWEEN 0 AND 3600),
    CONSTRAINT ck_answer_attempts_status CHECK (
        evaluation_status IN ('PENDING', 'EVALUATED', 'REJECTED', 'NEEDS_REVIEW')
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_answer_attempts_session_submitted
    ON answer_attempts (session_id, submitted_at, id);

CREATE TABLE attempt_evidence (
    id BIGINT NOT NULL AUTO_INCREMENT,
    attempt_id BIGINT NOT NULL,
    graph_version_id BIGINT NOT NULL,
    knowledge_node_id BIGINT NOT NULL,
    dimension VARCHAR(30) NOT NULL,
    score DECIMAL(5,4) NOT NULL,
    reliability DECIMAL(5,4) NOT NULL,
    evaluator_type VARCHAR(30) NOT NULL,
    evaluator_version VARCHAR(80) NOT NULL,
    rationale VARCHAR(500) NOT NULL,
    misconception_codes JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_attempt_evidence PRIMARY KEY (id),
    CONSTRAINT fk_attempt_evidence_attempt FOREIGN KEY (attempt_id) REFERENCES answer_attempts (id),
    CONSTRAINT fk_attempt_evidence_graph FOREIGN KEY (graph_version_id)
        REFERENCES knowledge_graph_versions (id),
    CONSTRAINT fk_attempt_evidence_node FOREIGN KEY (graph_version_id, knowledge_node_id)
        REFERENCES knowledge_nodes (graph_version_id, id),
    CONSTRAINT uk_attempt_evidence_source UNIQUE (attempt_id, knowledge_node_id, dimension),
    CONSTRAINT ck_attempt_evidence_dimension CHECK (
        dimension IN ('RECOGNITION', 'UNDERSTANDING', 'RECALL', 'APPLICATION')
    ),
    CONSTRAINT ck_attempt_evidence_score CHECK (score BETWEEN 0 AND 1),
    CONSTRAINT ck_attempt_evidence_reliability CHECK (reliability BETWEEN 0 AND 1),
    CONSTRAINT ck_attempt_evidence_evaluator CHECK (
        evaluator_type IN ('DETERMINISTIC', 'TEST_RUNNER', 'AI', 'HUMAN')
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_attempt_evidence_attempt_node
    ON attempt_evidence (attempt_id, knowledge_node_id, dimension);

CREATE TABLE outbox_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_key VARCHAR(160) NOT NULL,
    owner_module VARCHAR(60) NOT NULL,
    aggregate_type VARCHAR(80) NOT NULL,
    aggregate_id VARCHAR(120) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    occurred_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT uk_outbox_events_key UNIQUE (event_key),
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_outbox_events_attempt_count CHECK (attempt_count >= 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_outbox_events_pending
    ON outbox_events (status, occurred_at, id);
