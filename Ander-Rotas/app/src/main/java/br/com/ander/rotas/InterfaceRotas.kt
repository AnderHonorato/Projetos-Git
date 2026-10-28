package br.com.ander.rotas

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Figtree = FontFamily(Font(R.font.figtree))
private val Tipografia = Typography().let { t -> Typography(bodyLarge=t.bodyLarge.copy(fontFamily=Figtree),bodyMedium=t.bodyMedium.copy(fontFamily=Figtree),bodySmall=t.bodySmall.copy(fontFamily=Figtree),titleLarge=t.titleLarge.copy(fontFamily=Figtree),titleMedium=t.titleMedium.copy(fontFamily=Figtree),titleSmall=t.titleSmall.copy(fontFamily=Figtree),headlineSmall=t.headlineSmall.copy(fontFamily=Figtree),labelLarge=t.labelLarge.copy(fontFamily=Figtree),labelMedium=t.labelMedium.copy(fontFamily=Figtree),labelSmall=t.labelSmall.copy(fontFamily=Figtree)) }
private val Vinho=Color(0xFF8F2D4A); private val Verde=Color(0xFF2C7369); private val Creme=Color(0xFFF8EEE3); private val Papel=Color(0xFFFFFAF5); private val Texto=Color(0xFF2F2522); private val Muted=Color(0xFF7C6961); private val Borda=Color(0xFFDFCEC5)
private fun hora(epoch:Long)=SimpleDateFormat("HH:mm",Locale("pt","BR")).format(Date(epoch))
private fun dataHora(epoch:Long)=SimpleDateFormat("dd/MM · HH:mm",Locale("pt","BR")).format(Date(epoch))
private fun ruaAproximada(endereco: Endereco) = endereco.detalhe.contains("aproximado da rua",true) || endereco.detalhe.contains("número não localizado",true)
fun nomeModo(modo:String)=when(modo.uppercase()){ "WALK","FOOT","WALKING"->"A pé";"SUBWAY","METRO"->"Metrô";"RAIL","TRAIN"->"Trem";"TRAM"->"VLT";"FERRY"->"Balsa";else->"Ônibus" }

