-- Allow full application package names, including HarmonyOS bundle names.
-- Also safe for databases already expanded manually.
ALTER TABLE `logan_task`
  MODIFY COLUMN `app_id` VARCHAR(256)
  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
  DEFAULT '' COMMENT 'app标识';
