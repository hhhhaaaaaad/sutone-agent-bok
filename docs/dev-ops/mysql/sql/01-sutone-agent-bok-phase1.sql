SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `sutone_agent_bok`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `sutone_agent_bok`;

DROP TABLE IF EXISTS `ai_task`;
DROP TABLE IF EXISTS `article_meta`;
DROP TABLE IF EXISTS `article`;
DROP TABLE IF EXISTS `draft`;
DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(64) NOT NULL COMMENT '登录账号',
  `password_hash` varchar(128) NOT NULL COMMENT '密码哈希值(演示可明文/弱哈希)',
  `nickname` varchar(64) DEFAULT NULL COMMENT '用户昵称',
  `avatar_url` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否删除 0-否 1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE `draft` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '草稿ID',
  `user_id` bigint(20) NOT NULL COMMENT '所属用户ID',
  `title` varchar(128) DEFAULT NULL COMMENT '草稿标题',
  `content_md` longtext COMMENT 'Markdown格式正文',
  `summary` varchar(512) DEFAULT NULL COMMENT '摘要草稿',
  `cover_url` varchar(2048) DEFAULT NULL COMMENT '封面图URL',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '草稿状态: 0-编辑中, 1-已发布, 2-已废弃',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_status_update_time` (`user_id`, `status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内容创作草稿表';

CREATE TABLE `article` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '文章ID',
  `draft_id` bigint(20) NOT NULL COMMENT '来源草稿ID(保留追溯关系)',
  `author_id` bigint(20) NOT NULL COMMENT '作者用户ID',
  `title` varchar(128) NOT NULL COMMENT '文章标题',
  `content_md` longtext NOT NULL COMMENT 'Markdown格式正文',
  `content_html` longtext COMMENT 'HTML格式正文(可选,用于前端直接渲染)',
  `summary` varchar(512) DEFAULT NULL COMMENT '文章摘要',
  `cover_url` varchar(2048) DEFAULT NULL COMMENT '封面图',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '发布状态: 0-下线, 1-正常可见',
  `publish_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_draft_id` (`draft_id`),
  KEY `idx_author_id` (`author_id`),
  KEY `idx_publish_time` (`publish_time`),
  KEY `idx_author_status_publish_time` (`author_id`, `status`, `publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='正式发布文章表';

CREATE TABLE `article_meta` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `article_id` bigint(20) NOT NULL COMMENT '文章ID',
  `word_count` int(11) NOT NULL DEFAULT '0' COMMENT '文章字数',
  `view_count` int(11) NOT NULL DEFAULT '0' COMMENT '阅读量',
  `like_count` int(11) NOT NULL DEFAULT '0' COMMENT '点赞数(预留)',
  `favorite_count` int(11) NOT NULL DEFAULT '0' COMMENT '收藏数(预留)',
  `tags` varchar(255) DEFAULT NULL COMMENT 'JSON格式标签列表',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_id` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章元数据与统计表';

CREATE TABLE `ai_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `user_id` bigint(20) NOT NULL COMMENT '发起用户ID',
  `draft_id` bigint(20) DEFAULT NULL COMMENT '关联的草稿ID',
  `task_type` varchar(32) NOT NULL COMMENT '任务类型: GENERATE_OUTLINE, GENERATE_BODY, POLISH_TEXT 等',
  `prompt_payload` text COMMENT '发给大模型的完整提示词或请求JSON',
  `response_content` longtext COMMENT '大模型返回的原始结果',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '任务状态: 0-进行中, 1-成功, 2-失败',
  `error_msg` varchar(512) DEFAULT NULL COMMENT '失败原因记录',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_draft_id` (`draft_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_status_update_time` (`user_id`, `status`, `update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 创作任务记录表';

INSERT INTO `user` (`id`, `username`, `password_hash`, `nickname`, `avatar_url`)
VALUES
  (1, 'admin', 'admin', '苏东昊', NULL)
ON DUPLICATE KEY UPDATE
  `password_hash` = VALUES(`password_hash`),
  `nickname` = VALUES(`nickname`),
  `avatar_url` = VALUES(`avatar_url`);

SET FOREIGN_KEY_CHECKS = 1;
