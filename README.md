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

## Instalação / release

Para compilar **todos** os scripts e gerar um pacote pronto para instalar
(pasta `CLEO\` + textos + sfx + docs):

```
GTA3SC=/caminho/gta3sc CLEO_XML=/caminho/cleo.xml ./tools/build_release.sh
```

O resultado sai em `release/Spider-Man_GTA_SA_<versao>_INSTALL.zip` e o log de
compilação (com o tamanho e o hash de cada `.cs`) em
`release/COMPILE_REPORT.txt`.

Dentro do zip:

| Caminho | Conteúdo |
|---|---|
| `CLEO\MSpiderJ16Dv7*.cs` | loader, eventos e música |
| `CLEO\cleo_text\MSpiderJ16Dv7.fxt` | textos do mod |
| `CLEO\SpiderJ16D\*.cs` | 31 scripts do mod (+ `sp_ai.cs`) |
| `CLEO\SpiderJ16D\config.ini` | configuração, incluindo a seção `[ai]` |
| `CLEO\SpiderJ16D\sfx\` | 96 efeitos sonoros |
| `CLEO\SpiderJ16D\music\` | vazio (mp3 de música ambiente não fazem parte do repo) |
| `INSTALL.txt` / `IA_MODOS.md` | instruções de instalação e documentação da IA |
| `EXTRAS\` | scripts opcionais (Screwball, SAMS, `spider_fx_system.txt`) |

O pacote **não** inclui modelos/skins/animações nem os `.fxs` de partículas:
esses arquivos continuam vindo da instalação oficial do mod.
