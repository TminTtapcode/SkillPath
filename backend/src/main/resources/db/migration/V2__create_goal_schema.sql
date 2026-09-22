CREATE TABLE goal_templates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    template_key VARCHAR(100) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_goal_templates PRIMARY KEY (id),
    CONSTRAINT uk_goal_templates_key UNIQUE (template_key)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE user_goals (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    goal_template_id BIGINT NOT NULL,
    target_date DATE NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    default_daily_minutes SMALLINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    active_owner_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN user_id ELSE NULL END
    ) STORED,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_user_goals PRIMARY KEY (id),
    CONSTRAINT fk_user_goals_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_goals_template FOREIGN KEY (goal_template_id) REFERENCES goal_templates (id),
    CONSTRAINT uk_user_goals_one_active UNIQUE (active_owner_id),
    CONSTRAINT ck_user_goals_daily_minutes CHECK (default_daily_minutes BETWEEN 30 AND 180),
    CONSTRAINT ck_user_goals_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_user_goals_owner_status ON user_goals (user_id, status);

CREATE TABLE idempotency_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    operation_name VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    outcome_status VARCHAR(20) NOT NULL,
    resource_id BIGINT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_idempotency_records PRIMARY KEY (id),
    CONSTRAINT fk_idempotency_records_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_idempotency_records_goal FOREIGN KEY (resource_id) REFERENCES user_goals (id),
    CONSTRAINT uk_idempotency_owner_operation_key UNIQUE (user_id, operation_name, idempotency_key),
    CONSTRAINT ck_idempotency_status CHECK (outcome_status IN ('IN_PROGRESS', 'COMPLETED'))
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_idempotency_expiry ON idempotency_records (expires_at);
