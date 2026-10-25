# Fontes, cobertura e limites

Verificado em 24/10/2026; fixture reobtida em 25/10/2026 após manutenção do ambiente. App pessoal gratuito, GPL-3.0; não é oficial dos operadores.

## Motor efetivamente implementado

API de produção: `https://api.transitous.org/api/v6/plan`. Contrato oficial https://github.com/motis-project/motis/blob/v2.10.2/openapi.yaml . Política https://transitous.org/api/ e atribuição/fontes https://transitous.org/sources/ .

Consultas `fromPlace=latitude,longitude`, `toPlace=latitude,longitude`, `time` ISO8601, `numItineraries=3`, `TRANSIT` e `WALK`, acesso a pé até 1800 **segundos**, geometria e transferências detalhadas. Resposta `itineraries[].legs[]`; o parser respeita `legGeometry.precision` (6 nas respostas reais), paradas intermediárias em ordem e extremidades. Geometria ausente fica vazia; não é substituída por reta fictícia.

`realTime=false` é programado; `realTime=true` é previsão do serviço, nunca posição GPS de veículo. Rotas canceladas são descartadas. A fonte indica as agências retornadas, sem fixar ARTESP/SPTrans para viagens arbitrárias. Rótulo de GTFS possivelmente desatualizado permanece visível e ARTESP recebe aviso de tempos interpolados.

Transitous é comunitário, sem SLA, para projetos livres não comerciais e uso leve. Exige nome, versão e contato em User-Agent, atribuição às fontes e OpenStreetMap. Código deve ser publicado sob licença livre; testes leves de protótipo antes disso são admitidos. Uso intenso de roteamento/muitos usuários exige contato prévio com os mantenedores. Antes de distribuição ampla publique os fontes; para monetização/escala hospede motor/dados próprios ou obtenha autorização. Contato do app: https://github.com/AnderHonorato . Não foi validado um endpoint OTP público de São Paulo; a implementação usa MOTIS real.

## Evidências de chamadas reais

`pesquisa/http-validacao.json` contém URLs reproduzíveis e resumos das respostas HTTP200 de ida, volta e Tucuruvi→Luz para 26/10/2026. As ruas iniciais são aproximações públicas, sem confirmação de números. Nem todo itinerário retornado pelo servidor pode ser oferecido: a consulta às 09:00UTC retornou transferências WALK marcadas `cancelled:true` nas duas primeiras alternativas. O app descarta ambas, preservando a regra de cancelamento também para caminhadas.

A fixture viável `app/src/test/resources/motis-plan-real.json` é a terceira alternativa real não cancelada: caminhadas + 566 + 049 + 426, 09:46→12:05UTC; 426 tem 38 paradas intermediárias e 1087 pontos de geometria. A primeira alternativa cancelada foi preservada em `motis-plan-cancelled-transfer-real.json` como regressão; não se removeu o cancelamento para fazer testes passarem. `pesquisa/motis-0900-raw.json` preserva a resposta integral reobtida em 25/10/2026 às 03:59UTC; SHA256 `64eb2edda47fb6ec89501b3e220298db1e3e84ec599f0de72ed8ffea83bec468`.

Ida às 12:00UTC também trouxe METRÔ L1, CPTM L11, CPTM L08 e 054; volta às 18:00UTC trouxe trilhos e 054. Consulta entre estações `br-sao-paulo_18882` Tucuruvi e `br-sao-paulo_18940` Luz retornou METRÔ L1, sentido Jabaquara, 14 minutos, sete paradas intermediárias e 137 pontos. São respostas programadas do agregado, não confirmação operacional atual das concessionárias. Não se fixou conexão Lapa/Luz/Tucuruvi no código.

## GTFS oficiais e futuro motor próprio

ARTESP: https://dadosabertos.artesp.sp.gov.br/dataset/gtfs . ZIP publicado 07/10/2026 (44,5MiB): https://dadosabertos.artesp.sp.gov.br/dataset/92e8bb31-df66-4700-ad9a-42a48f04fb7f/resource/3918f5ec-206d-4826-8a88-5d9f6f791fe2/download/artesp_gtfs.zip . Atualização mensal e Creative Commons Atribuição; Transitous declara CC-BY-4.0.

Limites oficiais: calendário só dias úteis/sábado/domingo, sem exceções de feriados; horários intermediários interpolados; algumas viagens sem stop_times; sem shape_dist_traveled. A configuração Transitous https://github.com/public-transport/transitous/blob/main/feeds/br.json apontava ao recurso ARTESP de junho, embora o oficial já tivesse outubro. Por isso não se promete sincronização ao vivo com a versão oficial mais nova.

