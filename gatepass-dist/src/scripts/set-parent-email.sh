#!/bin/sh
# Usage: ./set-parent-email.sh CS24002 someone@example.com
cd "$(dirname "$0")" || exit 1
exec java --enable-native-access=ALL-UNNAMED -cp "guard/gatepass-guard.jar:guard/lib/*" com.gatepass.db.DatabaseInitializer parent-email "$1" "$2"
