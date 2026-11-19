#!/usr/bin/env bash
# Restore a backup from R2:  scripts/restore.sh bible-20261119T031500Z.dump
# Test this monthly on a scratch database (see deploy/README.md).
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; source .env; set +a
file=${1:?usage: restore.sh <backup file name>}
export RCLONE_CONFIG_R2_TYPE=s3 RCLONE_CONFIG_R2_PROVIDER=Cloudflare
export RCLONE_CONFIG_R2_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID" RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY"
export RCLONE_CONFIG_R2_ENDPOINT="https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com"
target=${RESTORE_DB:-bible_restore_test}
docker compose exec -T postgres psql -U bible -d postgres -c "DROP DATABASE IF EXISTS ${target}" -c "CREATE DATABASE ${target}"
rclone cat "r2:${R2_BACKUP_BUCKET}/postgres/${file}" | docker compose exec -T postgres pg_restore -U bible -d "${target}" --no-owner
echo "Restored ${file} into database ${target}"
