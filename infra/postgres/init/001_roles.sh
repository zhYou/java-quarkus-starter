#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=runtime_password="$APP_DB_PASSWORD" --set=migration_password="$APP_DB_MIGRATION_PASSWORD" <<'SQL'
CREATE ROLE app_runtime LOGIN PASSWORD :'runtime_password';
CREATE ROLE app_migration LOGIN PASSWORD :'migration_password';
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO app_migration;
GRANT USAGE ON SCHEMA public TO app_runtime;
SQL
