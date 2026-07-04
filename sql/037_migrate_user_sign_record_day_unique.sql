\set ON_ERROR_STOP on

BEGIN;

LOCK TABLE user_sign_record IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM user_sign_record LIMIT 1) THEN
        RAISE EXCEPTION 'user_sign_record is not empty; abort day-level sign-in migration';
    END IF;
END $$;

ALTER TABLE user_sign_record
    DROP CONSTRAINT IF EXISTS uq_user_sign_record_user_period;

DROP INDEX IF EXISTS idx_user_sign_record_sign_date;
DROP INDEX IF EXISTS idx_user_sign_record_user_latest;

ALTER TABLE user_sign_record
    ALTER COLUMN sign_date TYPE DATE
    USING ((sign_date AT TIME ZONE 'Asia/Shanghai')::date);

ALTER TABLE user_sign_record
    DROP COLUMN IF EXISTS sign_period_key;

ALTER TABLE user_sign_record
    ADD CONSTRAINT uq_user_sign_record_user_date
    UNIQUE (user_id, sign_date);

CREATE INDEX idx_user_sign_record_sign_date
    ON user_sign_record (sign_date);

CREATE INDEX idx_user_sign_record_user_latest
    ON user_sign_record (user_id, sign_date DESC, id DESC);

COMMENT ON TABLE user_sign_record IS '用户签到记录表：一行代表一个用户在一个业务日期完成一次签到，使用 user_id + sign_date 唯一约束保证按天签到幂等。';
COMMENT ON COLUMN user_sign_record.sign_date IS '签到业务日期，按配置业务时区折算到天；同一用户同一日期只能签到一次';

COMMIT;
