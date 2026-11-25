#!/usr/bin/env bash
# make-replays.sh OUTDIR : synthetic drives at Plan 1's spot-check points, for debug replay.
# Each row: one fix per second, speed accuracy 0.5 m/s, position accuracy 5 m.
set -e
out=${1:-.}
mkdir -p "$out"

drive() { # file lat lon bearing speed-per-second...
  local f=$1 lat=$2 lon=$3 bearing=$4
  shift 4
  {
    echo "# SpeedBeep drive v1"
    echo "t_ms,lat,lon,speed_mps,speed_acc_mps,h_acc_m,bearing_deg"
    local t=0
    for kmh in "$@"; do
      awk -v t="$t" -v lat="$lat" -v lon="$lon" -v kmh="$kmh" -v b="$bearing" \
        'BEGIN { printf "%d,%.7f,%.7f,%.2f,0.5,5.0,%.1f\n", t * 1000, lat, lon, kmh / 3.6, b }'
      t=$((t + 1))
    done
  } > "$out/$f"
  echo "wrote $out/$f"
}

times() { for _ in $(seq "$1"); do printf '%s ' "$2"; done; }

# Seefeldstrasse, Zürich (posted 50 → TOWN): 5 s at 45, 15 s at 60, 10 s at 45.
drive town-over.csv 47.356325 8.555848 147 $(times 5 45) $(times 15 60) $(times 10 45)
# A1 near Winterthur (FAST): 20 s at 110.
drive motorway.csv 47.45 8.70 35 $(times 20 110)
# Stuttgart, outside the road file (NOT COVERED): 10 s at 60.
drive abroad.csv 48.78 9.18 0 $(times 10 60)
# Seefeldstrasse again, staying over for 2 minutes: for the notification-button checks.
drive long-over.csv 47.356325 8.555848 147 $(times 5 45) $(times 120 60)
