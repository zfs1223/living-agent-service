#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    SELECT 'CREATE DATABASE openproject OWNER livingagent'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'openproject')\gexec
    GRANT ALL PRIVILEGES ON DATABASE openproject TO livingagent;
EOSQL
