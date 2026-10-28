# Validação 1.1.0 — 28/10/2026

`assembleDebug testDebugUnitTest lintDebug`: BUILD SUCCESSFUL. JDK17, SDK35, Gradle8.10.2. Foram executados49 testes, sem falhas, erros ou ignorados. A suíte inclui regressões de contagem3/2/1, GPS impreciso, ônibus parado antes do ponto, desembarque antes de uma caminhada, bloqueio dos caminhos de edição durante viagem, confirmação de cancelamento, ordenação de paradas, favoritas na ida/volta, GeoJSON e compatibilidade dos roteiros antigos.

Lint: 0 erros e 4 avisos. Avisos identificados: InlinedApi, ModifierParameter, DataExtractionRules, StaticFieldLeak. APIs antigas de anotações do MapLibre e ícones legados continuam emitindo avisos de depreciação na compilação.

APK: `Ander-Rotas-v1.1.0.apk`, 61498916bytes. SHA256: `8d3313b434391f22e5f282e4ad1a010a57c8893ad08c23bc1ff7eb0c736d4bca`. Pacote `br.com.ander.rotas`, versão1.1.0/código2, minSdk26, targetSdk35. `apksigner verify` aprovou assinatura v2; certificadoSHA256 `65a0bdbc46677d33a4dc15a664608db39e4c918698aeecd2995bb0c4b3ef7fb7`, igual ao APK1.0.0. Pode atualizar sem trocar pacote ou chave.

Consulta real à API Photon por Estação Luz retornou seis sugestões, incluindo Estação da Luz, em São Paulo. Testes de serviço/GPS são unitários ou simulados; não representam validação física no GalaxyA35 nem viagem com tela bloqueada, volume, vibração e bateria reais. A revisão independente identificou quatro problemas e a nova suíte passou após suas correções.

O pipeline emulado foi atualizado para testar o APK recém-compilado e conferir origem automática, ausência de destino predefinido e digitação no campo de sugestões. Seu resultado deve ser consultado na execução específica; a validação local acima não afirma que o emulador foi executado.
