# SpeedBeep

Map-less Android app that beeps when you drive over a set speed (default 53 km/h),
except on motorways and roads posted above 50. Design: `docs/superpowers/specs/2026-11-25-speedbeep-design.md`.

Modules:
- `:core`: road file format, road matcher, beep rules (plain Kotlin, unit-tested)
- `:roadbuilder`: PC tool that builds `app/src/main/assets/roads-ch.bin` from the Geofabrik Switzerland extract

Road data © OpenStreetMap contributors, ODbL.
