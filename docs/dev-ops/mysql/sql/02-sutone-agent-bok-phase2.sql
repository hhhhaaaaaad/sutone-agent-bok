-- Phase 2: enable_illustration column for AI writing illustration toggle
USE `sutone_agent_bok`;

ALTER TABLE `ai_task`
  ADD COLUMN `enable_illustration` TINYINT(1) NOT NULL DEFAULT 0
  COMMENT '是否启用配图 0-否 1-是'
  AFTER `prompt_payload`;
