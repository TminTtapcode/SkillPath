ALTER TABLE learning_sessions MODIFY sequence_id BIGINT NULL;

ALTER TABLE learning_sessions ADD CONSTRAINT ck_learning_session_sequence_source CHECK (
    (assignment_source='LEARNER_SELECTED' AND sequence_id IS NOT NULL) OR
    (assignment_source='PLANNER' AND sequence_id IS NULL));

CREATE INDEX ix_learning_task_planner_decision ON learning_tasks(planner_decision_id);
ALTER TABLE learning_tasks ADD CONSTRAINT fk_learning_task_planner_decision
    FOREIGN KEY(planner_decision_id) REFERENCES planner_decisions(id);
