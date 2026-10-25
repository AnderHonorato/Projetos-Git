# Ander Rotas — arquitetura e requisitos

Android nativo Kotlin, Compose e MapLibre. Pacote br.com.ander.rotas, versão1.0.0/código1, Android8+, alvo35. Mapa interativo é a tela principal; pesquisar quaisquer endereços brasileiros com cobertura real, favoritos de destinos E viagens, percurso reverso, etapas e GPS autorizado. Paleta referência Portal Ferramentas do Ander: creme#f8eee3, papel#fffaf5, vinho#8f2d4a e rosa#b85c6b, tipografiaFigtree, cartões arredondados e temaescuro.

Modelos.kt contratos: Ponto,Endereco,Etapa,Rota,TipoDado,ViagemFavorita. RepositorioRotas cuida HTTP MOTIS/Transitous, Nominatim busca explícita1/s, favoritos/histórico/rotasJSON privados. Sem credenciais noAPK, sem backendpago. JSON/prefs substituem Room para reduzir dependências e manter armazenamento local com contratos migráveis.

MotorNavegacao puro calcula distância à geometria, progressão manual, ruído/desvio persistente e chegada. NavegacaoService é fonte única do progresso, fornece SessaoNavegacao.estado/ativa/erro/localizacoes. GPS e rede apenas após iniciar, notificação permanente com encerrar, Android foregroundservice location; sem permissão permanentebackground. Intervalo5s, filtro2s monotônico para não contar dois provedores como duas observações; minDistance0 permite confirmar chegada parado. VM preserva viagem ativa na recriação e não processa motor duplicado.

UIMainActivity/InterfaceRotas/MapaRotas: mapas reaisOpenFreeMap, polilinhas por etapa, GPS e orientação, geocodificação por ação explícita, longpress para corrigir ponto, origemGPS opcional, horas programadas/previstas/indisponíveis diferenciadas, recálculo, favoritos, rotasoffline, downloadsMapLibre limitados a3000tiles/50MB/4requisições e useragentúnico. Últimos mapas/rotas salvos continuam disponíveis offline sem prometer novos horários.

MascoteStitch: atlas2Dpixelart com59quadros/11ações, falas contextuais, toque, minimizar/silenciar, pausa por lifecycle e preferência animaçõesAndroid.

Validação obrigatória: buildassinado, testesunitários significativos doGPS/geometrias/parser/caches, inspeção APK manifest/assinatura/SHA256 e emulador se disponível. PesquisaHTTP e GPSsimulado nunca equivalem a teste físico na rua. Não há rastreamento de veículos identificado; previsõesGTFSnão são posições deônibus. Números61/65 ausentes noOSM: mostrar aproximações e ajuste mapa.

Distribuição: fontesGPLv3 publicadas, pipelineGitHub com chave em secrets para atualizar mesma identidade, APKteste assinado noDrive e para download. Segredos/chaves fora dorepo, backupprivado. Documentar limitações verificadas.
