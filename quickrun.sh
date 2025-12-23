#!/usr/bin/env bash
set -euo pipefail

JAVA_HOME=${JAVA_HOME:-/Library/Java/JavaVirtualMachines/microsoft-21.jdk/Contents/Home}
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export JAVA_HOME
export FITNESSE_VERTX_PORT=${FITNESSE_VERTX_PORT:-8080}
export FITNESSE_ROOT_PATH=${FITNESSE_ROOT_PATH:-$SCRIPT_DIR}
export FITNESSE_ROOT_DIR=${FITNESSE_ROOT_DIR:-FitNesseRoot}
export FITNESSE_AUTH_ENABLED=${FITNESSE_AUTH_ENABLED:-false}
export SLIM_PORT=${SLIM_PORT:-8099}

./gradlew classes copyRuntimeLibs

exec "$JAVA_HOME/bin/java" \
  -Dprevent.system.exit=false \
  -Djava.security.manager=allow \
  -Dslim.port=${SLIM_PORT} \
  -cp "build/classes/java/main:build/resources/main:lib/*" \
  fitnesse.vertx.FitNesseVertxMain
