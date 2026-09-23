ALTER TABLE user_goals
    DROP CHECK ck_user_goals_status,
    ADD CONSTRAINT ck_user_goals_status
        CHECK (status IN ('ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED'));

CREATE TABLE task_template_practices (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_template_version_id BIGINT NOT NULL,
    position INT NOT NULL,
    prompt TEXT NOT NULL,
    starter_code TEXT NULL,
    expected_output TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_learning_task_practice_position UNIQUE(task_template_version_id, position),
    CONSTRAINT fk_learning_task_practice_template FOREIGN KEY(task_template_version_id) REFERENCES task_template_versions(id),
    CONSTRAINT ck_learning_task_practice_position CHECK(position > 0)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
