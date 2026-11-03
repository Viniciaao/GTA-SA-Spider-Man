#!/usr/bin/env bash
# Rebuilds the Spider-Man AI (sp_ai.cs) and the menu/loader (MSpiderJ16Dv7.cs).
#
# Requirements:
#   - gta3sc .................. https://github.com/thelink2012/gta3sc
#   - CLEO+ gta3sc config ..... https://github.com/JuniorDjjr/CLEOPlus
#                               ("(for developers)/gta3script/cleo.xml")
#
# Usage:
#   GTA3SC=/path/to/gta3sc CLEO_XML=/path/to/cleo.xml ./tools/build_ai.sh
#
# Defaults: GTA3SC=gta3sc (from PATH), CLEO_XML=./cleo.xml
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GTA3SC="${GTA3SC:-gta3sc}"
CLEO_XML="${CLEO_XML:-cleo.xml}"
SRC="$ROOT/GTA3Script Source"

if ! command -v "$GTA3SC" >/dev/null 2>&1; then
    echo "error: gta3sc not found (set GTA3SC=/path/to/gta3sc)" >&2
    exit 1
fi
if [ ! -f "$CLEO_XML" ]; then
    echo "error: CLEO+ cleo.xml not found (set CLEO_XML=/path/to/cleo.xml)" >&2
    exit 1
fi

# flags used by the mod: CLEO script, extensions and BREAK/CONTINUE inside loops
build() {
    local file="$1" out="$2"
    echo ">>> $file -> $out"
    ( cd "$SRC" && "$GTA3SC" compile "$file" \
        --config=gtasa \
        --add-config="$CLEO_XML" \
        --cs --guesser -fbreak-continue \
        -o "$out" )
}

build sp_ai.sc           "$ROOT/cleo/SpiderJ16D/sp_ai.cs"
build MSpiderJ16Dv7.sc   "$ROOT/cleo/MSpiderJ16Dv7.cs"

echo "done."