@Composable fun InterfaceRotas(vm:RotasViewModel,onGps:()->Unit,onIniciar:()->Unit,onParar:()->Unit) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val mapaOffline by MapasOffline.estado.collectAsStateWithLifecycle()
    val leituraGps by SessaoNavegacao.localizacoes.collectAsState(initial=null)
    var geocodificador by rememberSaveable { mutableStateOf(vm.obterGeocodificador()) }
    LaunchedEffect(Unit){ MapasOffline.atualizar(context) }
    val cores=if(estado.temaEscuro) darkColorScheme(primary=Color(0xFFF0ADB9),onPrimary=Color(0xFF4E162B),background=Color(0xFF241D1C),surface=Color(0xFF302625),onSurface=Creme,surfaceVariant=Color(0xFF443532),onSurfaceVariant=Color(0xFFDDC6BE),outline=Color(0xFF705852)) else lightColorScheme(primary=Vinho,onPrimary=Color.White,background=Creme,surface=Papel,onSurface=Texto,surfaceVariant=Color(0xFFF0E2D8),onSurfaceVariant=Muted,outline=Borda)
    MaterialTheme(colorScheme=cores,typography=Tipografia) {
        var pagina by rememberSaveable { mutableIntStateOf(0) }; var buscaOrigem by rememberSaveable { mutableStateOf<Boolean?>(null) }; var detalhes by rememberSaveable { mutableStateOf(false) }
        var planejadorExpandido by rememberSaveable { mutableStateOf(true) }
        LaunchedEffect(estado.rotas) { if(estado.rotas.isNotEmpty()) planejadorExpandido=false }
        LaunchedEffect(estado.navegando){if(estado.navegando){pagina=0;buscaOrigem=null;detalhes=false;planejadorExpandido=false}}
        var vozPermitida by remember{mutableStateOf(context.getSharedPreferences("aparencia",0).getBoolean("voz_permitida",false))}
        LaunchedEffect(pagina){vozPermitida=context.getSharedPreferences("aparencia",0).getBoolean("voz_permitida",false)}
        var focoGps by remember { mutableIntStateOf(0) }; var erroMapa by remember { mutableStateOf<String?>(null) }; var reiniciarMapa by remember { mutableIntStateOf(0) }; var pontoMarcado by remember { mutableStateOf<Ponto?>(null) }
        var aviso by remember { mutableStateOf<String?>(null) }; var mascoteMini by rememberSaveable { mutableStateOf(false) }; var mascoteMudo by rememberSaveable { mutableStateOf(false) }; var interagiuMascote by rememberSaveable { mutableStateOf(false) }
        var saltoEtapa by remember { mutableStateOf(false) }; var aberturaMascote by remember { mutableStateOf(true) }; var fraseIdle by remember { mutableIntStateOf(0) }
        val frases=listOf("Seu próximo caminho começa aqui.","Confira o sentido antes de embarcar.","Um passo de cada vez. Vamos juntos!","Guarde seus destinos em Salvos.")
        LaunchedEffect(Unit){ delay(5000);aberturaMascote=false;if(!interagiuMascote) mascoteMini=true }
        LaunchedEffect(estado.navegacao?.etapaAtual){ if((estado.navegacao?.etapaAtual?:0)>0){ saltoEtapa=true;delay(1400);saltoEtapa=false } }
        LaunchedEffect(Unit){ while(true){delay(45000);fraseIdle=(fraseIdle+1)%frases.size} }
        val etapaAtual=estado.rotaSelecionada?.etapas?.getOrNull(estado.navegacao?.etapaAtual?:0)
        val esperaMinutos=etapaAtual?.let{(it.inicio-System.currentTimeMillis())/60000}?:0
        val acaoMascote=when {
            estado.navegacao?.chegou==true->"celebrar";estado.navegacao?.desvio==true->"duvida";saltoEtapa->"pular";estado.calculando->"correr";estado.buscando->"duvida"
            estado.navegando && etapaAtual?.let{nomeModo(it.modo)=="A pé"}==true->if((leituraGps?.velocidade?:0f)>2.5f)"correr" else "andar"
            estado.navegando && esperaMinutos>15->"dormir";estado.navegando && esperaMinutos>2->"sentar";estado.navegando->"apontar";aberturaMascote->"acenar";else->"idle"
        }
        SideEffect{(context as? android.app.Activity)?.window?.let{window->WindowCompat.getInsetsController(window,window.decorView).apply{isAppearanceLightStatusBars=!estado.temaEscuro;isAppearanceLightNavigationBars=!estado.temaEscuro}}}
        Scaffold(containerColor=MaterialTheme.colorScheme.background,contentWindowInsets=WindowInsets(0,0,0,0),bottomBar={
            NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp){listOf("Rotas" to "rota","Salvos" to "salvo","Ajustes" to "ajustes").forEachIndexed{index,item->NavigationBarItem(selected=pagina==index,onClick={pagina=index},icon={Simbolo(item.second,Modifier.size(23.dp),if(pagina==index)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)},label={Text(item.first,fontSize=12.sp,fontWeight=if(pagina==index)FontWeight.Bold else FontWeight.Medium)},colors=NavigationBarItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primary.copy(alpha=.10f)))}}
        }){padding->
            Box(Modifier.fillMaxSize().padding(padding)){
                if(pagina==0){
                    Column(Modifier.fillMaxSize()){
                        Cabecalho(estado.navegando,estado.temaEscuro,vm::mudarTema)
                        Box(Modifier.weight(1f).fillMaxWidth()){
                            key(reiniciarMapa){MapaRotas(estado.origem,estado.destino,estado.rotaSelecionada,estado.posicao,focoGps,estado.navegando,rumo=leituraGps?.rumo,navegacao=estado.navegacao,modifier=Modifier.fillMaxSize(),onPonto={if(!estado.navegando)pontoMarcado=it},onEstado={erroMapa=it})}
                            Row(Modifier.align(Alignment.TopStart).padding(16.dp)){Etiqueta("MAPA INTERATIVO",Verde)}
                            Column(Modifier.align(Alignment.CenterEnd).padding(end=16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                                FilledIconButton(onClick={onGps();focoGps++},modifier=Modifier.size(48.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.primary)){Simbolo("gps",Modifier.size(23.dp))}
                                FilledIconButton(onClick={detalhes=true},modifier=Modifier.size(48.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=MaterialTheme.colorScheme.surface,contentColor=MaterialTheme.colorScheme.primary)){Simbolo("etapas",Modifier.size(23.dp))}
                            }
                            Text("© OpenStreetMap · OpenMapTiles · OpenFreeMap",fontSize=9.sp,color=Texto,modifier=Modifier.align(Alignment.BottomStart).padding(start=9.dp,bottom=5.dp).background(Papel.copy(alpha=.94f),RoundedCornerShape(4.dp)).clickable{context.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.openstreetmap.org/copyright")))}.padding(5.dp))
                            Box(Modifier.align(Alignment.BottomEnd).padding(end=11.dp,bottom=30.dp)){MascoteStitch(acaoMascote,if(estado.navegacao?.chegou==true)"Chegamos! Que bom ir com você." else if(estado.navegando)estado.navegacao?.mensagem?:"Vamos juntos!" else if(estado.calculando)"Procurando o melhor caminho…" else frases[fraseIdle],mascoteMini,mascoteMudo,{interagiuMascote=true;mascoteMini=!mascoteMini},{mascoteMudo=!mascoteMudo})}
                            if(erroMapa!=null) Surface(Modifier.align(Alignment.TopCenter).padding(top=52.dp,start=16.dp,end=16.dp),color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(16.dp)){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text("Não foi possível carregar o mapa. Seus trajetos salvos continuam disponíveis.",Modifier.weight(1f),fontSize=12.sp);TextButton(onClick={erroMapa=null;reiniciarMapa++}){Text("Tentar")}}}
                        }
                        Surface(shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp),color=MaterialTheme.colorScheme.surface,shadowElevation=12.dp){
                            Column(Modifier.fillMaxWidth().animateContentSize().padding(horizontal=20.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                                Box(Modifier.align(Alignment.CenterHorizontally).width(64.dp).height(16.dp).clickable{planejadorExpandido=!planejadorExpandido}.semantics{contentDescription="Expandir ou recolher planejador"},contentAlignment=Alignment.Center){Box(Modifier.width(34.dp).height(4.dp).background(MaterialTheme.colorScheme.outline,CircleShape))}
                                if((estado.navegando||estado.navegacao?.chegou==true)&&estado.rotaSelecionada!=null){PainelNavegacao(estado,vm::avancarEtapa,vm::voltarEtapa,{detalhes=true},onParar,vm::recalcular)}else if(!planejadorExpandido){
                                    Row(verticalAlignment=Alignment.CenterVertically){
                                        Column(Modifier.weight(1f)){Text(estado.rotaSelecionada?.let{"${it.duracaoMinutos} min · chegada ${hora(it.fim)}"}?:"Seu próximo destino",fontWeight=FontWeight.Bold,fontSize=21.sp);Text(estado.destino.nome,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)}
                                        IconButton(onClick={planejadorExpandido=true}){Simbolo("busca",Modifier.size(21.dp),descricao="Editar origem e destino")}
                                        TextButton(onClick={if(estado.rotaSelecionada!=null)detalhes=true else vm.calcular()},enabled=!estado.calculando){Text(if(estado.rotaSelecionada!=null)"Detalhes" else "Buscar")}
                                    }
                                    if(estado.rotaSelecionada!=null)Button(onClick={onIniciar();focoGps++},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Text("Ir")}
                                    if(estado.rotas.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){estado.rotas.forEach{rota->FilterChip(selected=rota.id==estado.rotaSelecionada?.id,onClick={vm.selecionarRota(rota);detalhes=true},label={Text("${rota.duracaoMinutos} min · ${hora(rota.inicio)}")})}}
                                    if(!estado.conectado)Text("Sem internet · horários salvos sem atualização.",fontSize=11.sp,color=MaterialTheme.colorScheme.error)
                                }else{
                                    Row(verticalAlignment=Alignment.CenterVertically){Text("Qual é o próximo destino?",Modifier.weight(1f),fontSize=21.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.5).sp);IconButton(onClick=vm::inverter){Simbolo("inverter",Modifier.size(23.dp))}}
                                    Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){CampoEndereco("De",estado.origem.nome.ifBlank{"Sua localização · toque para editar"},"origem"){vm.prepararBusca(true);buscaOrigem=true}};IconButton(onClick={vm.usarLocalizacaoComoOrigem();onGps()}){Simbolo("gps",Modifier.size(21.dp),descricao="Usar minha localização como origem")}}
                                    CampoEndereco("Para",estado.destino.nome.ifBlank{"Digite seu destino"},"destino"){vm.prepararBusca(false);buscaOrigem=false}
                                    ParadasPlanejadas(estado.paradasDesejadas,vm::moverParada,vm::removerParada)
                                    TextButton(onClick={vm.prepararBusca(false,true);buscaOrigem=false},enabled=estado.paradasDesejadas.size<8,contentPadding=PaddingValues(0.dp),modifier=Modifier.height(30.dp)){Text("+ Adicionar parada",fontSize=12.sp)}
                                    if(ruaAproximada(estado.origem)||ruaAproximada(estado.destino))Text("Ponto aproximado da rua · confirme a posição no mapa",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=10.sp)
                                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                                        TextButton(onClick={val calendario=Calendar.getInstance().apply{timeInMillis=estado.quando};DatePickerDialog(context,{_,ano,mes,dia->calendario.set(ano,mes,dia);TimePickerDialog(context,{_,h,m->calendario.set(Calendar.HOUR_OF_DAY,h);calendario.set(Calendar.MINUTE,m);vm.definirQuando(calendario.timeInMillis)},calendario.get(Calendar.HOUR_OF_DAY),calendario.get(Calendar.MINUTE),true).show()},calendario.get(Calendar.YEAR),calendario.get(Calendar.MONTH),calendario.get(Calendar.DAY_OF_MONTH)).show()},contentPadding=PaddingValues(0.dp)){Simbolo("relogio",Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text(if(estado.horarioFuturo)"Sair ${dataHora(estado.quando)}" else "Sair agora",fontSize=12.sp)}
                                        Button(onClick=vm::calcular,enabled=!estado.calculando && MotorNavegacao.valido(estado.origem.ponto) && MotorNavegacao.valido(estado.destino.ponto),shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(horizontal=20.dp,vertical=11.dp)){if(estado.calculando)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp,color=MaterialTheme.colorScheme.onPrimary)else{Text("Buscar rotas",fontWeight=FontWeight.Bold);Spacer(Modifier.width(8.dp));Simbolo("seta",Modifier.size(17.dp),MaterialTheme.colorScheme.onPrimary)}}
                                    }
                                    if(estado.horarioFuturo)TextButton(onClick=vm::sairAgora,contentPadding=PaddingValues(0.dp),modifier=Modifier.height(27.dp)){Text("Voltar para sair agora",fontSize=11.sp)}
                                    if(!estado.conectado)Text("Sem internet · use mapas e viagens salvas. Horários sem atualização.",color=MaterialTheme.colorScheme.error,fontSize=11.sp)
                                    if(estado.rotas.isNotEmpty())Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){estado.rotas.forEach{rota->FilterChip(selected=rota.id==estado.rotaSelecionada?.id,onClick={vm.selecionarRota(rota);detalhes=true},label={Text("${rota.duracaoMinutos} min · ${hora(rota.inicio)}",fontWeight=FontWeight.SemiBold)},leadingIcon={Simbolo("rota",Modifier.size(15.dp))})}}else Text("Endereços livres · ônibus, metrô e caminhada",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp)
                                }
                            }
                        }
                    }
                }else if(pagina==1){
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                        TituloPagina("Seus caminhos","SALVOS","Guarde lugares e leve suas viagens com você.")
                        Secao("Destinos favoritos")
                        if(estado.favoritos.isEmpty())Vazio("Seus lugares preferidos vão morar aqui.","Toque na estrela de um endereço encontrado.")
                        estado.favoritos.forEach{endereco->CartaoLinha(endereco.nome,endereco.detalhe,"salvo"){vm.selecionarEndereco(endereco,false);pagina=0}}
                        Secao("Viagens favoritas")
                        estado.viagensFavoritas.forEach{viagem->Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface){Column(Modifier.padding(16.dp)){Text(viagem.nome,fontWeight=FontWeight.Bold);Text("${viagem.origem.nome} → ${viagem.destino.nome}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Row{TextButton(onClick={vm.abrirViagem(viagem);pagina=0}){Text("Ida")};TextButton(onClick={vm.abrirViagem(viagem,true);pagina=0}){Text("Volta")}}}}}
                        if(estado.viagensFavoritas.isEmpty())Vazio("Ida e volta, sempre à mão.","Salve uma viagem nos detalhes do trajeto.")
                        Secao("Viagens neste aparelho")
                        estado.salvas.forEach{rota->CartaoLinha(rota.destino.nome,"${rota.duracaoMinutos} min · consultada ${dataHora(rota.consultadaEm)}","relogio"){vm.abrirRotaSalva(rota);pagina=0;detalhes=true}}
                        if(estado.salvas.isEmpty())Vazio("Ainda não há viagens salvas.","Rotas escolhidas para salvar ficam disponíveis para reabrir sem internet. Horários salvos podem estar desatualizados.")
                        Secao("Buscas recentes");estado.historico.take(10).forEach{endereco->CartaoLinha(endereco.nome,endereco.detalhe,"relogio"){vm.selecionarEndereco(endereco,false);pagina=0}}
                        Spacer(Modifier.height(18.dp))
                    }
                }else{
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal=22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                        TituloPagina("Do seu jeito","AJUSTES","Um companheiro simples para chegar bem.")
                        Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                            Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Aparência escura",fontWeight=FontWeight.SemiBold);Text("Conforto para viagens à noite",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(checked=estado.temaEscuro,onCheckedChange={vm.mudarTema()})}
                            HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.5f))
                            Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Companheiro silencioso",fontWeight=FontWeight.SemiBold);Text("Oculta as falas do mascote",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(checked=mascoteMudo,onCheckedChange={mascoteMudo=it})}
                        }}
                        Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Avisos por voz",fontWeight=FontWeight.SemiBold);Text("Dois pontos antes e ao desembarcar",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(checked=vozPermitida,onCheckedChange={vozPermitida=it;context.getSharedPreferences("aparencia",0).edit().putBoolean("voz_permitida",it).apply()})}
                        Secao("Mapas sem internet")
                        Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Simbolo("mapa",Modifier.size(29.dp));Text("Um pedaço da cidade com você",fontWeight=FontWeight.Bold,fontSize=18.sp);Text("Salve a região de 8 × 8 km em torno do destino atual. Mantém uma região por vez. O download acontece somente ao tocar no botão, até 50 MB. Busca e novos horários precisam de internet.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(mapaOffline.mensagem,fontSize=12.sp,fontWeight=FontWeight.Medium);Button(onClick={MapasOffline.salvar(context,estado.destino)},enabled=!mapaOffline.ocupado && MotorNavegacao.valido(estado.destino.ponto),shape=RoundedCornerShape(14.dp)){Text(if(mapaOffline.ocupado)"Salvando…" else "Salvar região do destino")};TextButton(onClick={MapasOffline.excluir(context)},enabled=!mapaOffline.ocupado){Text("Excluir mapas salvos")}}}
                        Secao("Busca de endereços")
                        OutlinedTextField(value=geocodificador,onValueChange={geocodificador=it},modifier=Modifier.fillMaxWidth(),label={Text("Servidor de sugestões compatível com Photon")},supportingText={Text("URL HTTPS da API de sugestões. Resultados aparecem ao digitar.")},shape=RoundedCornerShape(16.dp),singleLine=true)
                        OutlinedButton(onClick={runCatching{vm.configurarGeocodificador(geocodificador.trim())}.onSuccess{aviso="Servidor de busca atualizado."}.onFailure{aviso=it.message?:"Informe uma URL HTTPS válida."}}){Text("Salvar servidor de busca")}
                        Secao("Fontes e transparência")
                        Text("Rotas: Transitous. Endereços: OpenStreetMap / Photon. Mapa: OpenFreeMap, OpenMapTiles e colaboradores do OpenStreetMap. A cobertura depende das fontes de cada região.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Horário programado é a tabela publicada. Previsão e tempo real são identificados apenas quando fornecidos pela fonte. Sempre confira o letreiro, o sentido e os avisos da operadora.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Sua localização preenche a origem ao abrir. O acompanhamento contínuo começa ao tocar em Ir. Origem, destino e paradas são enviados à fonte de rotas; o GPS também pode orientar as sugestões. Cancele pelo app ou pela notificação, com confirmação.",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Ander Rotas · 1.1.0 · versão de teste",fontSize=12.sp,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(bottom=22.dp))
                    }
                }
                estado.erro?.let{erro->Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(16.dp),shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.errorContainer,shadowElevation=6.dp){Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text(erro,Modifier.weight(1f),fontSize=13.sp,color=MaterialTheme.colorScheme.onErrorContainer);IconButton(onClick=vm::limparErro){Simbolo("fechar",Modifier.size(18.dp),MaterialTheme.colorScheme.onErrorContainer)}}}}
            }
        }
        if(buscaOrigem!=null&&!estado.navegando){val origem=buscaOrigem==true;BuscaEndereco(estado,origem,{vm.editarTexto(it,origem)},{vm.buscar(estado.textoBusca,origem)},{vm.selecionarEndereco(it,origem);buscaOrigem=null},vm::salvarFavorito,{vm.fecharBusca();buscaOrigem=null})}
        if(detalhes)DetalhesViagem(estado,{detalhes=false},{detalhes=false;onIniciar();focoGps++},onParar,{vm.salvarViagem("${estado.origem.nome} → ${estado.destino.nome}");aviso="Viagem salva. Ida e volta disponíveis em Salvos."},{rota->val texto=buildString{append("Ander Rotas\n${rota.origem.nome} → ${rota.destino.nome}\n${hora(rota.inicio)}–${hora(rota.fim)} · ${rota.duracaoMinutos} min\n");rota.etapas.forEach{append("${nomeModo(it.modo)} ${it.linha} · ${it.origem} → ${it.destino}\n")};append("${rota.fonte}\nHorários consultados em ${dataHora(rota.consultadaEm)}. ${rota.aviso}")};context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,texto)},"Compartilhar viagem"))})
        if(estado.confirmarCancelamento)AlertDialog(onDismissRequest=vm::manterViagem,title={Text("Cancelar esta viagem?")},text={Text("O acompanhamento pelo GPS e a notificação de status serão encerrados.")},confirmButton={TextButton(onClick=vm::parar){Text("Cancelar viagem")}},dismissButton={TextButton(onClick=vm::manterViagem){Text("Continuar viagem")}})
        pontoMarcado?.takeIf{!estado.navegando}?.let{ponto->AlertDialog(onDismissRequest={pontoMarcado=null},title={Text("Usar este ponto")},text={Text("Ponto escolhido no mapa: %.5f, %.5f. Confira a rua e o lado da via antes de sair.".format(ponto.latitude,ponto.longitude))},confirmButton={TextButton(onClick={vm.selecionarEndereco(Endereco("Ponto escolhido no mapa",ponto,"Coordenada selecionada manualmente"),false);pontoMarcado=null}){Text("Como destino")}},dismissButton={TextButton(onClick={vm.selecionarEndereco(Endereco("Ponto escolhido no mapa",ponto,"Coordenada selecionada manualmente"),true);pontoMarcado=null}){Text("Como origem")}})}
        aviso?.let{mensagem->AlertDialog(onDismissRequest={aviso=null},title={Text("Ander Rotas")},text={Text(mensagem)},confirmButton={TextButton(onClick={aviso=null}){Text("Entendi")}})}
    }
}

