ALTER TABLE task_template_versions DROP CHECK ck_learning_task_evaluation;
ALTER TABLE task_template_versions
    ADD CONSTRAINT ck_learning_task_evaluation
        CHECK (evaluation_mode IN ('NONE','SELF_REPORT','OBJECTIVE'));

-- Assessment owns the check definition and the attempt linkage. Existing
-- diagnostic sessions/attempts/evidence remain unchanged and readable.
CREATE TABLE task_check_definitions (
    task_template_version_id BIGINT NOT NULL PRIMARY KEY,
    graph_version_id BIGINT NOT NULL,
    question_version_id BIGINT NOT NULL,
    evaluator_version VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_task_check_template FOREIGN KEY (task_template_version_id) REFERENCES task_template_versions(id),
    CONSTRAINT fk_task_check_graph FOREIGN KEY (graph_version_id) REFERENCES knowledge_graph_versions(id),
    CONSTRAINT fk_task_check_question FOREIGN KEY (question_version_id) REFERENCES question_versions(id),
    CONSTRAINT ck_task_check_status CHECK (status IN ('ACTIVE','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE task_check_submissions (
    learning_task_id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    goal_id BIGINT NOT NULL,
    graph_version_id BIGINT NOT NULL,
    assessment_session_id BIGINT NOT NULL,
    answer_attempt_id BIGINT NOT NULL,
    task_template_version_id BIGINT NOT NULL,
    submitted_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_task_check_session UNIQUE (assessment_session_id),
    CONSTRAINT uk_task_check_attempt UNIQUE (answer_attempt_id),
    CONSTRAINT fk_task_check_task FOREIGN KEY (learning_task_id) REFERENCES learning_tasks(id),
    CONSTRAINT fk_task_check_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_task_check_goal FOREIGN KEY (goal_id) REFERENCES user_goals(id),
    CONSTRAINT fk_task_check_graph_version FOREIGN KEY (graph_version_id) REFERENCES knowledge_graph_versions(id),
    CONSTRAINT fk_task_check_session FOREIGN KEY (assessment_session_id) REFERENCES assessment_sessions(id),
    CONSTRAINT fk_task_check_attempt FOREIGN KEY (answer_attempt_id) REFERENCES answer_attempts(id),
    CONSTRAINT fk_task_check_template_version FOREIGN KEY (task_template_version_id) REFERENCES task_template_versions(id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE INDEX ix_task_check_owner ON task_check_submissions (user_id, learning_task_id);
