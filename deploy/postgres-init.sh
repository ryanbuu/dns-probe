#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER:-postgres}" --dbname postgres \
  --set=app_password="$INFRAPILOT_DATABASE_PASSWORD" <<'SQL'
CREATE ROLE infrapilot LOGIN PASSWORD :'app_password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE infrapilot OWNER infrapilot;
REVOKE ALL ON DATABASE infrapilot FROM PUBLIC;
SQL
