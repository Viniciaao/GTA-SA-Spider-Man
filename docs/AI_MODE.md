# IA do Spider-Man (`sp_ai`) — 3 modos de jogo

Adiciona um **Spider-Man controlado pela IA** ao mod, com três modos selecionáveis
pelo `CLEO\SpiderJ16D\config.ini`. A IA é *ambiental*: ela aparece no mapa, faz uma
sequência de ações e vai embora. Nada de menu, teclas ou HUD novos.

```
[ai]
mode = 1       ; 0 = normal | 1 = player + IA | 2 = só IA
chance = 35    ; % de chance de encontro a cada "interval"
interval = 45000
suit = 2       ; skin usada pela IA (1-9, ou 0 = ped comum)
```

---

## Os três modos

| `mode` | Nome | O que acontece |
|:--:|---|---|
| `0` | **Normal** | O mod funciona como sempre. Nenhuma IA no mapa. |
| `1` | **Player + IA** | O jogador pode ser o Spider-Man (menu, teclas, HUD, missões…) **e**, de tempos em tempos, pode encontrar um Spider-Man da IA pelo mapa. |
| `2` | **Só IA** | O jogador **não tem nada** do mod: sem menu, sem Ctrl+O, sem mensagens, sem HUD, sem poderes, sem teias. O `sp_main` e todos os scripts do jogador nunca são iniciados. Quem existe é só a IA, que pode ser encontrada pelo mapa. |

No modo 2 o `MSpiderJ16Dv7.cs` fica em um *watchdog* (1 verificação a cada 2 s): só
garante que a thread da IA está viva e não faz mais nada. `sp_msc` (música) e `m_ev`
(eventos) ficam parados esperando o mod ser ativado — e ele nunca é.

---

## O que a IA faz

Quando o sorteio de encontro dá certo (e o jogador está a pé, no mapa aberto, fora de
interior/missão/menu/cutscene), a IA:

1. **Spawna fora da tela**, 85 m atrás do jogador (checagem `IS_POINT_ON_SCREEN`,
   `GET_GROUND_Z_FOR_3D_COORD` e teste de água — se não der, tenta mais 3 vezes);
2. **chega balançando** (arcos de "web swing" com colisão desligada + anim `jump_glide_A`),
   pousa perto do jogador e cumprimenta;
3. executa **3 ações aleatórias** (patrulha, crime de rua, perseguição de carro);
4. **vai embora** balançando 3 arcos para longe e é removida fora da tela.

Cada ação, quando **termina**, passa para a próxima (`actionsDone++`). Ao fim da
sequência a IA sai e o script volta ao loop de espera.

### Ações

| Ação | Comportamento | Como termina |
|---|---|---|
| `ACT_ARRIVE` | voa até 10 m na frente do jogador e faz pose (`menu_idle_A`) | ao pousar |
| `ACT_PATROL` | 5 "swings" em volta do jogador (25–70 m), com uma pose no meio | após o 5º salto |
| `ACT_CRIME` | cria 3 bandidos (`BALLAS1`, `BALLAS2`, `VMAFF1`, faca/bastão) atacando; a IA atira teias (`sp_we_a`, dano por script) e parte para cima | quando os 3 caem (ou 90 s) |
| `ACT_CHASE` | cria um carro (`SENTINEL`) com motorista fugindo; a IA persegue, "teia" o carro até parar e luta com o motorista | quando o motorista cai (ou 45 s) |
| `ACT_LEAVE` | 3 arcos de 45 m para longe | ao fim dos saltos |

Mensagens (`~b~~h~Spider-Man~w~: ...`) aparecem em inglês ou espanhol conforme o
`LANG` do `config.ini` — e **nunca** no modo 2. O blip no radar também só existe no modo 1.

---

## Otimização

* Um **único** ator de IA por vez (a cópia extra de thread é bloqueada pela var 59).
* Loop de espera: 1 verificação/s + `WAIT` longo entre sorteios (nada de `WAIT 0` girando).
* Movimento por interpolação de coordenadas a cada **110 ms** (poucas operações por
  tick) em vez de calcular física por frame.
* Sem varredura de *peds* do mundo (`GET_ANY_CHAR_NO_SAVE_RECURSIVE`) — a IA só conhece
  os handles que ela mesma criou.
* Aborto imediato do encontro (e limpeza de tudo) se: jogador morre/preso, entra em
  carro, abre o menu, entra em interior, começa missão, o mod é desativado (modo 1) ou
  se afasta mais de 260 m.
