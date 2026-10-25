# Stitch: pixel art animado

`MascoteStitch.kt` expõe `MascoteStitch(estado, fala, minimizado, silencioso, onMinimizar, onSilenciar, modifier)` no pacote `br.com.ander.rotas`.

Interpretação original em pixel art do Stitch (Disney). Atlas transparente de 640×880px, células80×80, **59 quadros únicos**. Sem emoji, GIF, download externo ou bitmap estático deslocado. Gerador: `python3 tools/generate_stitch_sprites.py` (Pillow). O script verifica a unicidade dos quadros por ação.

|Linha|Estado|Quadros|Cadência|
|---|---|---:|---|
|0|idle|6|Respiração, olhar e piscar|
|1|andar|8|130ms; pés e braços alternados|
|2|correr|8|75ms; passos amplos|
|3|pular|5|130ms; pernas recolhidas|
|4|acenar|6|180ms; mão acima da cabeça|
|5|apontar|4|180ms; dedo esticado|
|6|sentar|4|180ms; pés abertos|
|7|dormir|4|650ms; olhos fechados|
|8|celebrar|6|130ms; braços erguidos|
|9|duvida|4|180ms; sobrancelha levantada|
|10|toque|4|180ms; sorriso e bochechas|

Os quadros são extraídos como matrizes `SpriteFrame.pixels: IntArray` ARGB, convertidas em bitmap uma única vez. Canvas usa nearest-neighbor (`FilterQuality.None`). Braços, pés, orelhas e expressões são redesenhados por quadro. `stitch_sprite_sheet.png` é a prancha de inspeção; `stitch_preview.png` é uma ampliação do primeiro quadro. A arte foi inspecionada visualmente com `view_image` e restaurada sem mudança do desenho original.

## Integração

Largura máxima160dp, sprite108dp, controles48dp; minimizado48×48dp. Reserve espaço no painel sem sobrepor instruções ou controles do mapa. Os callbacks devem alternar os valores controlados de silenciar e minimizar.

Toque escolhe uma entre sete frases, excluindo a anterior, com reação2,8s; balões temporários até6s e no máximo3linhas. Nenhum áudio é emitido; silenciar oculta balões. Ícones vetoriais Material com rótulos TalkBack em português. Estado desconhecido retorna a `idle`.

O laço pausa fora de `Lifecycle.RESUMED`, fora da janela ou minimizado. Respeita `ANIMATOR_DURATION_SCALE=0`. Observadores são removidos quando o componente sai da composição. Quadros são limitados antes de acessar a animação ao trocar estado.
