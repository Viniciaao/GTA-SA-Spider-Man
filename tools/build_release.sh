#!/usr/bin/env bash
# ============================================================================
#  build_release.sh - compiles every Spider-Man mod script and packs a
#                     ready-to-install ZIP (CLEO folder layout).
#
#  Requirements:
#    - gta3sc ................. https://github.com/thelink2012/gta3sc
#    - CLEO+ gta3sc config .... https://github.com/JuniorDjjr/CLEOPlus
#                               ("(for developers)/gta3script/cleo.xml")
#    - zip
#
#  Usage:
#    GTA3SC=/path/to/gta3sc CLEO_XML=/path/to/cleo.xml ./tools/build_release.sh
#
#  Optional environment variables:
#    VERSION=1.9-AI      -> name of the package / zip
#    OUTDIR=release      -> where the zip is written
#    SKIP_EXTRAS=1       -> do not build the optional scripts
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GTA3SC="${GTA3SC:-gta3sc}"
CLEO_XML="${CLEO_XML:-cleo.xml}"
VERSION="${VERSION:-1.9-AI}"
OUTDIR="${OUTDIR:-$ROOT/release}"
SRC="$ROOT/GTA3Script Source"

NAME="Spider-Man_GTA_SA_${VERSION}_INSTALL"
STAGE="$OUTDIR/$NAME"
ZIP="$OUTDIR/$NAME.zip"
CLEO="$STAGE/CLEO"
SUBFOLDER="$CLEO/SpiderJ16D"

command -v "$GTA3SC" >/dev/null 2>&1 || { echo "error: gta3sc not found (set GTA3SC=...)" >&2; exit 1; }
[ -f "$CLEO_XML" ] || { echo "error: cleo.xml not found (set CLEO_XML=...)" >&2; exit 1; }
command -v zip >/dev/null 2>&1 || { echo "error: zip not found" >&2; exit 1; }

rm -rf "$STAGE" "$ZIP"
mkdir -p "$CLEO/cleo_text" "$SUBFOLDER" "$STAGE/EXTRAS"

REPORT="$OUTDIR/COMPILE_REPORT.txt"
: > "$REPORT"
say() { printf '%s\n' "$*" | tee -a "$REPORT"; }

# compile_script <source file (absolute or relative to SRC)> <output .cs>
compile_script() {
    local file="$1" out="$2" mode="${3:-cs}"
    local dir base
    dir="$(cd "$(dirname "$file")" && pwd)"
    base="$(basename "$file")"
    local flag="--cs"
    [ "$mode" = "cm" ] && flag="--cm"

    # sp_main.sc uses "REQUIRE funcs.sc", which gta3sc looks up inside a
    # directory named after the script -> build it from a temporary mirror
    if [ "$base" = "sp_main.sc" ]; then
        local tmp
        tmp="$(mktemp -d)"
        mkdir -p "$tmp/sp_main"
        cp "$dir/funcs.sc" "$tmp/sp_main/funcs.sc"
        cp "$file" "$tmp/sp_main.sc"
        ( cd "$tmp" && "$GTA3SC" compile sp_main.sc --config=gtasa --add-config="$CLEO_XML" \
            $flag --guesser -fbreak-continue -o "$out" ) >>"$REPORT" 2>&1
        rm -rf "$tmp"
    else
        ( cd "$dir" && "$GTA3SC" compile "$base" --config=gtasa --add-config="$CLEO_XML" \
            $flag --guesser -fbreak-continue -o "$out" ) >>"$REPORT" 2>&1
    fi
}

# build the install set and compare with the binary that was already in the repo
# build_one <source.sc> <output.cs> [reference binary already in the repo]
build_one() {
    local src="$1" out="$2" ref="${3:-}"
    local label
    label="$(basename "$out")"
    local before=""
    if [ -n "$ref" ] && [ -f "$ref" ]; then before="$(sha1sum "$ref" | cut -d' ' -f1)"; fi
    compile_script "$src" "$out"
    local after size status
    after="$(sha1sum "$out" | cut -d' ' -f1)"
    size="$(stat -c%s "$out" 2>/dev/null || stat -f%z "$out")"
    if [ -z "$before" ]; then
        status="novo"
    elif [ "$before" = "$after" ]; then
        status="identico ao .cs que ja estava no repo"
    else
        status="recompilado (diferente do .cs que ja estava no repo)"
    fi
    say "  OK  $label  -  ${size} bytes  -  $status"
}

echo ">>> limpando estagio"
mkdir -p "$SUBFOLDER"

echo ">>> compilando scripts do CLEO (raiz)"
for f in MSpiderJ16Dv7 MSpiderJ16Dv7_events MSpiderJ16Dv7_music; do
    build_one "$SRC/$f.sc" "$CLEO/$f.cs" "$ROOT/cleo/$f.cs"
done

