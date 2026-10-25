# Evidências de validação

O APK entregue tem 61.411.017 bytes, SHA-256 4077599fec639211afb3fcf39a9a67389b8c091f909eb410358cb2ae233f555b. Pacote br.com.ander.rotas, versão 1.0.0/código1, minSdk26, targetSdk35.

Build local Java17/SDK35/Gradle8.10.2: `assembleDebug testDebugUnitTest lintDebug` terminou com BUILD SUCCESSFUL. Nova execução de testes após adicionar simulação sobre geometria real terminou com BUILD SUCCESSFUL em17s. XMLs registram32 testes, zero falhas/erros/ignorados: Motor13, segurança7, formato10 e percurso simulado2. Logs e XMLs estão em evidencias-build.

apksigner confirmou assinatura v2. Certificado SHA-256 65a0bdbc46677d33a4dc15a664608db39e4c918698aeecd2995bb0c4b3ef7fb7. A chave está fora do repositório, com backup privado.

Lint: zero erros e três avisos: ordenação de parâmetro Modifier; política Android12 de transferência/backup que pode exigir dataExtractionRules mesmo com allowBackup=false; referência estática OfflineRegion no gerenciador de mapas. O gerenciador recebe applicationContext e libera observadores/região ao terminar o download. São riscos registrados para acompanhamento, não comprovação de ausência de vazamentos em uso longo.

Consultas HTTP reais com status200 para ida, volta e Tucuruvi–Luz estão documentadas em pesquisa/http-validacao.json. Há metadados e uma resposta original para reproduzir a regressão de transferência cancelada. A resposta cancelada foi rejeitada corretamente pelo produto; a fixture válida usa uma alternativa não cancelada.

O emulador local sem aceleração não concluiu a conferência visual antes da interrupção. O workflow inclui uma nova execução em emulador API35 com capturas reais e teste de inicialização, permissão, GPS simulado e ausência de rede. Somente o resultado efetivo do job autoriza afirmar que esses testes de interface passaram. Este documento não afirma teste físico no Galaxy A35, viagem real ou medição de bateria.
