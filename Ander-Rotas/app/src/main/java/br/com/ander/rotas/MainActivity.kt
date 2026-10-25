package br.com.ander.rotas

import android.Manifest
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
            if (iniciarDepoisDaPermissao) pedirNotificacaoEIniciar()
        } else Toast.makeText(this, "Permita a localização para seguir a viagem pelo GPS.", Toast.LENGTH_LONG).show()
        iniciarDepoisDaPermissao = false
    }
    private val permissaoNotificacao = registerForActivityResult(ActivityResultContracts.RequestPermission()) { iniciarServico() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        modelo = ViewModelProvider(this)[RotasViewModel::class.java]
        setContent { InterfaceRotas(modelo, onGps = { solicitarGps(false) }, onIniciar = { solicitarGps(true) }, onParar = { modelo.parar(); NavegacaoService.parar(this) }) }
    }
    private fun solicitarGps(iniciar: Boolean) {
        val tem = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (tem) { localizar(); if (iniciar) pedirNotificacaoEIniciar() }
        else { iniciarDepoisDaPermissao = iniciar; permissoesLocalizacao.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    }
    private fun pedirNotificacaoEIniciar() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissaoNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        else iniciarServico()
    }
    private fun iniciarServico() {
        modelo.estado.value.rotaSelecionada?.let { rota ->
            runCatching { NavegacaoService.iniciar(this, rota); modelo.iniciar() }.onFailure { Toast.makeText(this, "Não foi possível iniciar o acompanhamento. Ative a localização e tente novamente.", Toast.LENGTH_LONG).show() }
        }
    }
    private fun localizar() {
        NavegacaoService.solicitarLocalizacao(this) { leitura ->
            if (leitura != null) modelo.atualizarPosicao(leitura.ponto, leitura.precisaoMetros)
            else Toast.makeText(this, "Localização ainda indisponível. Ative o GPS e tente novamente.", Toast.LENGTH_LONG).show()
        }
    }
}
