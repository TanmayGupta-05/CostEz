#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
export DEBUG=false
echo "Open http://localhost:8080 after startup. Demo: demo@costez.local / CostezDemo2026!"
exec java -jar costez.jar
