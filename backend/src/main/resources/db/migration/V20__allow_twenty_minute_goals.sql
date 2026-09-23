ALTER TABLE user_goals
    DROP CHECK ck_user_goals_daily_minutes,
    ADD CONSTRAINT ck_user_goals_daily_minutes
        CHECK (default_daily_minutes BETWEEN 20 AND 180);
