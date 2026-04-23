#!/bin/sh
set -eu

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
  echo "Usage: sh run_offline_pcap.sh <pcap-or-dir> [output-dir]" >&2
  exit 1
fi

INPUT_PATH="$1"
OUTPUT_DIR="${2:-./data/offline}"
NATIVE_DIR="$(pwd)/jnetpcap/linux/jnetpcap-1.4.r1425"

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
    echo "Offline CICFlowMeter processing needs Java 8. Current Java: $java_version" >&2
    echo "Install it with: sudo apt install openjdk-8-jdk" >&2
    exit 1
    ;;
esac

mkdir -p "$OUTPUT_DIR"
export LD_LIBRARY_PATH="$NATIVE_DIR${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
./gradlew exeCMD -PpcapPath="$INPUT_PATH" -PoutPath="$OUTPUT_DIR"
