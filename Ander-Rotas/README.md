# Ander Rotas

Aplicativo Android nativo em português para planejar viagens de transporte público, acompanhar o caminho pelo GPS e guardar destinos e trajetos favoritos.

## Usar no celular

Instale `Ander-Rotas-v1.0.0.apk`. Na primeira abertura, o mapa mostra a região de São Paulo e Cajamar. Toque nos campos De/Para para pesquisar outros endereços, ou toque e segure o mapa para escolher um ponto. O botão de GPS centraliza sua localização; a opção de usá-la como origem depende de sua ação.

Toque em Buscar rotas, compare as alternativas e abra os detalhes. Confira a linha e o sentido. Para acompanhar o trajeto, toque em Seguir viagem com GPS e autorize localização e notificações. Confirme cada etapa ao embarcar ou desembarcar. A navegação não identifica automaticamente o veículo. Encerre pelo app ou pela notificação para parar a localização contínua.

Salve uma viagem nos detalhes para manter a origem/destino nos favoritos e o roteiro no aparelho. Em Salvos, consulte a ida, a volta ou um roteiro armazenado. Em Ajustes, salve uma pequena região do mapa antes de ficar offline, altere o tema e controle os comentários do mascote.

## Dados e limites

- MapLibre e OpenFreeMap exibem cartografia real do OpenStreetMap. A região offline é limitada a 3.000 tiles/50 MB, com até quatro pedidos simultâneos.
- Transitous/MOTIS calcula viagens com as fontes disponíveis na região. As fontes incluem SPTrans e ARTESP em São Paulo; a base metropolitana agregada pode estar defasada em relação ao portal oficial. A linha 054 Jordanésia–Lapa foi encontrada; a 051 não é equivalente.
- A busca Nominatim ocorre apenas ao tocar no botão, sem consultas automáticas ao digitar. O servidor pode ser trocado em Ajustes. A distribuição em escala requer respeitar os limites agregados do serviço e possivelmente hospedar um geocodificador próprio.
- Horário programado, previsão e informação indisponível possuem identificação por etapa. Uma previsão de chegada não é localização real de um ônibus. Esta versão acompanha o usuário pelo GPS, sem posição de veículos.
- Os números 61 da Rua Silveira Tavares e 65 da Rua Seis não foram confirmados pelo geocodificador. Os pontos iniciais são aproximações do centro das ruas, com aviso e ajuste manual no mapa.
- Offline permite reabrir roteiros escolhidos e mapas previamente armazenados. Novas buscas, novos horários e novos cálculos precisam de internet. O cache não vira uma nova previsão de transporte.
- GPS físico, condições reais de embarque, bateria durante uma viagem longa e funcionamento no Galaxy A35 precisam de validação no próprio aparelho. Consulte `docs/VALIDACAO.md` para a evidência executada no ambiente de desenvolvimento.

## Desenvolvimento

Kotlin 2.0.21, Jetpack Compose, Android SDK 35, Gradle 8.10.2, AGP 8.7.3, MapLibre 11.5.2 e OkHttp 4.12.0. Android mínimo 8/API 26. Pacote estável `br.com.ander.rotas`; versão 1.0.0, código 1.

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

Não há telemetria de GPS. O histórico e os favoritos ficam no aparelho. A origem e o destino são enviados ao serviço ao calcular viagens; pesquisas de endereços são enviadas ao geocodificador; o mapa solicita as regiões visíveis ao provedor. O acompanhamento contínuo só começa após uma ação sua e a autorização de localização. As permissões permanentes de localização em segundo plano não são solicitadas.

Fontes e termos: `docs/FONTES.md`. Arquitetura: `docs/ARQUITETURA.md`. Revisão e testes: `docs/REVISAO.md` e `docs/VALIDACAO.md`.
