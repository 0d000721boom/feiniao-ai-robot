CREATE TABLE IF NOT EXISTS t_file_chunk_info (
    id BIGSERIAL PRIMARY KEY,
    file_md5 VARCHAR(32) NOT NULL,
    chunk_number INTEGER NOT NULL,
    chunk_path TEXT NOT NULL,
    chunk_size BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_file_chunk_file_md5_number UNIQUE (file_md5, chunk_number)
);

CREATE INDEX IF NOT EXISTS idx_file_chunk_file_md5
    ON t_file_chunk_info (file_md5);

ALTER TABLE t_ai_customer_service_file_storage
    ALTER COLUMN file_path DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_file_md5
    ON t_ai_customer_service_file_storage (file_md5);
