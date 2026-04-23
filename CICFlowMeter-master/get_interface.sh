#!/bin/sh

iface="$(awk '$2 == "00000000" { print $1; exit }' /proc/net/route)"

if [ -z "$iface" ]; then
  iface="eth0"
fi

printf '%s\n' "$iface" > interface
