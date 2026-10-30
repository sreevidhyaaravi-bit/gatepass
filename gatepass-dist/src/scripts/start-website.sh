#!/bin/sh
# Starts Apache Tomcat 10.1 with the student + warden website at http://localhost:8080 (Ctrl+C stops it).
cd "$(dirname "$0")" || exit 1
exec java --enable-native-access=ALL-UNNAMED -jar server/gatepass-launcher.jar website
