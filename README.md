# GTA SA Spider-Man v1.9 Beta
https://sites.google.com/view/j16d

https://www.moddb.com/mods/spider-man-mod-gta-sa


You probably need:
https://gtaforums.com/topic/876530-gta3script-toolchain/


Tutorials:
https://forum.mixmods.com.br/f141-secao-tutorial-gta3script/t26-indice-de-tutoriais-cleo-script-gta3script


## Spider-Man AI (`sp_ai`) — 3 game modes

The mod now ships an AI controlled Spider-Man that can be found around the map.
It is configured in `CLEO\SpiderJ16D\config.ini`, section `[ai]`:

| `mode` | Behavior |
|:--:|---|
| `0` | **Normal** — player can be Spider-Man, no AI |
| `1` | **Player + AI** — player can be Spider-Man and may meet the AI Spider-Man |
| `2` | **AI only** — the player gets nothing from the mod (no menu, no keys, no messages, no hud). Only the AI exists |

The AI spawns off screen, swings in, performs a random sequence of actions
(patrol / street crime / car chase) and advances to the next action every time
one is finished; at the end it leaves. `chance` and `interval` control how often
it can be found.

* Documentation (PT-BR): [docs/AI_MODE.md](docs/AI_MODE.md)
* Source: `GTA3Script Source/sp_ai.sc` → `cleo/SpiderJ16D/sp_ai.cs`
* Rebuild: `GTA3SC=gta3sc CLEO_XML=cleo.xml ./tools/build_ai.sh`