echo ">>> compilando scripts do CLEO/SpiderJ16D"
SUB_SCRIPTS=(
    "sp_3d" "sp_ai" "sp_dw" "sp_ev" "sp_evb" "sp_hud" "sp_lf" "sp_lvl" "sp_main"
    "sp_mb" "sp_me" "sp_ml" "sp_mm" "sp_po" "sp_prt" "sp_res" "sp_st" "sp_wep"
    "wa" "wa_1" "wb" "wc" "wd" "we" "we_1" "wf" "wg" "wh"
)
for f in "${SUB_SCRIPTS[@]}"; do
    build_one "$SRC/$f.sc" "$SUBFOLDER/$f.cs" "$ROOT/cleo/SpiderJ16D/$f.cs"
done

# mission scripts (they live in their own folders in the source tree)
MISSION_SCRIPTS=(
    "mission_script_spiderman/Car Chase/sp_cc"
    "mission_script_spiderman/Car Chase/sp_cd"
    "mission_script_spiderman/Street Crimes (waves)/m_w"
    "mission_script_spiderman/Thug Hideouts/m_th"
    "mission_script_spiderman/backpacks/m_bp"
)
for f in "${MISSION_SCRIPTS[@]}"; do
    build_one "$SRC/$f.sc" "$SUBFOLDER/$(basename "$f").cs" "$ROOT/cleo/SpiderJ16D/$(basename "$f").cs"
done

