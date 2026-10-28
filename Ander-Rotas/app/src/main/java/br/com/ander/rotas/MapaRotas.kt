package br.com.ander.rotas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.offline.*
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

const val ESTILO_MAPA = "https://tiles.openfreemap.org/styles/liberty"
private var mapaConfigurado = false
@Synchronized private fun configurarMapLibre(context: Context) {
    MapLibre.getInstance(context.applicationContext)
    if (mapaConfigurado) return
    val dispatcher = Dispatcher().apply { maxRequests = 4; maxRequestsPerHost = 4 }
    HttpRequestUtil.setOkHttpClient(OkHttpClient.Builder().dispatcher(dispatcher).addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", "Rotas/1.2.0 (Android; native MapLibre)").build()) }.build())
    mapaConfigurado = true
}

@Composable
fun MapaRotas(origem: Endereco, destino: Endereco, rota: Rota?, posicao: Ponto?, focoGps: Int, navegando: Boolean, rumo: Float? = null, navegacao: EstadoNavegacao? = null, zoomComando: Int = 0, norteComando: Int = 0, modifier: Modifier = Modifier, onPonto: (Ponto) -> Unit, onEstado: (String?) -> Unit) {
    val context = LocalContext.current
    remember(context) { configurarMapLibre(context); true }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val onEstadoAtual by rememberUpdatedState(onEstado)
    val onPontoAtual by rememberUpdatedState(onPonto)
    var mapa by remember { mutableStateOf<MapLibreMap?>(null) }
    var pronto by remember { mutableStateOf(false) }
    var seguindoGps by remember { mutableStateOf(false) }
    var zoomAtual by remember { mutableDoubleStateOf(10.0) }
    var centroAtual by remember { mutableStateOf(Ponto(-23.43,-46.68)) }
    var marcadorGps by remember { mutableStateOf<Marker?>(null) }
    val density = context.resources.displayMetrics.density
    val icones = remember {
        listOf(0xFF8F2D4A.toInt(), 0xFF2D7668.toInt(), 0xFF3770D7.toInt()).map { cor ->
            val bitmap = Bitmap.createBitmap((32*density).toInt(), (40*density).toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap); val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = android.graphics.Color.WHITE; canvas.drawCircle(bitmap.width/2f, bitmap.height/2f, 13*density, paint)
            paint.color = cor; canvas.drawCircle(bitmap.width/2f, bitmap.height/2f, 9*density, paint)
            paint.color = android.graphics.Color.WHITE; canvas.drawCircle(bitmap.width/2f, bitmap.height/2f, 3*density, paint)
            IconFactory.getInstance(context).fromBitmap(bitmap)
        }
    }
    val view = remember {
        MapView(context).apply {
            onCreate(null)
            setOnTouchListener { view,event ->
                when(event.actionMasked){
                    MotionEvent.ACTION_DOWN->{seguindoGps=false;view.parent?.requestDisallowInterceptTouchEvent(true)}
                    MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
            addOnDidFailLoadingMapListener { onEstadoAtual("Mapa indisponível. Verifique a conexão e tente novamente.") }
            getMapAsync { map ->
                mapa = map
                map.uiSettings.apply { setAllGesturesEnabled(true);isScrollGesturesEnabled=true;isZoomGesturesEnabled=true;isRotateGesturesEnabled=true;isTiltGesturesEnabled=true; isLogoEnabled = false; isAttributionEnabled = false; isCompassEnabled = true; setCompassMargins(16, 150, 16, 16) }
                map.addOnCameraIdleListener { zoomAtual=map.cameraPosition.zoom;map.cameraPosition.target?.let{centroAtual=Ponto(it.latitude,it.longitude)} }
                map.addOnMapLongClickListener { point -> onPontoAtual(Ponto(point.latitude, point.longitude)); true }
                map.addOnCameraMoveStartedListener { reason -> if (reason == 1) seguindoGps = false }
                map.cameraPosition = CameraPosition.Builder().target(LatLng(-23.43,-46.68)).zoom(10.0).build()
                map.setStyle(Style.Builder().fromUri(ESTILO_MAPA)) { pronto = true; onEstadoAtual(null) }
            }
        }
    }
    DisposableEffect(view, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> when(event) {
            Lifecycle.Event.ON_START -> view.onStart(); Lifecycle.Event.ON_RESUME -> view.onResume(); Lifecycle.Event.ON_PAUSE -> view.onPause(); Lifecycle.Event.ON_STOP -> view.onStop(); else -> Unit
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); view.onPause(); view.onStop(); view.onDestroy() }
    }
    AndroidView(factory={view},modifier=modifier.semantics{
        contentDescription="Mapa de rotas. Arraste com um dedo e use dois dedos para ampliar ou reduzir."
        stateDescription="Zoom ${"%.1f".format(java.util.Locale.US,zoomAtual)} · centro ${"%.5f".format(java.util.Locale.US,centroAtual.latitude)}, ${"%.5f".format(java.util.Locale.US,centroAtual.longitude)}"
    })
    var ultimoZoomComando by remember{mutableIntStateOf(0)}
    LaunchedEffect(pronto,zoomComando){
        if(pronto && zoomComando!=ultimoZoomComando){
            seguindoGps=false
            mapa?.animateCamera(CameraUpdateFactory.zoomBy((zoomComando-ultimoZoomComando).toDouble()),250)
            ultimoZoomComando=zoomComando
        }
    }
    LaunchedEffect(pronto,norteComando){if(pronto && norteComando>0){seguindoGps=false;mapa?.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(mapa!!.cameraPosition).bearing(0.0).build()),250)}}
    LaunchedEffect(pronto,origem,destino,rota,navegando,navegacao?.etapaAtual,navegacao?.paradaAtual){
        val map = mapa ?: return@LaunchedEffect
        if (!pronto) return@LaunchedEffect
        map.clear(); marcadorGps = null
        val indiceAtual=navegacao?.etapaAtual?:0
        rota?.etapas?.forEachIndexed{indice,etapa->
            val passada=navegando && indice<indiceAtual
            if(etapa.pontos.size>=2){
                val points=etapa.pontos.map{LatLng(it.latitude,it.longitude)}
                map.addPolyline(PolylineOptions().addAll(points).color(android.graphics.Color.WHITE).width(if(passada)4f else 8f))
                map.addPolyline(PolylineOptions().addAll(points).color(if(passada)android.graphics.Color.LTGRAY else corModoAndroid(etapa.modo)).width(if(passada)2f else 5f))
            }
            if(!passada && etapa.modo.uppercase() !in setOf("WALK","FOOT","WALKING","CAMINHADA","A_PÉ","A PE")){
                etapa.paradas.forEachIndexed{ordem,parada->
                    val atual=navegando && indice==indiceAtual
                    if(MotorNavegacao.valido(parada.ponto) && (!atual || ordem>(navegacao?.paradaAtual?:0))){
                        val proxima=atual && ordem==(navegacao?.paradaAtual?:0)+1
                        map.addMarker(MarkerOptions().position(LatLng(parada.ponto.latitude,parada.ponto.longitude))
                            .title("${if(proxima)"Próximo ponto" else if(ordem==0)"Embarque" else if(ordem==etapa.paradas.lastIndex)"Desembarque" else "Parada"} · ${parada.nome}")
                            .snippet("${etapa.linha} · ${etapa.sentido}").icon(icones[if(proxima)1 else 0]))
                    }
                }
            }
        }
        rota?.paradasDesejadas?.forEachIndexed{i,e->if(MotorNavegacao.valido(e.ponto))map.addMarker(MarkerOptions().position(LatLng(e.ponto.latitude,e.ponto.longitude)).title("Parada desejada ${i+1} · ${e.nome}").icon(icones[1]))}
        if(MotorNavegacao.valido(origem.ponto))map.addMarker(MarkerOptions().position(LatLng(origem.ponto.latitude,origem.ponto.longitude)).title("Origem · ${origem.nome}").icon(icones[0]))
        if(MotorNavegacao.valido(destino.ponto))map.addMarker(MarkerOptions().position(LatLng(destino.ponto.latitude,destino.ponto.longitude)).title("Destino · ${destino.nome}").icon(icones[1]))
    }
    LaunchedEffect(pronto,posicao,rota,origem.ponto,destino.ponto,navegando,navegacao?.etapaAtual,navegacao?.paradaAtual) {
        val map = mapa ?: return@LaunchedEffect; val ponto = posicao ?: return@LaunchedEffect
        if (!pronto) return@LaunchedEffect
        val latLng = LatLng(ponto.latitude,ponto.longitude)
        if (marcadorGps == null) marcadorGps = map.addMarker(MarkerOptions().position(latLng).title("Sua localização").icon(icones[2]))
        else marcadorGps?.position = latLng
    }
    LaunchedEffect(pronto,rota?.id,origem.ponto,destino.ponto) {
        val map = mapa ?: return@LaunchedEffect
        if (!pronto) return@LaunchedEffect
        val points = rota?.etapas?.flatMap { it.pontos }.orEmpty().ifEmpty { listOf(origem.ponto,destino.ponto).filter(MotorNavegacao::valido) }
        if(points.size==1)map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points[0].latitude,points[0].longitude),15.0),700)
        if (points.size > 1) runCatching { map.animateCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points.map { LatLng(it.latitude,it.longitude) }).build(),80,100,80,70),900) }
    }
    LaunchedEffect(navegando){if(navegando)seguindoGps=true}
    LaunchedEffect(focoGps) { if (focoGps > 0) seguindoGps = true }
    LaunchedEffect(seguindoGps,pronto,posicao,if(navegando) rumo else null) {
        if (seguindoGps && pronto && posicao != null) mapa?.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(posicao.latitude,posicao.longitude)).zoom(15.5).tilt(0.0).bearing(if(navegando) rumo?.toDouble() ?: 0.0 else 0.0).build()),700)
    }
}
fun corModoAndroid(modo: String): Int = when(modo.uppercase()) {
    "WALK","FOOT","WALKING" -> android.graphics.Color.rgb(124,105,97)
    "SUBWAY","METRO","RAIL","TRAIN" -> android.graphics.Color.rgb(44,115,105)
    else -> android.graphics.Color.rgb(143,45,74)
}

