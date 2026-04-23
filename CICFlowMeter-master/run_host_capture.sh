#!/bin/sh
set -eu

if [ -d /usr/lib/jvm/java-1.8.0-openjdk-amd64 ]; then
  export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-amd64
  export PATH="$JAVA_HOME/bin:$PATH"
elif [ -d /usr/lib/jvm/java-8-openjdk-amd64 ]; then
  export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
  export PATH="$JAVA_HOME/bin:$PATH"
fi

java_version="$(java -version 2>&1 | head -n 1 || true)"
case "$java_version" in
  *\"1.8*|*\"8.*)
    ;;
  *)
    echo "CICFlowMeter host capture needs Java 8. Current Java: $java_version" >&2
    echo "Install it with: sudo apt install openjdk-8-jdk" >&2
    echo "Then rerun: sudo sh run_host_capture.sh" >&2
    exit 1
    ;;
esac

./get_interface.sh
./gradlew execute
