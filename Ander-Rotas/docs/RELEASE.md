# Ander Rotas 1.0.0 — versão de teste

APK Android nativo assinado, pacote br.com.ander.rotas, Android 8+, preparado para instalação manual no Galaxy A35. Mapa interativo, busca livre de endereços brasileiros, alternativas de transporte conforme cobertura, destinos e trajetos favoritos, ida/volta, GPS opt-in, alertas, recálculo, mapas/roteiros salvos e Stitch em pixel art.

Validação local: assembleDebug e lintDebug concluídos; 32 testes unitários aprovados, zero falhas; assinatura APK v2 e SHA-256 verificados. Os testes de percurso são simulações sobre geometrias reais. O percurso inverso simulado não determina horários de transporte: estes são consultados separadamente na API.

Limitações: GPS e bateria em viagem física e instalação no próprio Galaxy A35 ainda precisam ser conferidos. Não há rastreamento de veículos; os horários são programados ou previsões identificadas pela fonte. Pontos iniciais são aproximações das ruas porque os números de porta não foram encontrados. Bases GTFS podem estar defasadas; conexões canceladas são descartadas. Consulte os avisos das operadoras.

Instalação: baixe Ander-Rotas-v1.0.0.apk, abra no Android e permita a instalação desse arquivo quando solicitado. Autorize localização ao usar o GPS. Para atualizar, mantenha a mesma chave e pacote; não desinstale para preservar os dados.

SHA-256: 4077599fec639211afb3fcf39a9a67389b8c091f909eb410358cb2ae233f555b
