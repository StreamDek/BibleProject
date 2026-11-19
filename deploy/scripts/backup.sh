#!/usr/bin/env bash
# Nightly PostgreSQL backup to Cloudflare R2. Install with:
#   crontab -e   →   15 3 * * * /opt/bible/deploy/scripts/backup.sh >> /var/log/bible-backup.log 2>&1
# Keeps 30 daily backups (configure an R2 lifecycle rule to expire older objects).
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; source .env; set +a

stamp=$(date -u +%Y%m%dT%H%M%SZ)
export RCLONE_CONFIG_R2_TYPE=s3
export RCLONE_CONFIG_R2_PROVIDER=Cloudflare
export RCLONE_CONFIG_R2_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID"
export RCLONE_CONFIG_R2_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY"
export RCLONE_CONFIG_R2_ENDPOINT="https://${R2_ACCOUNT_ID}.r2.cloudflarestorage.com"

docker compose exec -T postgres pg_dump -U bible -d bible --format=custom --compress=9 \
  | rclone rcat "r2:${R2_BACKUP_BUCKET}/postgres/bible-${stamp}.dump"
echo "$(date -u) backup bible-${stamp}.dump uploaded"
