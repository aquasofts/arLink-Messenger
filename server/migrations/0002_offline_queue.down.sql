-- 0002_offline_queue.down.sql
DROP INDEX IF EXISTS uniq_offline_msg;
DROP INDEX IF EXISTS idx_offline_to_ts;
DROP TABLE IF EXISTS offline_queue;
