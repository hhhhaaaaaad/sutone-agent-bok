-- V2 记忆系统升级：向量同步状态追踪
ALTER TABLE memory_record
    ADD COLUMN IF NOT EXISTS vector_status VARCHAR(16) DEFAULT 'SYNCED'
        COMMENT '向量同步状态: SYNCED / PENDING / FAILED',
    ADD COLUMN IF NOT EXISTS retry_count INT DEFAULT 0
        COMMENT '向量同步重试次数',
    ADD INDEX IF NOT EXISTS idx_vector_status (vector_status);
