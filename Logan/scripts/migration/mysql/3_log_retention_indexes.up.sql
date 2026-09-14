-- Support retention scans and reference checks without loading log content.
ALTER TABLE `logan_task`
  ADD INDEX `idx_retention_add_time` (`add_time`, `id`),
  ADD INDEX `idx_log_file_name` (`log_file_name`(191));
