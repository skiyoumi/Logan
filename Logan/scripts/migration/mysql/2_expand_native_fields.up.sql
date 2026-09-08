-- Expand existing installations without rewriting historical task or log data.
ALTER TABLE `logan_task`
  MODIFY COLUMN `app_id` varchar(256) COLLATE utf8mb4_general_ci DEFAULT '' COMMENT 'app标识';

ALTER TABLE `logan_log_detail`
  MODIFY COLUMN `content` mediumtext COLLATE utf8mb4_general_ci NOT NULL COMMENT '原始日志';
