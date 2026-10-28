package br.com.ander.rotas

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private lateinit var modelo: RotasViewModel
    private var iniciarDepoisDaPermissao = false
    private var gpsSilencioso=false
    private var mostrarIntroducao by mutableStateOf(false)
    private val permissoesLocalizacao = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissoes ->
        if (permissoes.values.any { it }) {
            localizar(gpsSilencioso)
            if (iniciarDepoisDaPermissao) pedirVozEIniciar()
        } else if(!gpsSilencioso)Toast.makeText(this,"Permita a localização ou digite a origem.",Toast.LENGTH_LONG).show()
        iniciarDepoisDaPermissao = false
    }
    private val permissaoNotificacao = registerForActivityResult(ActivityResultContracts.RequestPermission()) { iniciarServico() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NavegacaoService.limparAvisosSemViagem(this)
        modelo = ViewModelProvider(this)[RotasViewModel::class.java]
        val prefs=getSharedPreferences("aparencia",0)
        mostrarIntroducao=savedInstanceState?.getBoolean("mostrar_introducao")?:!prefs.getBoolean("introducao_concluida_v1",false)
        setContent {
            if(mostrarIntroducao)IntroducaoRotas(concluir={
                prefs.edit().putBoolean("introducao_concluida_v1",true).apply();mostrarIntroducao=false
                if(!SessaoNavegacao.ativa.value)solicitarGps(false,true)
            })else InterfaceRotas(modelo,onGps={solicitarGps(false)},onIniciar={solicitarGps(true)},
                onParar={if(modelo.estado.value.navegacao?.chegou==true)modelo.parar()else modelo.solicitarCancelamento()},
                onAjuda={mostrarIntroducao=true})
        }
        tratarIntent(intent)
        if(savedInstanceState==null && !mostrarIntroducao && !SessaoNavegacao.ativa.value)solicitarGps(false,true)
    }
    override fun onSaveInstanceState(outState:Bundle){outState.putBoolean("mostrar_introducao",mostrarIntroducao);super.onSaveInstanceState(outState)}
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);tratarIntent(intent)}
    private fun tratarIntent(intent:Intent){
        if(intent.getBooleanExtra("confirmar_cancelamento",false) && intent.getStringExtra("rota_id")==SessaoNavegacao.rota?.id){mostrarIntroducao=false;modelo.solicitarCancelamento()}
        intent.removeExtra("confirmar_cancelamento")
    }
    private fun pedirVozEIniciar(){
        val prefs=getSharedPreferences("aparencia",0)
        if(prefs.contains("voz_permitida")){pedirNotificacaoEIniciar();return}
        AlertDialog.Builder(this).setTitle("Permitir avisos por voz?")
            .setMessage("O app pode falar quando faltarem dois pontos e ao chegar ao desembarque, inclusive com a tela bloqueada. Você pode alterar em Ajustes.")
            .setPositiveButton("Permitir"){_,_->prefs.edit().putBoolean("voz_permitida",true).apply();pedirNotificacaoEIniciar()}
            .setNegativeButton("Agora não"){_,_->prefs.edit().putBoolean("voz_permitida",false).apply();pedirNotificacaoEIniciar()}.show()
    }
    private fun solicitarGps(iniciar:Boolean,silencioso:Boolean=false) {
        if(iniciar && modelo.estado.value.navegando)return
        gpsSilencioso=silencioso
        val tem = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (tem) { localizar(silencioso); if (iniciar) pedirVozEIniciar() }
        else { iniciarDepoisDaPermissao = iniciar; permissoesLocalizacao.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    }
    private fun pedirNotificacaoEIniciar() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissaoNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        else iniciarServico()
    }
    private fun iniciarServico(){modelo.iniciar()}
    private fun localizar(silencioso:Boolean=false) {
        NavegacaoService.solicitarLocalizacao(this) { leitura ->
            if (leitura != null) modelo.atualizarPosicao(leitura.ponto,leitura.precisaoMetros,leitura.instante)
            else if(!silencioso)Toast.makeText(this,"Localização indisponível. Ative o GPS ou digite a origem.",Toast.LENGTH_LONG).show()
        }
    }
}
