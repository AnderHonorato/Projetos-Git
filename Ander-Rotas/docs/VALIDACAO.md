# Evidências de validação

O APK entregue tem 61.411.017 bytes, SHA-256 4077599fec639211afb3fcf39a9a67389b8c091f909eb410358cb2ae233f555b. Pacote br.com.ander.rotas, versão 1.0.0/código1, minSdk26, targetSdk35.

Build local Java17/SDK35/Gradle8.10.2: `assembleDebug testDebugUnitTest lintDebug` terminou com BUILD SUCCESSFUL. Nova execução de testes após adicionar simulação sobre geometria real terminou com BUILD SUCCESSFUL em17s. XMLs registram32 testes, zero falhas/erros/ignorados: Motor13, segurança7, formato10 e percurso simulado2. Logs e XMLs estão em evidencias-build.

apksigner confirmou assinatura v2. Certificado SHA-256 65a0bdbc46677d33a4dc15a664608db39e4c918698aeecd2995bb0c4b3ef7fb7. A chave está fora do repositório, com backup privado.

Lint: zero erros e três avisos: ordenação de parâmetro Modifier; política Android12 de transferência/backup que pode exigir dataExtractionRules mesmo com allowBackup=false; referência estática OfflineRegion no gerenciador de mapas. O gerenciador recebe applicationContext e libera observadores/região ao terminar o download. São riscos registrados para acompanhamento, não comprovação de ausência de vazamentos em uso longo.

Consultas HTTP reais com status200 para ida, volta e Tucuruvi–Luz estão documentadas em pesquisa/http-validacao.json. Há metadados e uma resposta original para reproduzir a regressão de transferência cancelada. A resposta cancelada foi rejeitada corretamente pelo produto; a fixture válida usa uma alternativa não cancelada.

O emulador local sem aceleração não concluiu a conferência visual antes da interrupção. Depois, o job `interface-emulada` da execução GitHub Actions 40239091894 concluiu com sucesso no emulador API35, instalando o APK original e verificando seu SHA-256. O resultado registra abriu=true, botaoRotas=true, botaoGPS=true, dialogoLocalizacao=true e excecaoFatal=false. O log AndroidRuntime está vazio.

As capturas foram abertas e inspecionadas: o mapa real carregou; uma consulta do trajeto inicial retornou três alternativas, com duração/chegada e geometria desenhada; o tema escuro funcionou; o Android exibiu o pedido de localização; o teste enviou uma posição GPS simulada e abriu Salvos. São capturas do APK executado, sem mockups.

O teste também desligou Wi-Fi e dados do emulador e preservou o mapa já carregado. Isso não comprova isolamento total de rede no ambiente emulado: a captura não apresentou o aviso offline. O download de uma região completa, navegação com a tela bloqueada, alertas/notificações em deslocamento físico, recálculo numa viagem real e bateria em uso longo não foram testados de ponta a ponta. Favoritos, cache e busca possuem testes do contrato/persistência e inspeção de implementação; a tela Salvos capturada estava vazia, sem simular favoritos que não foram criados.

Este documento não afirma teste físico no Galaxy A35, viagem real ou medição de bateria. O teste de sentido inverso usa a geometria invertida para verificar o motor; horários e conexões reais de retorno foram consultados separadamente na API.

A execução GitHub Actions 40239209759 também concluiu com sucesso as verificações, a interface emulada e a publicação da versão inicial. Release: https://github.com/AnderHonorato/Projetos-Git/releases/tag/ander-rotas-v1.0.0 . O APK publicado tem o mesmo SHA-256 do arquivo entregue no Drive.
