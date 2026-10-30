#!/bin/sh
# Starts the security guard desktop application (Java Swing).
cd "$(dirname "$0")" || exit 1
exec java --enable-native-access=ALL-UNNAMED -jar guard/gatepass-guard.jar
