# Revisão independente — Ander Rotas

Revisão estática e testes JVM do motor. Não houve teste de campo/GPS físico. O build Android e eventual emulador são validados separadamente pelo responsável pelo build.

## Evidência já executada antes da restauração do workspace

Kotlin JVM 1.9.24, JDK 17 e JUnit 4.13.2: `MotorNavegacaoTest` e os seis primeiros casos de `MotorNavegacaoSegurancaTest` passaram: **OK (19 tests)**. A restauração dos arquivos exige nova execução; não reaproveitar esse resultado como validação do APK final. O sétimo teste adicional registra a chegada dentro do cooldown do pré-desembarque.

Após a restauração, os dois arquivos de testes e os modelos/motor foram novamente compilados com Kotlin JVM 1.9.24 e executados com JUnit 4.13.2: **OK (20 tests)**, em 0,04 s, incluindo o novo caso de chegada. Esse resultado cobre o domínio JVM; não substitui o build e os testes Android.

## Correções identificadas para conferir no código final

- Cada localização precisa alimentar um único motor: remover coleta duplicada Activity/ViewModel e publicar estado calculado pelo serviço.
- Restaurar a rota e a etapa ao reabrir a interface durante uma sessão ativa; mostrar falhas do serviço na interface.
- Com localização aproximada, usar provedor de rede; não solicitar GPS sem permissão precisa.
- Registrar localização durante navegação com intervalo de 5 segundos e deslocamento mínimo de 0 m. Deslocamento mínimo de 8 m impedia três confirmações quando a pessoa parava no destino.
- Deduplicar observações de GPS/rede pelo relógio monotônico, com separação mínima de 2 segundos, e recusar leituras antigas.
- Chegada confirmada precisa notificar uma vez mesmo dentro dos 90 segundos do aviso de desembarque. Alertas de desvio continuam sujeitos a cooldown.
- Ao parar, remover listeners, encerrar foreground e limpar rota ativa; não iniciar serviço automaticamente no boot ou em ações desconhecidas.
- Não repetir manualmente `MapView.onStart/onResume` após o catch-up do observador de lifecycle.
- Redesenhar polilinhas apenas quando a rota muda; atualizar somente o marcador a cada GPS. Reutilizar ícones.
- Reenquadrar a câmera após mudar origem/destino, inclusive sem rota calculada.
- Reabrir uma rota salva com aviso explícito de horários antigos. Previsões/tempo real antigos não devem manter esses rótulos; aplicar também no fallback automático de rede.
- Fonte/agência e aviso de ARTESP precisam depender da viagem real; não rotular toda rota arbitrária como ARTESP/SPTrans.
- Downloads offline devem impedir que um callback antigo de preparação reinicie download após exclusão. Desconectar observadores nos estados finais para não voltar a indicar download ativo.

## Cobertura dos testes adicionais

1. GPS impreciso interrompe a sequência de desvio.
2. GPS impreciso interrompe a sequência de chegada.
3. Precisão zero, negativa, NaN ou infinita não produz alerta/chegada/desvio.
4. Índices manuais respeitam os limites e GPS não avança etapas sozinho.
5. Trocar etapa limpa as confirmações da anterior.
6. Rota vazia aceita os comandos sem crash ou falso alerta.
7. Chegada após pré-desembarque dentro do cooldown produz um único aviso.

## Privacidade e escopo revisados

Manifest revisado com serviço não exportado, backup desativado, HTTPS e foreground de localização com permissões declaradas. Favoritos/cache ficam na área privada; buscas não selecionadas ficam apenas em memória. Localização é solicitada por ação do usuário. A renderização do mapa consulta o provedor de mapas; origem/destino são enviados às fontes para buscar rotas. Não há posição de veículo inferida de GTFS estático.

Falta validar o build Android, o APK, a assinatura e o comportamento no dispositivo. Não alegar instalação, startup em aparelho, offline completo ou percurso físico sem evidência da execução correspondente.

## Rodada estática após restauração

Motor, ViewModel, serviço e mapa restaurados foram relidos. Os principais problemas descritos acima foram corrigidos no código: fonte única de progresso, filtro monotônico de 2 segundos, intervalo de localização de 5 segundos sem distância mínima, chegada independente do cooldown, restauração de sessão, rede validada, snapshots antigos identificados, mapa inicializado antes dos ícones, geometria separada do GPS, chaves da câmera e callbacks offline com geração/observadores desconectados. Não foi encontrado novo bloqueador por leitura estática desses arquivos. O botão de exclusão de mapas fica desabilitado durante uma operação.

O build Android continua sendo necessário para comprovar compatibilidade com as dependências e o dispositivo. Os testes JVM executados cobrem apenas o motor; não verificam permissões, renderização nativa ou downloads offline reais.
