#!/bin/sh
# Restart the existing local database; never initializes or replaces its data.
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
mysql_dir="$project_dir/data/local-mysql"
mysql_binary=${QUIZ_MYSQLD:-/usr/local/mysql/bin/mysqld}
if [ ! -d "$mysql_dir/data/mysql" ]; then
  echo "Local database not initialized: $mysql_dir/data" >&2
  exit 1
fi
if [ -f "$mysql_dir/mysql.pid" ] && kill -0 "$(cat "$mysql_dir/mysql.pid")" 2>/dev/null; then
  echo "Local MySQL is already running on 127.0.0.1:13306"
  exit 0
fi
nohup "$mysql_binary" --no-defaults --datadir="$mysql_dir/data" \
  --bind-address=127.0.0.1 --port=13306 --socket="$mysql_dir/mysql.sock" \
  --pid-file="$mysql_dir/mysql.pid" --log-error="$mysql_dir/mysql.log" \
  --mysqlx=OFF >> "$mysql_dir/process.log" 2>&1 &
echo "Starting local MySQL on 127.0.0.1:13306; log: $mysql_dir/mysql.log"