data class EstadoMapaOffline(val mensagem: String = "Nenhuma região salva neste aparelho", val ocupado: Boolean = false)
object MapasOffline {
    private val _estado = MutableStateFlow(EstadoMapaOffline()); val estado = _estado.asStateFlow()
    private var ativa: OfflineRegion? = null
    private var geracao = 0L
    fun atualizar(context: Context) {
        if (_estado.value.ocupado) return
        configurarMapLibre(context)
        val token = geracao
        OfflineManager.getInstance(context.applicationContext).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                if(token != geracao) return
                val regioes = offlineRegions.orEmpty()
                if(regioes.isEmpty()) _estado.value = EstadoMapaOffline()
                else regioes.last().getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                    override fun onStatus(status: OfflineRegionStatus?) { if(token != geracao || status == null) return; _estado.value = EstadoMapaOffline(if(status.isComplete) "Região disponível sem internet · ${status.completedResourceSize/1_000_000} MB" else "Download anterior incompleto. Salve novamente para tentar.") }
                    override fun onError(error: String?) { if(token == geracao) _estado.value = EstadoMapaOffline("Não foi possível verificar os mapas salvos.") }
                })
            }
            override fun onError(error: String) { if(token == geracao) _estado.value = EstadoMapaOffline("Não foi possível verificar os mapas salvos.") }
        })
    }
    fun salvar(context: Context,destino: Endereco) {
        if(_estado.value.ocupado) return
        configurarMapLibre(context)
        if (!destino.ponto.latitude.isFinite() || !destino.ponto.longitude.isFinite() || kotlin.math.abs(destino.ponto.latitude) > 80.0 || kotlin.math.abs(destino.ponto.longitude) > 179.7) {
            _estado.value = EstadoMapaOffline("Este recorte cruza o limite do mapa offline. Escolha um ponto fora das regiões polares ou do antimeridiano.")
            return
        }
        val token = ++geracao
        _estado.value = EstadoMapaOffline("Preparando região de ${destino.nome}…",true)
        excluirInterno(context,token,{
            if(token != geracao) return@excluirInterno
            val manager = OfflineManager.getInstance(context.applicationContext); manager.setOfflineMapboxTileCountLimit(3000L)
            val lat = destino.ponto.latitude.coerceIn(-80.0,80.0); val lon = destino.ponto.longitude.coerceIn(-179.8,179.8)
            val dLat = 4.0/111.32; val dLon = 4.0/(111.32*kotlin.math.cos(Math.toRadians(lat)))
            val definition = OfflineTilePyramidRegionDefinition(ESTILO_MAPA,LatLngBounds.from(lat+dLat,lon+dLon,lat-dLat,lon-dLon),10.0,14.0,1f)
            manager.createOfflineRegion(definition,destino.nome.toByteArray(Charsets.UTF_8),object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    if(token != geracao) {
                        offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        offlineRegion.delete(object : OfflineRegion.OfflineRegionDeleteCallback { override fun onDelete() = Unit; override fun onError(error: String) = Unit })
                        return
                    }
                    ativa = offlineRegion
                    fun terminar(texto: String) { offlineRegion.setObserver(null); offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE); ativa = null; if(token == geracao) _estado.value = EstadoMapaOffline(texto) }
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            if(token != geracao) return
                            if(status.isComplete) terminar("Região salva · ${status.completedResourceSize/1_000_000} MB · detalhes até zoom 14")
                            else if(status.completedResourceSize >= 49_000_000L || status.completedTileCount >= 3000L) terminar("Limite da região atingido. Parte do mapa foi salva.")
                            else { val progresso = if(status.requiredResourceCount>0) (status.completedResourceCount*100/status.requiredResourceCount).coerceIn(0,99) else 0; _estado.value = EstadoMapaOffline("Salvando mapa · $progresso% · ${status.completedResourceSize/1_000_000} MB",true) }
                        }
                        override fun onError(error: OfflineRegionError) { terminar("Download interrompido. Verifique a conexão e tente novamente.") }
                        override fun mapboxTileCountLimitExceeded(limit: Long) { terminar("Limite de 3.000 blocos atingido. Download pausado.") }
                    }); offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }
                override fun onError(error: String) { if(token == geracao) _estado.value = EstadoMapaOffline("Não foi possível salvar esta região. Tente novamente.") }
            })
        },{ if(token == geracao) _estado.value = EstadoMapaOffline("Não foi possível preparar a região. Tente novamente.") })
    }
    fun excluir(context: Context) {
        val token = ++geracao
        ativa?.setObserver(null); ativa?.setDownloadState(OfflineRegion.STATE_INACTIVE); ativa = null
        _estado.value = EstadoMapaOffline("Excluindo mapas…",true)
        excluirInterno(context,token,{ if(token == geracao) _estado.value = EstadoMapaOffline("Mapas salvos excluídos.") },{ if(token == geracao) _estado.value = EstadoMapaOffline("Não foi possível excluir os mapas.") })
    }
    private fun excluirInterno(context: Context,token: Long,pronto: () -> Unit,erro: () -> Unit) {
        OfflineManager.getInstance(context.applicationContext).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                if(token != geracao) return
                val regioes = offlineRegions.orEmpty(); if(regioes.isEmpty()) { pronto(); return }
                var restantes = regioes.size; var falhou = false
                regioes.forEach { regiao -> regiao.setObserver(null); regiao.setDownloadState(OfflineRegion.STATE_INACTIVE); regiao.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() { restantes--; if(restantes==0 && !falhou && token == geracao) pronto() }
                    override fun onError(error: String) { if(!falhou && token == geracao) { falhou=true; erro() } }
                }) }
            }
            override fun onError(error: String) { if(token == geracao) erro() }
        })
    }
}
