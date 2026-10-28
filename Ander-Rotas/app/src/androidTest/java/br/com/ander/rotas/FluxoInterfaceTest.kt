package br.com.ander.rotas

import android.Manifest
import android.graphics.Point
import android.graphics.Rect
import android.location.Location
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapView
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class FluxoInterfaceTest {
    @get:Rule val ui=createEmptyComposeRule()
    private lateinit var cenario:ActivityScenario<MainActivity>
    private val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val pacote="br.com.ander.rotas"
    @Before fun preparar(){
        SessaoNavegacao.ativa.value=false;SessaoNavegacao.rota=null;SessaoNavegacao.estado.value=null;SessaoNavegacao.erro.value=null
        listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.POST_NOTIFICATIONS).forEach{device.executeShellCommand("pm grant $pacote $it")}
    }
    @After fun fechar(){if(::cenario.isInitialized)cenario.close();SessaoNavegacao.ativa.value=false;SessaoNavegacao.rota=null;SessaoNavegacao.estado.value=null}
    private fun abrir(){cenario=ActivityScenario.launch(MainActivity::class.java)}
    private fun captura(nome:String){
        ui.waitForIdle();val pasta=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"qa");pasta.mkdirs()
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
        val zoom=camera()!!.zoom;val x=retangulo.left+retangulo.width()*2/5;val y=retangulo.centerY()
        device.findObject(UiSelector().packageName(pacote).instance(0)).performTwoPointerGesture(Point(x-20,y),Point(x+20,y),Point(x-110,y),Point(x+110,y),30)
        ui.waitUntil(5_000){(camera()?.zoom?:zoom)>zoom+.25}
        captura("mapa-arraste-pinca-zoom")
    }
    @Test fun etapaSemGeometriaNaoMostraDistanciaZeroComoChegada(){
        val a=Endereco("Origem",Ponto(-23.53,-46.63));val b=Endereco("Destino",Ponto(-23.54,-46.62))
        val bus=Etapa("BUS","123","Terminal",a.nome,b.nome,0,1000,emptyList(),distanciaMetros=900.0)
        val r=Rota("qa-sem-tracado",a,b,listOf(bus));SessaoNavegacao.rota=r;SessaoNavegacao.ativa.value=true
        SessaoNavegacao.estado.value=MotorNavegacao().atualizar(r,a.ponto,5f)
        abrir();ui.onNodeWithText("sem traçado").assertExists()
        ui.onNodeWithText("Etapa sem geometria suficiente",substring=true).assertExists()
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
        captura("painel-viagem-ativa")
        ui.onNodeWithText("Cancelar").performClick();ui.onNodeWithText("Cancelar esta viagem?").assertExists()
        ui.onNodeWithText("Continuar viagem").performClick();ui.onNodeWithText("Ônibus 054").assertExists()
    }
}