@Composable private fun Cabecalho(navegando:Boolean,escuro:Boolean,onTema:()->Unit){Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(horizontal=22.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary),contentAlignment=Alignment.Center){Simbolo("rota",Modifier.size(25.dp),MaterialTheme.colorScheme.onPrimary)};Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("ANDER",fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=3.sp,color=MaterialTheme.colorScheme.primary);Text(if(navegando)"Vamos juntos." else "Seu caminho, mais leve.",fontSize=19.sp,fontWeight=FontWeight.Bold,letterSpacing=(-.5).sp)};IconButton(onClick=onTema){Simbolo(if(escuro)"sol" else "lua",Modifier.size(22.dp))}}}
@Composable private fun CampoEndereco(label:String,nome:String,icone:String,onClick:()->Unit){Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.background).clickable(onClick=onClick).padding(horizontal=13.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically){Simbolo(icone,Modifier.size(19.dp),if(icone=="origem")MaterialTheme.colorScheme.primary else Verde);Spacer(Modifier.width(11.dp));Text(label,Modifier.width(37.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(nome,Modifier.weight(1f),fontSize=13.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis);Simbolo("busca",Modifier.size(16.dp),MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun Etiqueta(texto:String,cor:Color=Vinho){Text(texto,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=.7.sp,color=cor,modifier=Modifier.background(Papel.copy(alpha=.97f),RoundedCornerShape(8.dp)).padding(horizontal=9.dp,vertical=6.dp))}
@Composable private fun TituloPagina(titulo:String,kicker:String,subtitulo:String){Column(Modifier.padding(top=24.dp,bottom=8.dp)){Text(kicker,color=MaterialTheme.colorScheme.primary,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp);Spacer(Modifier.height(8.dp));Text(titulo,fontSize=31.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp);Text(subtitulo,fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=7.dp))}}
@Composable private fun Secao(texto:String){Text(texto,fontSize=17.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=9.dp))}
@Composable private fun Vazio(titulo:String,detalhe:String){Column(Modifier.fillMaxWidth().padding(vertical=4.dp)){Text(titulo,fontSize=14.sp,fontWeight=FontWeight.Medium);Text(detalhe,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp))}}
@Composable private fun CartaoLinha(titulo:String,detalhe:String,icone:String,onClick:()->Unit){Surface(Modifier.fillMaxWidth().clickable(onClick=onClick),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Simbolo(icone,Modifier.size(23.dp));Spacer(Modifier.width(13.dp));Column(Modifier.weight(1f)){Text(titulo,fontSize=14.sp,fontWeight=FontWeight.SemiBold);if(detalhe.isNotBlank())Text(detalhe,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)};Simbolo("seta",Modifier.size(17.dp))}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun BuscaEndereco(estado:EstadoRotas,origem:Boolean,editar:(String)->Unit,buscar:()->Unit,selecionar:(Endereco)->Unit,favoritar:(Endereco)->Unit,fechar:()->Unit){
    ModalBottomSheet(onDismissRequest=fechar,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=MaterialTheme.colorScheme.surface){
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
            Text(if(estado.buscaParaParada)"Onde deseja parar?" else if(origem)"De onde vamos sair?" else "Para onde vamos?",fontSize=25.sp,fontWeight=FontWeight.Bold)
            OutlinedTextField(value=estado.textoBusca,onValueChange=editar,label={Text("Rua, número, bairro e cidade")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={buscar()}))
            if(estado.buscando)LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Sugestões a partir de três caracteres. Confira a cidade e a posição no mapa.",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp)
            estado.resultadosBusca.forEach{endereco->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f).clickable{selecionar(endereco)}.padding(vertical=10.dp)){Text(endereco.nome,fontWeight=FontWeight.SemiBold,fontSize=14.sp);Text(endereco.detalhe,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp)};IconButton(onClick={favoritar(endereco)}){Simbolo("salvo",Modifier.size(23.dp))}};HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.5f))}
            if(estado.resultadosBusca.isEmpty()&&!estado.buscando){Secao("Seus favoritos");estado.favoritos.take(5).forEach{endereco->CartaoLinha(endereco.nome,endereco.detalhe,"salvo"){selecionar(endereco)}};Text("Dica: toque e segure o mapa para escolher um ponto exato.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
    }
}
@Composable private fun PainelNavegacao(estado:EstadoRotas,proxima:()->Unit,anterior:()->Unit,detalhes:()->Unit,parar:()->Unit,recalcular:()->Unit){
    val nav=estado.navegacao;val etapa=estado.rotaSelecionada?.etapas?.getOrNull(nav?.etapaAtual?:0)
    Row(verticalAlignment=Alignment.CenterVertically){Text(if(nav?.chegou==true)"Você chegou!" else "Seu próximo passo",Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=22.sp);TextButton(onClick=parar){Text(if(nav?.chegou==true)"Concluir" else "Cancelar")}}
    Text(nav?.mensagem?:"Aguardando sua localização…",fontSize=14.sp,fontWeight=FontWeight.Medium)
    etapa?.let{Text("${nomeModo(it.modo)} ${it.linha} · ${it.sentido.ifBlank{it.destino}}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    nav?.pontosRestantes?.let{Text(if(it>0)"$it ponto(s) até o desembarque" else "Ponto de desembarque alcançado",fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary,fontSize=15.sp)}
    if(etapa!=null && nav!=null && etapa.paradas.size>=3 && nav.pontosRestantes!=null){
        if(nav.paradaAtual>0)Text("✓ ${nav.paradaAtual} ponto(s) passados · recolhidos",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        etapa.paradas.drop(nav.paradaAtual+1).take(2).forEachIndexed{indice,parada->Text("${if(indice==0)"● Próximo" else "│ Depois"}: ${parada.nome}",fontSize=12.sp,color=if(indice==0)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=if(indice==0)FontWeight.Bold else FontWeight.Normal)}
    }else if(etapa!=null && nomeModo(etapa.modo)!="A pé")Text("Sem contagem de pontos disponível · acompanhe a distância e o mapa.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    estado.precisaoMetros?.let{Text("Precisão GPS: ±${it.toInt()} m${if(it>100)" · localização aproximada" else ""}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    if(nav?.desvio==true)Text("Você pode estar fora do trajeto. Confira o mapa e seu sentido.",color=MaterialTheme.colorScheme.error,fontSize=12.sp)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=anterior){Text("Anterior")};Button(onClick=proxima,modifier=Modifier.weight(1f),shape=RoundedCornerShape(14.dp)){Text("Próxima etapa")};IconButton(onClick=detalhes){Simbolo("etapas",Modifier.size(22.dp))}}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Confirme a etapa ao embarcar e desembarcar.",Modifier.weight(1f),fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    if(!estado.conectado)Text("Sem internet · seguindo viagem salva; horários sem atualização.",fontSize=11.sp,color=MaterialTheme.colorScheme.error)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DetalhesViagem(estado:EstadoRotas,fechar:()->Unit,iniciar:()->Unit,parar:()->Unit,salvar:()->Unit,compartilhar:(Rota)->Unit){
    ModalBottomSheet(onDismissRequest=fechar,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=MaterialTheme.colorScheme.surface){
        val rota=estado.rotaSelecionada
        if(rota==null)Column(Modifier.padding(24.dp)){Text("Seu trajeto aparece aqui",fontWeight=FontWeight.Bold,fontSize=24.sp);Text("Escolha origem e destino e toque em Buscar rotas. Toque e segure no mapa para selecionar uma coordenada.",modifier=Modifier.padding(vertical=16.dp),fontSize=14.sp)}else{
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(bottom=26.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Seu caminho",fontWeight=FontWeight.Bold,fontSize=25.sp);Text("${hora(rota.inicio)} — ${hora(rota.fim)} · ${rota.duracaoMinutos} min",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton(onClick=salvar){Simbolo("salvo",Modifier.size(24.dp))};IconButton(onClick={compartilhar(rota)}){Simbolo("compartilhar",Modifier.size(24.dp))}}
                Text("${rota.origem.nome} → ${rota.destino.nome}",fontSize=13.sp,fontWeight=FontWeight.Medium)
                if(ruaAproximada(rota.origem)||ruaAproximada(rota.destino))Text("Número não localizado · ponto aproximado da rua. Confira no mapa; toque e segure para ajustar.",color=MaterialTheme.colorScheme.error,fontSize=12.sp)
                Surface(shape=RoundedCornerShape(13.dp),color=MaterialTheme.colorScheme.background){Text(rota.aviso,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(13.dp))}
                var mostrarPassadas by remember{mutableStateOf(false)}
                val indiceAtual=estado.navegacao?.etapaAtual?:0
                if(estado.navegando && indiceAtual>0)TextButton(onClick={mostrarPassadas=!mostrarPassadas}){Text(if(mostrarPassadas)"Recolher etapas concluídas" else "✓ $indiceAtual etapa(s) concluída(s) · mostrar")}
                rota.etapas.forEachIndexed{index,etapa->if(estado.navegando && index<indiceAtual && !mostrarPassadas)return@forEachIndexed
                    val ativa=estado.navegando && index==indiceAtual
                    val cor=Color(corModoAndroid(etapa.modo));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(13.dp)){
                    Column(horizontalAlignment=Alignment.CenterHorizontally){Box(Modifier.size(36.dp).background(cor,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Simbolo(if(nomeModo(etapa.modo)=="A pé")"andar" else "onibus",Modifier.size(20.dp),Color.White)};if(index<rota.etapas.lastIndex)Box(Modifier.width(2.dp).height(68.dp).background(cor.copy(alpha=.25f)))}
                    Column(Modifier.weight(1f).background(if(ativa)MaterialTheme.colorScheme.primary.copy(alpha=.08f)else Color.Transparent,RoundedCornerShape(12.dp)).padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Row{Text("${nomeModo(etapa.modo)} ${etapa.linha}",Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.Bold);Text(hora(etapa.inicio),fontSize=13.sp,fontWeight=FontWeight.SemiBold)}
                        if(ativa)Text("AGORA · ETAPA EM FOCO",fontSize=10.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                        if(etapa.sentido.isNotBlank())Text("Sentido ${etapa.sentido}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${etapa.origem} → ${etapa.destino}",fontSize=12.sp);Text("${((etapa.fim-etapa.inicio)/60000).coerceAtLeast(0)} min${if(etapa.distanciaMetros>0)" · ${etapa.distanciaMetros.toInt()} m" else ""}",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=11.sp)
                        Text(when(etapa.tipoDado){TipoDado.PROGRAMADO->"HORÁRIO PROGRAMADO";TipoDado.PREVISAO->"PREVISÃO DA FONTE";TipoDado.TEMPO_REAL->"TEMPO REAL";TipoDado.INDISPONIVEL->"HORÁRIO INDISPONÍVEL"},fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=.4.sp,color=cor)
                        if(etapa.paradas.isNotEmpty())LinhaParadas(etapa,if(ativa)estado.navegacao else null,ativa)

                    }
                }}
                Text("${rota.fonte} · consulta ${dataHora(rota.consultadaEm)}",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick=if(estado.navegando)parar else iniciar,modifier=Modifier.fillMaxWidth().height(52.dp),shape=RoundedCornerShape(16.dp)){Simbolo("gps",Modifier.size(20.dp),MaterialTheme.colorScheme.onPrimary);Spacer(Modifier.width(10.dp));Text(if(estado.navegando)"Cancelar viagem" else "Ir",fontWeight=FontWeight.Bold)}
            }
        }
    }
}

@Composable private fun ParadasPlanejadas(paradas:List<Endereco>,mover:(Int,Int)->Unit,remover:(Int)->Unit){
    if(paradas.isEmpty())return
    val altura=with(LocalDensity.current){60.dp.toPx()}
    var arrastando by remember{mutableStateOf<Int?>(null)}
    var deslocamento by remember{mutableFloatStateOf(0f)}
    val moverAtual by rememberUpdatedState(mover)
    val listaAtual by rememberUpdatedState(paradas)
    Text("Paradas · segure ≡ e arraste para ordenar",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.heightIn(max=180.dp).verticalScroll(rememberScrollState())){
        paradas.forEachIndexed{indice,endereco->
            Row(Modifier.fillMaxWidth().height(60.dp).background(if(arrastando==indice)MaterialTheme.colorScheme.primary.copy(alpha=.12f)else Color.Transparent,RoundedCornerShape(12.dp)).semantics{
                customActions=listOf(CustomAccessibilityAction("Mover para cima"){if(indice>0){mover(indice,indice-1);true}else false},CustomAccessibilityAction("Mover para baixo"){if(indice<paradas.lastIndex){mover(indice,indice+1);true}else false})
            },verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(44.dp).pointerInput(Unit){detectDragGesturesAfterLongPress(
                    onDragStart={arrastando=indice;deslocamento=0f},onDragEnd={arrastando=null},onDragCancel={arrastando=null},
                    onDrag={change,amount->change.consume();deslocamento+=amount.y
                        val atual=arrastando
                        if(atual!=null && kotlin.math.abs(deslocamento)>=altura*.65f){val novo=(atual+if(deslocamento>0)1 else -1).coerceIn(0,listaAtual.lastIndex)
                            if(novo!=atual){moverAtual(atual,novo);arrastando=novo;deslocamento=0f}}
                    })}.semantics{contentDescription="Segure e arraste a parada ${indice+1}"},contentAlignment=Alignment.Center){Text("≡",fontSize=25.sp,color=MaterialTheme.colorScheme.primary)}
                Text("${indice+1}. ${endereco.nome}",Modifier.weight(1f),fontSize=12.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                IconButton(onClick={remover(indice)}){Simbolo("fechar",Modifier.size(16.dp),descricao="Remover parada ${indice+1}")}
            }
        }
    }
}
@Composable private fun LinhaParadas(etapa:Etapa,nav:EstadoNavegacao?,ativa:Boolean){
    var expandir by remember{mutableStateOf(ativa)}
    var mostrarPassadas by remember{mutableStateOf(false)}
    TextButton(onClick={expandir=!expandir},contentPadding=PaddingValues(0.dp)){Text(if(expandir)"Recolher paradas" else "Ver ${etapa.paradas.size} paradas",fontSize=11.sp)}
    AnimatedVisibility(expandir){Column{
        val atual=nav?.paradaAtual?:-1
        if(ativa && atual>=0)TextButton(onClick={mostrarPassadas=!mostrarPassadas}){Text("✓ ${atual+1} ponto(s) alcançado(s) · ${if(mostrarPassadas)"recolher" else "mostrar"}",fontSize=10.sp)}
        etapa.paradas.forEachIndexed{indice,parada->if(!ativa || indice>atual || mostrarPassadas){
            val proxima=ativa && indice==atual+1
            Text("${if(proxima)"● PRÓXIMO" else if(ativa && indice<=atual)"✓" else "│"} ${parada.nome}",fontSize=12.sp,fontWeight=if(proxima)FontWeight.Bold else FontWeight.Normal,color=if(proxima)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=5.dp))
        }}
    }}
}

@Composable fun Simbolo(nome:String,modifier:Modifier=Modifier,cor:Color=MaterialTheme.colorScheme.primary,descricao:String?=null){
    Canvas(modifier.semantics{contentDescription=descricao?:when(nome){"inverter"->"Inverter origem e destino";"gps"->"Centralizar na minha localização";"etapas"->"Ver etapas da viagem";"salvo"->"Favoritos";"lua","sol"->"Alternar aparência";"fechar"->"Fechar aviso";"compartilhar"->"Compartilhar viagem";else->nome}}){
        val u=size.minDimension/24f
        fun line(x1:Float,y1:Float,x2:Float,y2:Float)=drawLine(cor,Offset(x1*u,y1*u),Offset(x2*u,y2*u),1.8f*u,StrokeCap.Round)
        fun circle(x:Float,y:Float,r:Float)=drawCircle(cor,r*u,Offset(x*u,y*u),style=Stroke(1.8f*u))
        fun path(vararg pts:Pair<Float,Float>,close:Boolean=false){val p=Path();pts.forEachIndexed{i,xy->if(i==0)p.moveTo(xy.first*u,xy.second*u)else p.lineTo(xy.first*u,xy.second*u)};if(close)p.close();drawPath(p,cor,style=Stroke(1.8f*u,cap=StrokeCap.Round))}
        when(nome){
            "busca"->{circle(10f,10f,6f);line(14.5f,14.5f,21f,21f)}
            "gps"->{circle(12f,12f,7f);circle(12f,12f,2f);line(12f,1f,12f,4f);line(12f,20f,12f,23f);line(1f,12f,4f,12f);line(20f,12f,23f,12f)}
            "origem"->{circle(12f,12f,6f);drawCircle(cor,2f*u,Offset(12*u,12*u))}
            "destino"->{circle(12f,9f,5f);path(7f to 11f,12f to 22f,17f to 11f);drawCircle(cor,1.7f*u,Offset(12*u,9*u))}
            "rota"->{circle(5f,18f,2.5f);circle(19f,5f,2.5f);path(5f to 15.5f,5f to 9f,15f to 9f,15f to 18f,19f to 18f,19f to 7.5f)}
            "salvo"->path(12f to 2.5f,15f to 8.5f,21.5f to 9.5f,17f to 14f,18f to 21f,12f to 17.8f,6f to 21f,7f to 14f,2.5f to 9.5f,9f to 8.5f,close=true)
            "inverter"->{path(5f to 8f,9f to 4f,13f to 8f);line(9f,4f,9f,20f);path(11f to 16f,15f to 20f,19f to 16f);line(15f,20f,15f,4f)}
            "seta"->{line(3f,12f,20f,12f);path(14f to 6f,20f to 12f,14f to 18f)}
            "relogio"->{circle(12f,12f,9f);path(12f to 6f,12f to 12f,16f to 14f)}
            "etapas"->{for(y in listOf(5f,12f,19f)){circle(4f,y,1.5f);line(9f,y,21f,y)}}
            "fechar"->{line(5f,5f,19f,19f);line(5f,19f,19f,5f)}
            "sol"->{circle(12f,12f,4f);for(i in 0..7){val a=i*Math.PI/4;line((12+7*kotlin.math.cos(a)).toFloat(),(12+7*kotlin.math.sin(a)).toFloat(),(12+10*kotlin.math.cos(a)).toFloat(),(12+10*kotlin.math.sin(a)).toFloat())}}
            "lua"->path(20f to 15f,16f to 16f,11f to 14f,8f to 10f,8f to 4f,4f to 7f,3f to 12f,5f to 18f,11f to 21f,17f to 20f,20f to 15f)
            "mapa"->{path(3f to 5f,9f to 3f,15f to 6f,21f to 3f,21f to 19f,15f to 22f,9f to 19f,3f to 21f,close=true);line(9f,3f,9f,19f);line(15f,6f,15f,22f)}
            "onibus"->{path(5f to 18f,5f to 5f,7f to 3f,17f to 3f,19f to 5f,19f to 18f,close=true);line(5f,11f,19f,11f);line(8f,18f,8f,21f);line(16f,18f,16f,21f);circle(8f,15f,1f);circle(16f,15f,1f)}
            "andar"->{circle(14f,4f,2f);path(5f to 13f,10f to 9f,14f to 10f,18f to 14f);path(13f to 9f,11f to 15f,6f to 21f);path(11f to 15f,16f to 18f,17f to 22f)}
            "compartilhar"->{circle(18f,4f,3f);circle(5f,12f,3f);circle(18f,20f,3f);line(8f,10.5f,15f,5.5f);line(8f,13.5f,15f,18.5f)}
            else->{circle(12f,12f,7f);circle(12f,12f,2f);line(12f,2f,12f,5f);line(12f,19f,12f,22f);line(2f,12f,5f,12f);line(19f,12f,22f,12f)}
        }
    }
}
