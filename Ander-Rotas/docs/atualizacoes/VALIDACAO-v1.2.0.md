# Validação Rotas 1.2.0

Código Android verificado: 34ce175526e17570aefc3ce680684409b3f7676c.

## Compilação e testes locais

- assembleDebug e assembleDebugAndroidTest concluídos com sucesso, JDK17/SDK35.
- 57 testes JVM, zero falhas/erros.
- lintDebug: zero erros, cinco avisos existentes (InlinedApi, ModifierParameter, DataExtractionRules, StaticFieldLeak, ClickableViewAccessibility).
- Revisão adicional de busca/cache, notificações, painel, mapa e introdução: nenhum bloqueio concreto pendente após correções.

## Interface Android API35

GitHub Actions run40774451839, commit07d5504964342141312018d033fb2a609b694693: compilação/verificação e interface-emulada aprovadas.

Oito testes instrumentados passaram: sugestões HTTP durante a digitação e seleção; botões, arraste e pinça alterando a câmera real; limpeza de avisos antigos sem viagem; notificação somente após Ir e remoção ao cancelar; camadas de texto preservadas no mapa; painel sem geometria sem falso0m; painel com linha, posição atual e próximo ponto; introdução ilustrada concluída uma vez e ausente na reabertura.

O SDK tem limiar de velocidade no reconhecimento de escala. O teste inicial de pinça não foi reconhecido; a rodada aprovada usa pinça diagonal e registrou zoom11.0→11.8407 e um gesto no detector nativo. Capturas aguardam a renderização. Smoke adicional confirmou início sem destino, campo de origem GPS, abertura da busca e ausência de exceção fatal. As permissões já estavam concedidas pelos testes instrumentados; essa rodada não demonstra o diálogo de primeira permissão.

## APK entregue

- Arquivo: Rotas-v1.2.0.apk (61.657.697 bytes).
- Pacote: br.com.ander.rotas; versão1.2.0; código3; nome visível Rotas.
- SHA256 do APK: 4ee6ee01ae02594223e809b9d6f7fdecee57491a2802ae7bcf6c9d7e4d596051.
- Certificado SHA256: 65a0bdbc46677d33a4dc15a664608db39e4c918698aeecd2995bb0c4b3ef7fb7 (mesma assinatura original, verificada com apksigner).

## Limites

Emulador e fixtures verificam comportamento da interface e regras de navegação; não substituem uma viagem física. Resultados de endereço dependem da rede e da cobertura dos provedores. Vibração, voz e alertas dependem das permissões e configurações do aparelho.
