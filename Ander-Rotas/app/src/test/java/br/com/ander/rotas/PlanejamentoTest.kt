package br.com.ander.rotas

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class PlanejamentoTest {
    private lateinit var vm:RotasViewModel
    private lateinit var app:AppTeste
    private val store=ViewModelStore()
    private val a=Endereco("Origem",Ponto(-23.5,-46.6))
    private val b=Endereco("Destino",Ponto(-23.4,-46.5))
    private val c=Endereco("Parada C",Ponto(-23.45,-46.55))
    private val d=Endereco("Parada D",Ponto(-23.43,-46.53))
    private val rota=Rota("ativa",a,b,listOf(Etapa("BUS",origem=a.nome,destino=b.nome,inicio=0,fim=1000,pontos=listOf(a.ponto,b.ponto))),paradasDesejadas=listOf(c,d))
    @Before fun preparar(){
        Dispatchers.setMain(UnconfinedTestDispatcher())
        SessaoNavegacao.ativa.value=false;SessaoNavegacao.rota=null;SessaoNavegacao.estado.value=null;SessaoNavegacao.erro.value=null
        app=AppTeste()
    }
    private fun abrir(ativa:Boolean=false){
        if(ativa){SessaoNavegacao.rota=rota;SessaoNavegacao.ativa.value=true;SessaoNavegacao.estado.value=MotorNavegacao().iniciar(rota)}
        vm=RotasViewModel(app);store.put("vm",vm)
    }
    @After fun limpar(){store.clear();SessaoNavegacao.ativa.value=false;SessaoNavegacao.rota=null;SessaoNavegacao.estado.value=null;Dispatchers.resetMain();app.pasta.deleteRecursively()}
    @Test fun abreSemOrigemOuDestinoPredefinido(){abrir();assertEquals("",vm.estado.value.origem.nome);assertEquals("",vm.estado.value.destino.nome);assertNull(vm.estado.value.rotaSelecionada)}
    @Test fun gpsPreencheOrigemMasNaoSobrescreveEscolhaManual(){
        abrir();vm.atualizarPosicao(a.ponto,8f);assertEquals(a.ponto,vm.estado.value.origem.ponto)
        vm.selecionarEndereco(c,true);vm.atualizarPosicao(b.ponto,5f)
        assertEquals(c,vm.estado.value.origem)
    }
    @Test fun ordenaParadasSemAlterarDestino(){
        abrir();vm.selecionarEndereco(b,false)
        listOf(c,d).forEach{vm.prepararBusca(false,true);vm.selecionarEndereco(it,false)}
        vm.moverParada(0,1);assertEquals(listOf(d,c),vm.estado.value.paradasDesejadas);assertEquals(b,vm.estado.value.destino)
        vm.removerParada(0);assertEquals(listOf(c),vm.estado.value.paradasDesejadas)
    }
    @Test fun favoritaPreservaParadasNaIdaENaVolta(){
        abrir();val r=RepositorioRotas(app);val favorita=ViagemFavorita("Com paradas",a,b,listOf(c,d))
        r.salvarViagemFavorita(favorita);val salva=r.viagensFavoritas().single();assertEquals(favorita,salva)
        vm.abrirViagem(salva);assertEquals(listOf(c,d),vm.estado.value.paradasDesejadas)
        vm.abrirViagem(salva,true);assertEquals(listOf(d,c),vm.estado.value.paradasDesejadas);assertEquals(b,vm.estado.value.origem)
    }
    @Test fun viagemAtivaBloqueiaTodosOsCaminhosDeTrocaDeDestino(){
        abrir(true)
        val acoes=listOf<()->Unit>(
            {vm.editarTexto("Outro destino",false)},{vm.prepararBusca(false,true)},{vm.selecionarEndereco(d,false)},
            {vm.selecionarEndereco(d,true)},{vm.inverter()},{vm.calcular()},{vm.definirQuando(100)},{vm.sairAgora()},
            {vm.selecionarRota(rota.copy(id="outra",destino=d))},{vm.abrirRotaSalva(rota.copy(id="salva"))},
            {vm.abrirViagem(ViagemFavorita("Outra",c,d))},{vm.moverParada(0,1)},{vm.removerParada(0)},
            {vm.usarLocalizacaoComoOrigem()},{vm.recalcular()})
        acoes.forEach{acao->acao();val e=vm.estado.value;assertEquals(a,e.origem);assertEquals(b,e.destino);assertEquals(rota,e.rotaSelecionada);assertEquals(listOf(c,d),e.paradasDesejadas);assertTrue(e.navegando)}
    }
    @Test fun cancelarExigeConfirmacaoEMantemViagemSeRecusar(){
        abrir(true);vm.solicitarCancelamento();assertTrue(vm.estado.value.confirmarCancelamento);assertTrue(vm.estado.value.navegando)
        vm.manterViagem();assertFalse(vm.estado.value.confirmarCancelamento);assertTrue(vm.estado.value.navegando)
        vm.solicitarCancelamento();vm.parar();assertFalse(vm.estado.value.navegando);assertNull(vm.estado.value.rotaSelecionada);assertFalse(vm.estado.value.confirmarCancelamento)
    }
    private class AppTeste:Application(){
        val pasta:File=Files.createTempDirectory("rotas-teste-").toFile()
        private val valores=mutableMapOf<String,Any?>()
        private lateinit var prefs:SharedPreferences
        private val editor=Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,arrayOf(SharedPreferences.Editor::class.java)){proxy,method,args->
            when(method.name){
                "putString","putBoolean","putInt","putLong","putFloat","putStringSet"->{valores[args!![0] as String]=args[1];proxy}
                "remove"->{valores.remove(args!![0]);proxy}
                "clear"->{valores.clear();proxy}
                "commit"->true
                "apply"->null
                else->null
            }
        } as SharedPreferences.Editor
        init{prefs=Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,arrayOf(SharedPreferences::class.java)){_,method,args->
            when(method.name){"getAll"->valores.toMap();"contains"->valores.containsKey(args!![0]);"edit"->editor
                "getString","getBoolean","getInt","getLong","getFloat","getStringSet"->valores[args!![0]]?:args[1]
                else->null}
        } as SharedPreferences}
        override fun getApplicationContext():Context=this
        override fun getSharedPreferences(name:String,mode:Int):SharedPreferences=prefs
        override fun getFilesDir():File=pasta
    }
}
