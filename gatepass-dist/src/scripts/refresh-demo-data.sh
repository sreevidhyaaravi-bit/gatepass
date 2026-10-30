#!/bin/sh
# Re-creates the 7 demo passes (GP1001 to GP1007) with dates relative to TODAY.
cd "$(dirname "$0")" || exit 1
exec java --enable-native-access=ALL-UNNAMED -cp "guard/gatepass-guard.jar:guard/lib/*" com.gatepass.db.DatabaseInitializer refresh