echo ">>> copiando assets (config, textos, sons)"
cp "$ROOT/cleo/SpiderJ16D/config.ini" "$SUBFOLDER/config.ini"
cp "$ROOT/cleo/cleo_text/MSpiderJ16Dv7.fxt" "$CLEO/cleo_text/MSpiderJ16Dv7.fxt"
mkdir -p "$SUBFOLDER/sfx" "$SUBFOLDER/music"
if [ -d "$ROOT/sfx" ]; then
    cp "$ROOT/sfx"/*.mp3 "$SUBFOLDER/sfx/" 2>/dev/null || true
fi
say "  OK  config.ini / MSpiderJ16Dv7.fxt / $(ls "$SUBFOLDER/sfx" | wc -l) mp3 em sfx/"

# the ambient music mp3s are not part of this repository: leave instructions
cat > "$SUBFOLDER/music/LEIA-ME.txt" <<'EOF'
Coloque aqui os mp3 de musica ambiente do mod (opcional):

  sfx_menuA.mp3  sfx_menuB.mp3
  sfx_dayA.mp3   sfx_dayB.mp3   sfx_dayC.mp3
  sfx_nightA.mp3 sfx_nightB.mp3 sfx_nightC.mp3
  sfx_cc_1.mp3   sfx_cc_2.mp3   sfx_cc_3.mp3
  sfx_th_1.mp3   sfx_th_2.mp3   sfx_th_3.mp3

Esses arquivos NAO fazem parte deste repositorio. Sem eles o mod funciona
normalmente, apenas sem a musica ambiente (o script usa DOES_FILE_EXIST).
EOF

if [ "${SKIP_EXTRAS:-0}" != "1" ]; then
    echo ">>> compilando scripts opcionais (EXTRAS)"
    EXTRAS_OK=0
    if compile_script "$SRC/mission_script_spiderman/Screwball/screwball_challenges.sc" \
                      "$STAGE/EXTRAS/screwball_challenges.cs"; then
        say "  OK  screwball_challenges.cs"
        EXTRAS_OK=1
    fi

    # screwball_challenge_a.sc declares FONT_* constants that the CLEO+ xml
    # already provides -> strip those enums from a temporary config copy
    if command -v python3 >/dev/null 2>&1; then
        TMPXML="$(mktemp)"
        python3 - "$CLEO_XML" > "$TMPXML" <<'PY'
import re, sys
d = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
for block in re.findall(r'<Enum\b[^>]*>.*?</Enum>', d, re.S):
    if 'Name="FONT_' in block:
        d = d.replace(block, '')
sys.stdout.write(d)
PY
        OLD_XML="$CLEO_XML"
        CLEO_XML="$TMPXML"
        if compile_script "$SRC/mission_script_spiderman/Screwball/screwball_challenge_a.sc" \
                          "$STAGE/EXTRAS/screwball_challenge_a.cm" cm; then
            say "  OK  screwball_challenge_a.cm"
            EXTRAS_OK=1
        fi
        CLEO_XML="$OLD_XML"
        rm -f "$TMPXML"
    fi

    mkdir -p "$STAGE/EXTRAS/SAMS"
    for f in m_sams_mission2 sp_camb sp_dig sp_prtb; do
        if compile_script "$SRC/SAMS GTA Loyalty Missions/Mission 2/$f.sc" \
                          "$STAGE/EXTRAS/SAMS/$f.cs"; then
            say "  OK  SAMS/$f.cs"
            EXTRAS_OK=1
        fi
    done
    say "  --  wars.sc e sams_m2.sc nao compilam com o config atual do CLEO+"
    [ "$EXTRAS_OK" = "1" ] || rmdir "$STAGE/EXTRAS" 2>/dev/null || true
fi

[ -d "$STAGE/EXTRAS" ] || rmdir "$STAGE/EXTRAS" 2>/dev/null || true
mkdir -p "$STAGE/EXTRAS"
cat > "$STAGE/EXTRAS/LEIA-ME.txt" <<'EOF'
Scripts OPCIONAIS - nao precisam ser instalados para o mod funcionar.

  screwball_challenges.cs .... script de teste (tecla P) do desafio Screwball
  screwball_challenge_a.cm ... misso customizada usada pelo script acima
  SAMS/ ...................... missoes do SAMS GTA Loyalty Missions
                               (precisam do framework SAMS instalado)

wars.sc e sams_m2.sc nao compilam com o config atual do CLEO+ e nao sao
usados pelo mod instalado.
EOF

cp "$ROOT/docs/AI_MODE.md" "$STAGE/IA_MODOS.md" 2>/dev/null || true

# particle system definition (optional: needs the .fxs from the official install)
if [ -f "$ROOT/FX System (Particles)/spider_fx_system.txt" ]; then
    mkdir -p "$STAGE/EXTRAS/FX"
    cp "$ROOT/FX System (Particles)/spider_fx_system.txt" "$STAGE/EXTRAS/FX/spider_fx_system.txt"
    cat > "$STAGE/EXTRAS/FX/LEIA-ME.txt" <<'FXEOF'
spider_fx_system.txt e uma definicao de FX_SYSTEM_DATA (109 sistemas de
particulas) que vai em  SA\data\effects\  junto com os arquivos .fxs
referenciados por ela.

Os .fxs e as texturas (sp_head_sphere etc.) NAO fazem parte deste pacote:
copie-os da instalacao oficial do mod. Sem eles o arquivo acima nao faz nada.
FXEOF
fi

cat > "$STAGE/INSTALL.txt" <<'EOF'
============================================================
 GTA SA Spider-Man v1.9 (+ IA)  -  pacote de instalacao
============================================================

REQUISITOS
  - GTA San Andreas 1.0 US (PC)
  - CLEO 4.4
  - CLEO+  (obrigatorio: o mod usa opcodes do CLEO+)
  - NAO use o newopcodes.cleo junto (conflita)

COMO INSTALAR
  1. Copie a pasta CLEO deste pacote para a raiz do GTA San Andreas,
     juntando com a pasta CLEO que ja existe (substitua os arquivos).
  2. Rode o jogo e aperte CTRL + O para abrir o menu do mod.

O que vai ser copiado:
  CLEO\MSpiderJ16Dv7.cs            -> menu / loader
  CLEO\MSpiderJ16Dv7_events.cs     -> eventos (perseguicoes, crimes)
  CLEO\MSpiderJ16Dv7_music.cs      -> musica ambiente
  CLEO\cleo_text\MSpiderJ16Dv7.fxt -> textos do mod
  CLEO\SpiderJ16D\config.ini       -> configuracao (inclui o modo da IA)
  CLEO\SpiderJ16D\*.cs             -> scripts do mod + IA (sp_ai.cs)
  CLEO\SpiderJ16D\sfx\*.mp3        -> efeitos sonoros
  CLEO\SpiderJ16D\music\           -> coloque aqui os mp3 de musica (opcional)

MODO DA IA  (CLEO\SpiderJ16D\config.ini -> secao [ai])
  mode = 0   Normal ....... o jogador pode ser o Spider-Man, sem IA
  mode = 1   Player + IA .. o jogador pode ser o Spider-Man e as vezes
                            encontra um Spider-Man controlado pela IA
  mode = 2   So IA ........ o jogador nao tem NADA do mod (sem menu, sem
                            teclas, sem mensagens, sem HUD). Só a IA existe.

  chance   = % de chance de encontro a cada "interval"
  interval = tempo entre sorteios, em ms (minimo 5000)
  suit     = skin usada pela IA (1-9)

  Detalhes completos: IA_MODOS.md

O QUE NAO VAI NESTE PACOTE
  - Modelos, skins (.dff/.txd), animacoes (anim\spider.ifp) e o sistema de
    particulas (effects) NAO fazem parte deste repositorio. Use os arquivos
    da instalacao oficial do mod; este pacote atualiza a parte de scripts.
  - Os mp3 de musica ambiente (CLEO\SpiderJ16D\music) tambem nao estao no
    repositorio. O mod roda sem eles (apenas sem musica).

DICA DE TESTE DA IA
  No config.ini deixe chance = 100 e interval = 5000, ative o mod (Ctrl+O) e
  fique a pe na rua. A secao [ai_stats] do config.ini conta os encontros.
EOF

echo ">>> gerando zip"
( cd "$OUTDIR" && zip -r -q "$(basename "$ZIP")" "$(basename "$STAGE")" )
rm -rf "$STAGE"

say ""
say "pacote: $ZIP  ($(du -h "$ZIP" | cut -f1))"
say "relatorio: $REPORT"
