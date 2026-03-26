#!/usr/bin/env bash
# =============================================================
# Export local Docker PostgreSQL data to a SQL dump file
# Usage: bash scripts/db/export-local.sh
# Output: scripts/db/local-dump.sql
# =============================================================
set -euo pipefail

CONTAINER="${DOCKER_CONTAINER:-property-postgres}"
DB="${LOCAL_DB_NAME:-propertydb}"
USER="${LOCAL_DB_USER:-propertyuser}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DUMP_FILE="$SCRIPT_DIR/local-dump.sql"

echo "Exporting $DB from Docker container: $CONTAINER ..."

docker exec "$CONTAINER" pg_dump \
  -U "$USER" -d "$DB" \
  --no-owner --no-acl --no-privileges \
  --format=plain \
  > "$DUMP_FILE"

echo "Done. Lines: $(wc -l < "$DUMP_FILE")"
echo "Dump saved to: $DUMP_FILE"
echo ""
echo "Next: SUPABASE_PASSWORD=<your-supabase-password> bash scripts/db/import-to-supabase.sh"
