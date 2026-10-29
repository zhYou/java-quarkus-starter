#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
 --set=runtime_password="$APP_DB_PASSWORD" --set=migration_password="$APP_DB_MIGRATION_PASSWORD" --set=admin_password="$POSTGRES_PASSWORD" <<'SQL'
CREATE EXTENSION IF NOT EXISTS citus;
CREATE ROLE app_runtime LOGIN PASSWORD :'runtime_password';
CREATE ROLE app_migration LOGIN PASSWORD :'migration_password';
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO app_migration;
GRANT USAGE ON SCHEMA public TO app_runtime;
INSERT INTO pg_dist_authinfo(nodeid,rolename,authinfo) VALUES
 (0,'bootstrap','password=' || :'admin_password'),
 (0,'app_runtime','password=' || :'runtime_password'),
 (0,'app_migration','password=' || :'migration_password');
SQL
