#!/bin/sh
# A nightly pg_dump, run by the backup service in docker-compose.prod.yml.
#
# A shell loop rather than cron, because this runs inside a container whose only job
# is this: cron would mean a second process to supervise and a second place for the
# schedule to hide. Restart policy handles crashes.
#
# PGPASSWORD comes from the environment. It is never echoed and never written to a
# file, so a dump landing on disk does not come with its own credentials beside it.

set -eu

KEEP_DAYS=14

while true; do
    # Seconds until 03:15 UTC, which is the quietest point for this feed: Halifax
    # runs almost no service then, so a dump competes with nothing.
    now=$(date -u +%s)
    target=$(date -u -d "today 03:15" +%s 2>/dev/null || echo 0)
    if [ "$target" -le "$now" ]; then
        target=$((target + 86400))
    fi
    sleep $((target - now))

    stamp=$(date -u +%Y-%m-%dT%H%M%SZ)
    file="/backups/bustruth-${stamp}.sql.gz"

    # To a temporary name first, then renamed. A dump interrupted halfway is not a
    # backup, and the only thing worse than no backup is a corrupt one that looks
    # like a good one.
    if PGPASSWORD="$POSTGRES_PASSWORD" pg_dump \
            -h postgres -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
            --no-owner --no-privileges \
            | gzip -9 > "${file}.partial"; then
        mv "${file}.partial" "$file"
        echo "$(date -u +%FT%TZ) backup written: ${file}"
    else
        rm -f "${file}.partial"
        echo "$(date -u +%FT%TZ) BACKUP FAILED" >&2
    fi

    # Only ever deletes its own dated dumps, and only after a successful run has
    # written a new one.
    find /backups -name 'bustruth-*.sql.gz' -mtime "+${KEEP_DAYS}" -delete
done
