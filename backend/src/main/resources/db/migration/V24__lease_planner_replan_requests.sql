-- Planner request delivery is separate from the Review outbox acknowledgement.
-- Existing PENDING requests remain eligible after this additive upgrade.
ALTER TABLE planner_replan_requests
    ADD COLUMN locked_at DATETIME(6) NULL,
    ADD COLUMN locked_until DATETIME(6) NULL,
    ADD COLUMN locked_by VARCHAR(80) NULL,
    ADD COLUMN updated_at DATETIME(6) NULL,
    ADD COLUMN result_code VARCHAR(80) NULL,
    ADD COLUMN result_plan_id BIGINT NULL;

UPDATE planner_replan_requests SET updated_at=created_at WHERE updated_at IS NULL;

ALTER TABLE planner_replan_requests
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL,
    ADD CONSTRAINT fk_planner_replan_result_plan FOREIGN KEY(result_plan_id) REFERENCES daily_plans(id),
    ADD CONSTRAINT ck_planner_replan_lease CHECK (
        (status='PROCESSING' AND locked_at IS NOT NULL AND locked_until IS NOT NULL AND locked_by IS NOT NULL)
        OR (status<>'PROCESSING' AND locked_at IS NULL AND locked_until IS NULL AND locked_by IS NULL)
    );

CREATE INDEX ix_planner_replan_lease ON planner_replan_requests(status,locked_until,id);
