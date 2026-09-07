# MySQL migrations

## Version 2: larger native log records

New databases create `logan_log_detail.content` as MEDIUMTEXT.
Existing databases apply `2_expand_log_content.up.sql` via the migration
service. Databases expanded manually can apply version 2 as well.

From the Compose directory, after updating these scripts on the server:

```bash
docker compose run --rm db-migrate
```

The standard Compose configuration mounts this directory at /etc/migrations.
For deployments using the migration image without that mount, rebuild and
publish the migration image with this directory as the Docker build context,
then deploy the updated image and run its migration component.

The version 2 down migration deliberately retains MEDIUMTEXT to avoid losing
long logs. It only rolls back migration bookkeeping; older application versions
can still use the larger column.

This migration does not reset any log task status or delete detail records.
Previously failed tasks require separate recovery after checking for existing
detail records. No task-specific repair belongs in the schema migration.

MEDIUMTEXT supports up to 16,777,215 bytes per record. Batch inserts are also
limited by MySQL max_allowed_packet; inspect it in the target deployment.

## Version 3: longer application identifiers

New databases create `logan_task.app_id` as VARCHAR(256).
Existing databases apply `3_expand_app_id.up.sql` using the migration service
command above. Databases expanded manually can apply version 3 as well.

This fixes uploads rejected with `Data too long for column 'app_id'`, including
the 33-character HarmonyOS bundle name `com.handeasy.easycrm.flutter.hmos`.
The column keeps its existing character set, collation, nullability and default
from the initial schema.

The version 3 down migration deliberately retains VARCHAR(256) to avoid
truncating existing application identifiers; it only rolls back migration
bookkeeping. This migration does not address compressed log parsing errors.
