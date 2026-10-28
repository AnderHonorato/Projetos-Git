package br.com.ander.rotas

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import org.maplibre.android.style.layers.SymbolLayer
import android.graphics.Point
import android.graphics.Rect
import android.location.Location
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.gestures.StandardScaleGestureDetector
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class FluxoInterfaceTest {
    @get:Rule val ui=createEmptyComposeRule()
    private lateinit var cenario:ActivityScenario<MainActivity>
    private val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val pacote="br.com.ander.rotas"
    @Before fun preparar(){
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("aparencia",0).edit().putBoolean("introducao_concluida_v1",true).commit()
        SessaoNavegacao.ativa.value=false;SessaoNavegacao.rota=null;SessaoNavegacao.estado.value=null;SessaoNavegacao.erro.value=null
        listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.POST_NOTIFICATIONS).forEach{device.executeShellCommand("pm grant $pacote $it")}
    }
    @After fun fechar(){if(::cenario.isInitialized)cenario.close();SessaoNavegacao.ativa.value=false;SessaoNavegacao.rota=null;SessaoNavegacao.estado.value=null}
    private fun abrir(){cenario=ActivityScenario.launch(MainActivity::class.java)}
    private fun captura(nome:String){
        ui.waitForIdle();InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(600);val pasta=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"qa");pasta.mkdirs()
        assertTrue(device.takeScreenshot(File(pasta,"$nome.png")))
    }
    private fun mapa(view:View):MapView?{
        if(view is MapView)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)mapa(view.getChildAt(i))?.let{return it}
        return null
    }
    private fun camera():CameraPosition?{
        val ref=AtomicReference<CameraPosition?>()
        cenario.onActivity{a->mapa(a.window.decorView)?.getMapAsync{ref.set(it.cameraPosition)}}
        return ref.get()
    }
    @Test fun introducaoIlustradaApareceUmaVezEPodeSerConcluida(){
        val prefs=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("aparencia",0)
        prefs.edit().putBoolean("introducao_concluida_v1",false).commit();abrir()
        ui.onNodeWithText("Buscar endereço").assertExists();ui.onNodeWithContentDescription("Mascote animado").assertExists();captura("introducao-01")
        ui.onNodeWithText("Próximo").performClick();ui.onNodeWithText("Mapa e paradas").assertExists();captura("introducao-02")
        ui.onNodeWithText("Próximo").performClick();ui.onNodeWithText("Acompanhar a viagem").assertExists();captura("introducao-03")
        ui.onNodeWithText("Começar").performClick();ui.onNodeWithText("Digite seu destino").assertExists()
        assertTrue(prefs.getBoolean("introducao_concluida_v1",false));cenario.close();abrir()
        ui.onAllNodesWithText("Buscar endereço").assertCountEquals(0)
    }
    @Test fun digitacaoMostraSugestoesSelecionaveisSemBotaoPesquisar(){
        abrir();ui.onNodeWithText("Digite seu destino").performClick()
        ui.onNode(hasSetTextAction()).performTextInput("Rua Silveira")
        ui.waitUntil(20_000){ui.onAllNodesWithText("Rua Silveira Tavares",substring=true).fetchSemanticsNodes().isNotEmpty()}
        captura("busca-sugestoes-reais")
        ui.onAllNodesWithText("Rua Silveira Tavares",substring=true).onFirst().performClick()
        ui.onNodeWithText("Rua Silveira Tavares").assertExists()
        ui.onAllNodesWithText("ANDER",substring=true).assertCountEquals(0)
    }
    @Test fun arrastarPincharEBotoesAlteramCameraReal(){
        abrir();ui.waitUntil(20_000){camera()!=null}
        // Aguarda a origem inicial para não confundir recentralização com gesto.
        ui.waitUntil(20_000){ui.onAllNodesWithText("Minha localização").fetchSemanticsNodes().isNotEmpty()}
        val antes=camera()!!.zoom
        ui.onNodeWithContentDescription("Aumentar zoom").performClick()
        ui.waitUntil(5_000){(camera()?.zoom?:antes)>antes+.5}
        val retangulo=Rect();cenario.onActivity{mapa(it.window.decorView)!!.getGlobalVisibleRect(retangulo)}
        val origem=camera()!!.target!!
        device.swipe(retangulo.left+retangulo.width()/4,retangulo.centerY(),retangulo.left+retangulo.width()/2,retangulo.centerY(),25)
        ui.waitUntil(5_000){camera()?.target?.let{MotorNavegacao.distancia(Ponto(origem.latitude,origem.longitude),Ponto(it.latitude,it.longitude))>15}?:false}
        captura("mapa-apos-arrastar")
        // Encerra a inércia do arraste antes de medir o gesto seguinte.
        val escalas=java.util.concurrent.atomic.AtomicInteger(0)
        cenario.onActivity{a->mapa(a.window.decorView)!!.getMapAsync{m->
            m.cancelTransitions()
            assertTrue(m.uiSettings.isZoomGesturesEnabled)
            m.addOnScaleListener(object:MapLibreMap.OnScaleListener{
                override fun onScaleBegin(detector:StandardScaleGestureDetector){escalas.incrementAndGet()}
                override fun onScale(detector:StandardScaleGestureDetector){}
                override fun onScaleEnd(detector:StandardScaleGestureDetector){}
            })
        }}
        val zoom=camera()!!.zoom;val x=retangulo.left+retangulo.width()*2/5;val y=retangulo.centerY()
        val inicio=retangulo.width()/10;val fim=minOf(retangulo.width()/4,retangulo.height()/3)
        assertTrue(device.findObject(UiSelector().packageName(pacote).instance(0)).performTwoPointerGesture(Point(x-inicio,y-inicio),Point(x+inicio,y+inicio),Point(x-fim,y-fim),Point(x+fim,y+fim),30))
        try{ui.waitUntil(5_000){(camera()?.zoom?:zoom)>zoom+.25}}finally{
            val pasta=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"qa");pasta.mkdirs()
            File(pasta,"gesto-pinca.txt").writeText("mapa=$retangulo zoomAntes=$zoom zoomDepois=${camera()?.zoom} gestosEscala=${escalas.get()}")
            captura("mapa-apos-pinca")
        }
        captura("mapa-arraste-pinca-zoom")
    }
    @Test fun abrirSemViagemRemoveAvisosAntigosENaoCriaNotificacoes(){
        val contexto=InstrumentationRegistry.getInstrumentation().targetContext
        val nm=contexto.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("qa-aviso-antigo","Aviso antigo",NotificationManager.IMPORTANCE_LOW))
        nm.notify(101,Notification.Builder(contexto,"qa-aviso-antigo").setSmallIcon(R.drawable.ic_launcher).setContentTitle("Aviso de viagem anterior").build())
        abrir();ui.waitUntil(5_000){nm.activeNotifications.none{it.id==100 || it.id==101}}
        ui.onAllNodesWithText("Seu caminho, mais leve.").assertCountEquals(0)
        captura("inicial-sem-notificacoes")
    }
    @Test fun notificacaoSoApareceDepoisDeIrEDesapareceAoCancelar(){
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences("aparencia",0).edit().putBoolean("voz_permitida",false).commit()
        val nm=ctx.getSystemService(NotificationManager::class.java)
        abrir();assertTrue(nm.activeNotifications.none{it.id==100 || it.id==101})
        val a=Endereco("Praça inicial",Ponto(-23.533,-46.625));val b=Endereco("Terminal",Ponto(-23.55,-46.64));val agora=System.currentTimeMillis()
        val r=Rota("qa-notificacao",a,b,listOf(Etapa("WALK",origem=a.nome,destino=b.nome,inicio=agora,fim=agora+600_000,pontos=listOf(a.ponto,b.ponto))))
        cenario.onActivity{ViewModelProvider(it)[RotasViewModel::class.java].selecionarRota(r)}
        ui.onNodeWithText("Ir").performClick()
        ui.waitUntil(12_000){nm.activeNotifications.any{it.id==100}}
        ui.onNodeWithContentDescription("Cancelar").performClick()
        ui.onNodeWithText("Cancelar esta viagem?").assertExists()
        ui.onNodeWithText("Cancelar viagem").performClick()
        ui.waitUntil(5_000){nm.activeNotifications.none{it.id==100 || it.id==101}}
        assertFalse(SessaoNavegacao.ativa.value)
        captura("apos-cancelar-sem-notificacao")
    }
    @Test fun mapaMantemNomesDeRuasELugares(){
        abrir()
        ui.waitUntil(20_000){
            val resultado=java.util.concurrent.atomic.AtomicBoolean(false)
            cenario.onActivity{a->mapa(a.window.decorView)?.getMapAsync{m->
                val camadas=m.style?.layers?.filterIsInstance<SymbolLayer>().orEmpty()
                resultado.set(camadas.isNotEmpty() && camadas.any{(it.textOpacity.value?:1f)>0f})
            }}
            resultado.get()
        }
        captura("mapa-com-nomes")
    }
    @Test fun etapaSemGeometriaNaoMostraDistanciaZeroComoChegada(){
        val a=Endereco("Origem",Ponto(-23.53,-46.63));val b=Endereco("Destino",Ponto(-23.54,-46.62))
        val bus=Etapa("BUS","123","Terminal",a.nome,b.nome,0,1000,emptyList(),distanciaMetros=900.0)
        val r=Rota("qa-sem-tracado",a,b,listOf(bus));SessaoNavegacao.rota=r;SessaoNavegacao.ativa.value=true
        SessaoNavegacao.estado.value=MotorNavegacao().atualizar(r,a.ponto,5f)
        abrir();ui.onNodeWithText("sem traçado").assertExists()
        ui.onNode(hasText("Etapa sem geometria suficiente",substring=true) and hasAnyAncestor(hasTestTag("painel-viagem"))).assertExists()
        ui.onAllNodesWithText("0 m").assertCountEquals(0)
        captura("painel-sem-tracado")
    }
    @Test fun painelDaViagemMostraLinhaProximoPontoEProgresso(){
        val ps=(0..4).map{Endereco("Ponto $it",Ponto(-23.53,-46.63+it*.002))}
        val agora=System.currentTimeMillis()
        val bus=Etapa("BUS","054","Terminal Central",ps.first().nome,ps.last().nome,agora,agora+900_000,ps.map{it.ponto},ps,distanciaMetros=900.0)
        val rota=Rota("qa-painel",ps.first(),ps.last(),listOf(bus))
        SessaoNavegacao.rota=rota;SessaoNavegacao.ativa.value=true
        SessaoNavegacao.estado.value=EstadoNavegacao(etapaAtual=0,metrosAteFim=680.0,mensagem="Siga até o desembarque.",paradaAtual=1,pontosRestantes=3,proximaParada="Ponto 2")
        abrir();ui.onNodeWithText("Ônibus 054").assertExists();ui.onNodeWithText("Ponto 2").assertExists();ui.onNodeWithText("3").assertExists()
        ui.onNodeWithText("Roteiro").assertExists();ui.onAllNodesWithText("ANDER",substring=true).assertCountEquals(0)
        val mapaPronto=java.util.concurrent.atomic.AtomicBoolean(false)
        cenario.onActivity{a->mapa(a.window.decorView)?.addOnDidBecomeIdleListener{mapaPronto.set(true)}}
        SessaoNavegacao.emitir(Location("gps").apply{latitude=ps[1].ponto.latitude;longitude=ps[1].ponto.longitude;accuracy=6f;time=System.currentTimeMillis();elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()})
        ui.waitUntil(5_000){ui.onAllNodesWithText("Perto de Ponto 1").fetchSemanticsNodes().isNotEmpty()}
        ui.waitUntil(20_000){
            val pronto=java.util.concurrent.atomic.AtomicBoolean(false)
            cenario.onActivity{a->mapa(a.window.decorView)?.getMapAsync{m->pronto.set(m.style?.layers?.isNotEmpty()==true)}}
            pronto.get()
        }
        ui.waitUntil(25_000){mapaPronto.get()}
        captura("painel-viagem-ativa")
        ui.onNodeWithContentDescription("Cancelar").performClick();ui.onNodeWithText("Cancelar esta viagem?").assertExists()
        ui.onNodeWithText("Continuar viagem").performClick();ui.onNodeWithText("Ônibus 054").assertExists()
    }
}