* Modelos são requisitados no início do encontro e liberados no `cleanup`
  (`MARK_MODEL_AS_NO_LONGER_NEEDED`). A skin fica no slot `SPECIAL07`
  (1-5 = street crimes, 8 = skin do jogador, 9 = missões).
* Antes de carregar a skin o script confere se o modelo existe
  (`GET_MODEL_BY_NAME`, CLEO+) e espera `HAS_SPECIAL_CHARACTER_LOADED`. Se a skin
  não estiver instalada, a IA nasce com um ped comum em vez de tentar carregar um
  modelo inexistente (isso crashava o jogo) e grava `suit_ok = 0` no `[ai_stats]`.

---

## Ajustes finos no código (`sp_ai.sc`)

Além do `config.ini`, algumas constantes no topo do `sp_ai.sc` controlam a IA:

| Constante | Padrão | Para que serve |
|---|:--:|---|
| `ai_actions_encounter` | `3` | quantas ações a IA faz antes de ir embora |
| `ai_thug_health` | `170` | vida dos bandidos |
| `ai_web_damage` | `25` | dano de cada teia |
| `ai_arc_height` | `10.0` | altura do arco do "web swing" |
| `ai_max_leap_ticks` | `14` | duração/tamanho do arco |
| `ai_spawn_back` | `-85.0` | distância atrás do jogador onde ela nasce |
| `ai_despawn_distance` | `260.0` | acima disso ela desiste e desaparece |
| `ai_special_slot` | `7` | slot `SPECIAL0N` da skin da IA |
| `ai_use_spider_anim` | `1` | usa o grupo de animação `spider` na IA (coloque `0` se ela ficar em T-pose) |

---

## Arquivos

| Arquivo | O que é |
|---|---|
| `GTA3Script Source/sp_ai.sc` | código-fonte da IA |
| `cleo/SpiderJ16D/sp_ai.cs` | IA compilada |
| `GTA3Script Source/MSpiderJ16Dv7.sc` | menu/loader — agora lê o `[ai] mode` e trata o modo 2 |
| `cleo/MSpiderJ16Dv7.cs` | menu/loader compilado |
| `cleo/SpiderJ16D/config.ini` | seções `[ai]` e `[ai_stats]` |
| `tools/build_ai.sh` | recompila os dois scripts |

`[ai_stats]` guarda quantos encontros, crimes resolvidos e perseguições a IA concluiu
(só curiosidade).

---

## Compilando

O `.cs` já vem compilado no repositório, mas se quiser refazer:

```bash
# precisa do gta3sc (https://github.com/thelink2012/gta3sc)
# e do cleo.xml do CLEO+ (https://github.com/JuniorDjjr/CLEOPlus, pasta
# "(for developers)/gta3script/cleo.xml")

cd "GTA3Script Source"
gta3sc compile sp_ai.sc --config=gtasa --add-config=caminho/para/cleo.xml \
        --cs --guesser -fbreak-continue -o ../cleo/SpiderJ16D/sp_ai.cs

gta3sc compile MSpiderJ16Dv7.sc --config=gtasa --add-config=caminho/para/cleo.xml \
        --cs --guesser -fbreak-continue -o ../cleo/MSpiderJ16Dv7.cs
```

Ou simplesmente:

```bash
GTA3SC=gta3sc CLEO_XML=/caminho/cleo.xml ./tools/build_ai.sh
```

> Os binários do repositório foram gerados com essas flags. Compilando os scripts
> antigos com elas (ex.: `sp_st.sc`, `sp_lf.sc`, `sp_wep.sc`) o resultado sai
> **byte a byte igual** aos `.cs` que já existiam — ou seja, é a mesma cadeia de
> ferramentas do mod.

---

## Se a IA não aparecer

1. `mode` precisa ser `1` ou `2` no `config.ini` (e reinicie o jogo — o modo é lido no
   carregamento do `MSpiderJ16Dv7.cs`);
2. no modo 1 o mod do jogador precisa estar **ativado** (Ctrl+O → ativar Spider-Man);
3. o jogador tem que estar **a pé**, no mapa aberto (não em interior), fora de missão,
   longe do menu e de cutscene;
4. `chance` acima de 0 e `sp_ai.cs` presente em `CLEO\SpiderJ16D\`;
5. para testar rápido: `chance = 100` e `interval = 5000`;
6. se o modelo `sN` da skin escolhida não estiver instalado, a IA não consegue ser
   criada — troque `suit` para `2` (skin clássica, a mesma do menu).

Para depurar, `[ai_stats]` no `config.ini` é incrementado a cada encontro/atado.
