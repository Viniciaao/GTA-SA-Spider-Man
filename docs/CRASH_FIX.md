# Crash da IA — `scrlog1.log` (corrigido)

Relatório do crash enviado pelo autor do mod, analisado com o log do SCRLog.

## Sintoma

O jogo fecha sozinho alguns segundos depois de a IA aparecer. O log terminava assim:

```
00001219&0: [856D] NOT DOES_CHAR_EXIST 0    // TRUE
00001224&1: [004D] GOTO_IF_FALSE -1233
00001231&1: [0485] IS_PC_VERSION    // TRUE
00001233&1: [00D6] IF 0
00001237&0: [0118] IS_CHAR_DEAD 0    // TRUE
00001242&1: [004D] GOTO_IF_FALSE -1251
00001249&1: [0485] IS_PC_VERSION    // TRUE
00001251&1: [00D6] IF 0
00001255&0: [8EE4] NOT LOCATE_CHAR_DISTANCE_TO_CHAR 0 1 260.0
```

A seção é do script `sp_ai`, com `ai_actor` (`l7`) = `0` — ou seja, o handle do
Spider-Man da IA já era inválido naquele frame. O crash acontece ao executar
`LOCATE_CHAR_DISTANCE_TO_CHAR` com um ped que não existe mais.

## Causa raiz

A sub-rotina `ai_shouldabort` saía com `RETURN_TRUE` justamente quando o ator não
existia:

```
IF NOT DOES_CHAR_EXIST ai_actor
    RETURN_TRUE
ENDIF
```

Só que **`RETURN_TRUE` / `RETURN_FALSE` não retornam** com o compilador
`gta3sc` para San Andreas: eles caem na definição `ID="0xc5" Supported="false"`
do `commands.xml` e são emitidos como `0x0485` / `0x059A` — opcodes de *condição*
(sempre-verdadeiro / sempre-falso). O efeito é:

* o resultado da condição até fica certo;
* a sub-rotina **não** retorna: a execução continua na checagem seguinte (e, no
  fim da rotina, cai dentro da rotina seguinte no arquivo compilado).

No log isso aparece como `[0485] IS_PC_VERSION // TRUE` no lugar do `RETURN`, e
a execução seguindo direto para o próximo `IF` (e o próximo, e o próximo) até
chegar em `LOCATE_CHAR_DISTANCE_TO_CHAR` com handle inválido → crash.

Comprovação isolada (compilar `IF NOT DOES_CHAR_EXIST a / RETURN_TRUE / ENDIF`):

```
d6 00 04 00 | 6d 85 03 00 00 | 4d 00 01 dc ff ff | 85 04 ...   <- 0485, sem 0051 (RETURN)
```

## Correção

Em `GTA3Script Source/sp_ai.sc`, as duas rotinas booleanas (`ai_can_encounter` e
`ai_shouldabort`) passaram a devolver o resultado em `iTemp` (`0` = sim / `1` =
abortar) e a sair com `RETURN` normal (`0x0051`), que funciona:

```
ai_shouldabort:
    iTemp = 1
    IF NOT IS_PLAYER_PLAYING 0
        RETURN
    ENDIF
    GOSUB read_vars
    ...
    IF DOES_CHAR_EXIST ai_actor
        IF IS_CHAR_DEAD ai_actor
            RETURN
        ENDIF
        IF NOT LOCATE_CHAR_DISTANCE_TO_CHAR ai_actor player_actor ai_despawn_distance
            RETURN
        ENDIF
        iTemp = 0
    ENDIF
RETURN
```

Os 8 pontos de chamada viraram `GOSUB ai_shouldabort` + `IF iTemp = 1`, e o loop
principal usa `GOSUB ai_can_encounter` + `IF iTemp = 0`.

A checagem de distância também foi aninhada dentro de `IF DOES_CHAR_EXIST
ai_actor`, então um handle inválido não chega mais nesse opcode nem por acidente.

Resultado: `sp_ai.cs` 8121 → 8217 bytes, sem nenhum `RETURN_TRUE`/`RETURN_FALSE`
(sobra apenas o comentário explicativo).

## Observação para o resto do mod

Os outros scripts do projeto (`funcs.sc`, `sp_ev.sc`, `sp_ml.sc`, `sp_mb.sc`,
`wh.sc`, `sp_hud.sc`, `sp_po.sc`, `sp_dw.sc`, ...) usam o mesmo idioma
`RETURN_TRUE`/`RETURN_FALSE` em sub-rotinas chamadas com `IF GOSUB ...`. Nos
binários publicados (inclusive os do build original) esses pontos também viram
`0x0485`/`0x059A`, ou seja, também não retornam. Nada foi alterado neles aqui —
é um trabalho separado, caso apareçam bugs do mesmo tipo nas teias/paredes.

---

# Crash #2 — `scrlog2.log` (carregamento da skin)

Depois da correção acima o `sp_ai` passou a rodar de verdade (no log dá para ver
as rotinas novas: `l11 = 0` + `RETURN` em `ai_can_encounter` e
`IF l11 == 0` no loop principal). O novo crash acontece no **primeiro encontro**,
na hora de carregar a skin:

```
00001573&1: [0006] l7(0) = 0            <- spawn_ai: ai_actor = 0
00001580&1: [0050] GOSUB -1930          <- set_suit_model
00001930&1: [0871] SWITCH_START 2 9 ...
00002078&1: [023C] LOAD_SPECIAL_CHARACTER 10 "S2"
```

O jogo morre dentro de `LOAD_SPECIAL_CHARACTER`. Dois caminhos levam a isso:

1. **modelo inexistente** — se o `.dff` da skin (`s1`..`s9`) não estiver no
   `gta3.img`/IDE, o request não encontra o modelo e o jogo cai;
2. **slot fora de faixa** — o slot era `10`; o mod nunca usa 10 (usa 1-5 em
   `m_w.sc`, 8 nas skins do jogador e 9 nas missões).

## Correção

* `ai_special_slot` 10 → **7** (`SPECIAL07`), um slot livre que já é usado pela
  própria engine em outros mods.
* Antes de chamar `LOAD_SPECIAL_CHARACTER` o script confere se o modelo existe:
  `IF GET_MODEL_BY_NAME "s2" (iTick)` (opcode CLEO+ `0xE9C`). Sem isso, um modelo
  ausente ia direto para o opcode que crasha.
* Depois do load: `LOAD_ALL_MODELS_NOW` e espera de até ~2 s por
  `HAS_SPECIAL_CHARACTER_LOADED ai_special_slot` (023D).
* Se a skin não carregar, a IA nasce com um ped comum (`VMAFF1`) e o script põe
  `iSuit = 0`, então não tenta carregar de novo a cada encontro (continua barato).
* `UNLOAD_SPECIAL_CHARACTER` logo depois do `CREATE_CHAR` (mesmo padrão do
  `MSpiderJ16Dv7.sc`, que usa o slot 8).
* Diagnóstico silencioso: quando a skin falha, `sp_ai` grava
  `suit_ok = 0` na seção `[ai_stats]` do `config.ini`. Se essa chave aparecer,
  os modelos das skins não estão instalados na pasta do jogo.
* `suit = 0` no `[ai]` desliga a skin de vez (a IA sempre usa ped comum).

`sp_ai.cs` 8217 → **8727 bytes**.
