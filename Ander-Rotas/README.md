# Ander Rotas

Aplicativo Android nativo em português para planejar viagens de transporte público, acompanhar o caminho pelo GPS e guardar destinos e trajetos favoritos.

## Usar no celular

Instale `Ander-Rotas-v1.1.0.apk` por cima da versão anterior. O app abre sem destino ou rota predefinida e solicita localização para preencher a origem, que pode ser editada. Digite o destino: sugestões aparecem a partir de três caracteres. Toque e segure no mapa para escolher coordenadas.

Adicione até oito paradas intermediárias antes de iniciar. Segure o símbolo ≡ e arraste para ordenar; o leitor de tela oferece ações para subir ou descer a parada. Toque em Buscar rotas, escolha a alternativa, confira a linha/sentido e toque em Ir. Durante uma viagem ativa não é possível trocar origem, destino ou paradas. Cancele no app ou pela notificação e confirme para encerrar o GPS e remover o status.

A viagem fica em foco, com próximo ponto destacado e pontos/etapas passados recolhidos. Confirme a etapa ao embarcar e desembarcar; o app acompanha seu GPS e não identifica automaticamente o veículo. Quando a fonte publica paradas intermediárias, o app conta os pontos restantes: três geram vibração/notificação, dois geram voz autorizada, e a aproximação final prepara o desembarque. A chegada ao ponto exige GPS preciso e leituras repetidas próximas do local. Confira se o veículo está parado antes de descer. Sem lista de paradas válida, o app mostra a distância, sem inventar contagem. Autorize voz na primeira viagem ou altere em Ajustes.

Salve uma viagem nos detalhes para manter a origem/destino nos favoritos e o roteiro no aparelho. Em Salvos, consulte a ida, a volta ou um roteiro armazenado. Em Ajustes, salve uma pequena região do mapa antes de ficar offline, altere o tema e controle os comentários do mascote.

## Dados e limites

- MapLibre e OpenFreeMap exibem cartografia real do OpenStreetMap. A região offline é limitada a 3.000 tiles/50 MB, com até quatro pedidos simultâneos.
- Transitous/MOTIS calcula viagens com as fontes disponíveis na região. As fontes incluem SPTrans e ARTESP em São Paulo; a base metropolitana agregada pode estar defasada em relação ao portal oficial. A linha 054 Jordanésia–Lapa foi encontrada; a 051 não é equivalente.
- Sugestões usam Photon, com debounce de 450 ms, cancelamento de consultas antigas, limite de frequência e cache em memória. O servidor compatível pode ser trocado em Ajustes. O endpoint público não garante disponibilidade e precisa ser substituído por uma instância própria se o uso crescer.
- Horário programado, previsão e informação indisponível possuem identificação por etapa. Uma previsão de chegada não é localização real de um ônibus. Esta versão acompanha o usuário pelo GPS, sem posição de veículos.
- Nenhum endereço é escolhido previamente. Endereços sem número confirmado são identificados nas sugestões; confira a posição no mapa.
- Offline permite reabrir roteiros escolhidos e mapas previamente armazenados. Novas buscas, novos horários e novos cálculos precisam de internet. O cache não vira uma nova previsão de transporte.
- GPS físico, condições reais de embarque, bateria durante uma viagem longa e funcionamento no Galaxy A35 precisam de validação no próprio aparelho. Consulte `docs/VALIDACAO.md` para a evidência executada no ambiente de desenvolvimento.

## Desenvolvimento

Kotlin 2.0.21, Jetpack Compose, Android SDK 35, Gradle 8.10.2, AGP 8.7.3, MapLibre 11.5.2 e OkHttp 4.12.0. Android mínimo 8/API 26. Pacote estável `br.com.ander.rotas`; versão 1.1.0, código 2.

Com JDK 17 e SDK Android instalados:

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
```

A implementação mantém dados em JSON e preferências privadas, sem backend pago, contas ou tokens secretos. Essa escolha reduz dependências nesta versão; os contratos separam persistência, rede, navegação, interface e serviço de localização.

## Assinatura e atualizações

O APK entregue é uma versão de teste assinada. O backup da chave está no destino privado do projeto e não faz parte dos fontes. Para gerar uma atualização por cima da versão instalada, preserve pacote e chave e aumente versionCode. Não desinstale a versão anterior se quiser preservar seus dados.

O build aceita `ANDER_KEYSTORE_PATH`, `ANDER_KEYSTORE_PASSWORD`, `ANDER_KEY_ALIAS` e `ANDER_KEY_PASSWORD`. Configure todos os valores juntos. O modelo de pipeline em `tools/ander-rotas.yml` usa Secrets; sem eles, os testes ainda podem rodar, mas nenhum APK de atualização é publicado. Uma chave de assinatura de distribuição diferente exige migração; não troque a chave inadvertidamente.

## Licenças e privacidade

Código sob GPL-3.0. Fonte Figtree sob OFL, incluída em assets/licenses. Mapas e dados continuam sujeitos às licenças e atribuições de seus provedores. Os desenhos do personagem são uma representação de Stitch, personagem da Disney, sem afiliação oficial; o código da animação é separado dos direitos do personagem.

Não há telemetria de GPS. O histórico e os favoritos ficam no aparelho. Origem, destino e paradas intermediárias são enviados ao serviço ao calcular viagens; pesquisas de endereços e, se disponível, uma posição de referência GPS são enviados ao geocodificador; o mapa solicita as regiões visíveis ao provedor. O acompanhamento contínuo só começa após uma ação sua e a autorização de localização. As permissões permanentes de localização em segundo plano não são solicitadas.

Fontes e termos: `docs/FONTES.md`. Arquitetura: `docs/ARQUITETURA.md`. Revisão e testes: `docs/REVISAO.md` e `docs/VALIDACAO.md`.
