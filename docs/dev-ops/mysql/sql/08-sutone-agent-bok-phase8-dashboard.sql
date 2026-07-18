-- ============================================================
-- Phase8: 统计仪表盘
-- ============================================================

CREATE TABLE `article_view_daily` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `article_id` bigint(20) NOT NULL,
  `date` date NOT NULL,
  `view_count` int(11) NOT NULL DEFAULT '0',
  `like_count` int(11) NOT NULL DEFAULT '0',
  `favorite_count` int(11) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_date` (`article_id`, `date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章每日统计表';

CREATE TABLE `article_view_snapshot` (
  `article_id` bigint(20) NOT NULL,
  `snapshot_date` date NOT NULL,
  `view_count` int(11) NOT NULL DEFAULT '0',
  PRIMARY KEY (`article_id`, `snapshot_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章浏览量每日快照';
