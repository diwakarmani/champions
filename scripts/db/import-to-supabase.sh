#!/usr/bin/env bash
# =============================================================
# Import local-dump.sql into Supabase PostgreSQL
# Usage: SUPABASE_PASSWORD=your_pass bash scripts/db/import-to-supabase.sh
#
# Supabase project ref: vqafsihxcryolxwysmfp
# Direct connection (port 5432) — use this for bulk import
# =============================================================
set -euo pipefail

# ── Supabase connection ───────────────────────────────────────
SUPA_HOST="db.vqafsihxcryolxwysmfp.supabase.co"
SUPA_PORT="5432"
SUPA_DB="postgres"
SUPA_USER="postgres"
SUPA_PASS="${SUPABASE_PASSWORD:?Set SUPABASE_PASSWORD env var}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DUMP_FILE="$SCRIPT_DIR/local-dump.sql"
INIT_FILE="$SCRIPT_DIR/01-init-supabase.sql"

if [ ! -f "$DUMP_FILE" ]; then
  echo "ERROR: $DUMP_FILE not found. Run export-local.sh first."
  exit 1
fi

echo "=== Step 1: Applying PostGIS + extra tables to Supabase ==="
PGPASSWORD="$SUPA_PASS" psql \
  --host="$SUPA_HOST" \
  --port="$SUPA_PORT" \
  --username="$SUPA_USER" \
  --dbname="$SUPA_DB" \
  --file="$INIT_FILE" \
  --single-transaction 2>&1 | grep -v "^NOTICE" || true

echo ""
echo "=== Step 2: Importing data dump ==="
PGPASSWORD="$SUPA_PASS" psql \
  --host="$SUPA_HOST" \
  --port="$SUPA_PORT" \
  --username="$SUPA_USER" \
  --dbname="$SUPA_DB" \
  --file="$DUMP_FILE" \
  --single-transaction \
  --set ON_ERROR_STOP=off 2>&1 | grep -v "^NOTICE" || true

echo ""
echo "Import complete. Verify on Supabase dashboard."
echo ""
echo "Render environment variables to set:"
echo "  DATABASE_URL=jdbc:postgresql://$SUPA_HOST:$SUPA_PORT/$SUPA_DB?sslmode=require"
echo "  DB_USERNAME=$SUPA_USER"
echo "  DB_PASSWORD=<your-supabase-password>"