Linha **054** oficial Cajamar (Jordanésia)↔São Paulo (Lapa), Anhanguera, terminais Rua Alto Porá/Rua Anápolis e Rua Clemente Álvares,507: https://emtu.sp.gov.br/sistemas/linha/resultado_imp.htm?numlinha=52341 . A **051** EMTU é Diadema(Vila São José)↔São Paulo(Saúde): https://www.emtu.sp.gov.br/Sistemas/linha/resultado.htm?numlinha=51&pag=buscanumero.htm . Sempre distinguir agência e linha; não atribuir 051 a Jordanésia por suposição.

SPTrans: https://www.sptrans.com.br/desenvolvedores/ conduz a cadastro/login para obter GTFS e chave Olho Vivo. Feed agregado listado no Transitous como CC-BY-4.0. Catálogo técnico https://www.transit.land/data/feeds/f-6gy-sptrans aponta URL `http://www.sptrans.com.br/umbraco/Surface/PerfilDesenvolvedor/BaixarGTFS`; app não usa HTTP. Para base própria obtenha versão atual na área oficial e verifique condições.

Olho Vivo: https://www.sptrans.com.br/desenvolvedores/api-do-olho-vivo-guia-de-referencia/documentacao-api/ . Base HTTPS `https://api.olhovivo.sptrans.com.br/v2.1`; `POST /Login/Autenticar?token=...`, consultas `/Linha/Buscar`, `/Posicao/Linha`, `/Previsao`. Exige token de aplicativo e sessão. Sem token fornecido, app não consulta Olho Vivo nem promete posição ou previsão ao vivo dos ônibus.

Motor próprio possível com GTFS atualizado SPTrans+ARTESP, extrato viário OpenStreetMap e MOTIS/OTP, validação de calendários e rotina de atualização. Não foi validado GTFS municipal separado de Cajamar; cobertura encontrada lá é a metropolitana ARTESP, sem garantia de todos os ônibus municipais. App atual usa motor online, não calcula novas rotas GTFS inteiramente offline.

## Geocodificação e precisão

Padrão `https://nominatim.openstreetmap.org/search`. Política que o responsável deve ler: https://operations.osmfoundation.org/policies/nominatim/ . Apenas busca explicitamente acionada, sem autocomplete nem consultas periódicas. Fila do processo com 1,1segundo mínimo entre requisições; limite público **1requisição/segundo agregado para o app inteiro**, somando aparelhos. Destina-se aqui ao protótipo pessoal. Distribuição ampla precisa provedor com capacidade adequada ou serviço próprio.

User-Agent identifica app/versão/contato. Cache de 32 buscas por seis horas só em memória. Configuração permite trocar domínio HTTPS de serviço compatível Nominatim sem atualizar APK. Desenvolvedor deve decidir conscientemente e assumir responsabilidade pela política; serviço público não pode ser oferecido genericamente por plataformas no-code/low-code. Atribuição: © OpenStreetMap contributors, ODbL1.0, https://www.openstreetmap.org/copyright .

| Busca | Via encontrada | Coordenadas aproximadas |
|---|---|---|
| Rua Silveira Tavares,61, São Paulo | OSM way/85901821, Parque Edu Chaves; sem house_number61 | -23.4771045,-46.5668804 |
| Rua Seis,65, Jordanésia,Cajamar | OSM way/239853680, Vila Mariotti; sem house_number65 | -23.3410193,-46.8413569 |

Os pontos são das ruas, sem confirmação de porta. Confira/ajuste no mapa antes de navegar. Não converter aproximação em confirmação de número.

## Persistência

Sem servidor próprio, analytics ou telemetria. Consultas necessárias enviam endereço/pontos ao geocodificador e Transitous. GPS de navegação fica em memória. Só salvar/iniciar rota explicitamente escolhida grava seus pontos em JSON privado `rotas_escolhidas_v1.json`, até dez rotas/3MB, arquivo temporário e renomeação. SharedPreferences privados guardam favoritos, viagens favoritas e até trinta endereços escolhidos. Sem criptografia adicional; apagar dados do app remove-os.

Sem rede, rota salva de mesma origem/destino pode ser apresentada com aviso de horários antigos e ausência de recálculo. Todas etapas são reclassificadas INDISPONIVEL, sem previsão antiga rotulada como ao vivo. Não há garantia de novos trajetos, mapa ou horários atualizados offline. Consultas não escolhidas não são gravadas em disco.
