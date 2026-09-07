-- Keep complete request/response logs in one record (up to 16 MiB).
-- Also safe for databases already expanded manually.
ALTER TABLE `logan_log_detail`
  MODIFY COLUMN `content` MEDIUMTEXT
  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
  NOT NULL COMMENT '原始日志';
