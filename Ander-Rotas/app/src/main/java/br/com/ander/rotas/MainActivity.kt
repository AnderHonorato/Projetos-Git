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

class MainActivity : ComponentActivity() {
    private lateinit var modelo: RotasViewModel
    private var iniciarDepoisDaPermissao = false
    private val permissoesLocalizacao = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissoes ->
        if (permissoes.values.any { it }) {
            localizar()
            if (iniciarDepoisDaPermissao) pedirVozEIniciar()
        } else Toast.makeText(this, "Você pode digitar a origem. Para acompanhar a viagem, permita a localização.", Toast.LENGTH_LONG).show()
        iniciarDepoisDaPermissao = false
    }
    private val permissaoNotificacao = registerForActivityResult(ActivityResultContracts.RequestPermission()) { iniciarServico() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        modelo = ViewModelProvider(this)[RotasViewModel::class.java]
        setContent { InterfaceRotas(modelo, onGps = { solicitarGps(false) }, onIniciar = { solicitarGps(true) }, onParar = { if(modelo.estado.value.navegacao?.chegou==true)modelo.parar()else modelo.solicitarCancelamento() }) }
        tratarIntent(intent)
        if(savedInstanceState==null && !SessaoNavegacao.ativa.value)solicitarGps(false)
    }
    override fun onNewIntent(intent:Intent){super.onNewIntent(intent);setIntent(intent);tratarIntent(intent)}
    private fun tratarIntent(intent:Intent){
        if(intent.getBooleanExtra("confirmar_cancelamento",false) && intent.getStringExtra("rota_id")==SessaoNavegacao.rota?.id)modelo.solicitarCancelamento()
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
    private fun solicitarGps(iniciar: Boolean) {
        if(iniciar && modelo.estado.value.navegando)return
        val tem = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (tem) { localizar(); if (iniciar) pedirVozEIniciar() }
        else { iniciarDepoisDaPermissao = iniciar; permissoesLocalizacao.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    }
    private fun pedirNotificacaoEIniciar() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissaoNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        else iniciarServico()
    }
    private fun iniciarServico(){modelo.iniciar()}
    private fun localizar() {
        NavegacaoService.solicitarLocalizacao(this) { leitura ->
            if (leitura != null) modelo.atualizarPosicao(leitura.ponto,leitura.precisaoMetros,leitura.instante)
            else Toast.makeText(this, "Localização indisponível. Ative o GPS ou digite a origem.", Toast.LENGTH_LONG).show()
        }
    }
}
