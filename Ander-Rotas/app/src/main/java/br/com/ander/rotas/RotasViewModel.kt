package br.com.ander.rotas

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class EstadoRotas(
    val origem: Endereco = ORIGEM_INICIAL, val destino: Endereco = DESTINO_INICIAL,
    val textoOrigem: String = ORIGEM_INICIAL.nome, val textoDestino: String = DESTINO_INICIAL.nome,
    val quando: Long = System.currentTimeMillis(), val horarioFuturo: Boolean = false,
    val conectado: Boolean = false, val resultadosBusca: List<Endereco> = emptyList(),
    val buscaParaOrigem: Boolean = true, val buscando: Boolean = false, val calculando: Boolean = false,
    val rotas: List<Rota> = emptyList(), val rotaSelecionada: Rota? = null,
    val navegando: Boolean = false, val navegacao: EstadoNavegacao? = null,
    val posicao: Ponto? = null, val precisaoMetros: Float? = null,
    val favoritos: List<Endereco> = emptyList(), val viagensFavoritas: List<ViagemFavorita> = emptyList(),
    val historico: List<Endereco> = emptyList(), val salvas: List<Rota> = emptyList(),
    val temaEscuro: Boolean = false, val erro: String? = null)

class RotasViewModel(application: Application) : AndroidViewModel(application) {
    private val repositorio = RepositorioRotas(application)
    private val preferencias = application.getSharedPreferences("aparencia", 0)
    private val motor = MotorNavegacao()
    private val conectividade = application.getSystemService(ConnectivityManager::class.java)
    private val _estado = MutableStateFlow(EstadoRotas(temaEscuro = preferencias.getBoolean("escuro", false), conectado = temInternet()))
    val estado: StateFlow<EstadoRotas> = _estado.asStateFlow()
    private var busca: Job? = null
    private var calculo: Job? = null
    private var ultimaPosicaoEm = 0L
    private var callbackRegistrado = false
    private val callbackRede = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = atualizarConexao()
        override fun onLost(network: Network) = atualizarConexao()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = atualizarConexao()
    }
    init {
        callbackRegistrado = runCatching { conectividade.registerDefaultNetworkCallback(callbackRede); true }.getOrDefault(false)
        SessaoNavegacao.rota?.takeIf { SessaoNavegacao.ativa.value }?.let { rota ->
            _estado.update { it.copy(rotaSelecionada = rota, origem = rota.origem, destino = rota.destino,
                textoOrigem = rota.origem.nome, textoDestino = rota.destino.nome, navegando = true,
                navegacao = SessaoNavegacao.estado.value) }
        }
        viewModelScope.launch(Dispatchers.IO) { atualizarSalvos() }
        viewModelScope.launch {
            SessaoNavegacao.localizacoes.collect { atualizarPosicao(it.ponto, it.precisaoMetros, it.instante) }
        }
        viewModelScope.launch {
            SessaoNavegacao.estado.collect { nav ->
                if (nav != null && _estado.value.rotaSelecionada != null)
                    _estado.update { it.copy(navegacao = nav, navegando = SessaoNavegacao.ativa.value) }
            }
        }
        viewModelScope.launch { SessaoNavegacao.erro.collect { erro -> if (erro != null) _estado.update { it.copy(erro = erro) } } }
        viewModelScope.launch {
            SessaoNavegacao.ativa.collect { ativa ->
                _estado.update { it.copy(navegando = ativa, rotaSelecionada = if (ativa) SessaoNavegacao.rota ?: it.rotaSelecionada else it.rotaSelecionada) }
            }
        }
    }
    fun editarTexto(texto: String, paraOrigem: Boolean) {
        val atual = _estado.value
        val anterior = if (paraOrigem) atual.textoOrigem else atual.textoDestino
        if (texto != anterior && (atual.rotaSelecionada != null || atual.calculando)) invalidarConsulta()
        busca?.cancel()
        _estado.update { if (paraOrigem) it.copy(textoOrigem = texto, buscando = false, resultadosBusca = emptyList(), erro = null)
            else it.copy(textoDestino = texto, buscando = false, resultadosBusca = emptyList(), erro = null) }
    }
    /** Somente botão explícito: sem autocomplete de endereços. */
    fun buscar(texto: String, paraOrigem: Boolean) {
        editarTexto(texto, paraOrigem)
        if (texto.trim().length < 3) { _estado.update { it.copy(erro = "Digite pelo menos três caracteres para buscar um endereço.") }; return }
        busca = viewModelScope.launch {
            _estado.update { it.copy(buscando = true, buscaParaOrigem = paraOrigem) }
            try {
                delay(300)
                val encontrados = repositorio.buscarEnderecos(texto.trim())
                _estado.update { it.copy(resultadosBusca = encontrados, buscando = false,
                    erro = if (encontrados.isEmpty()) "Nenhum endereço encontrado. Acrescente cidade e número." else null) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _estado.update { it.copy(buscando = false, erro = e.message ?: "Não foi possível buscar o endereço. Verifique a conexão.") } }
        }
    }
    fun selecionarEndereco(endereco: Endereco, paraOrigem: Boolean) {
        busca?.cancel(); invalidarConsulta()
        _estado.update { if (paraOrigem) it.copy(origem = endereco, textoOrigem = endereco.nome, resultadosBusca = emptyList(), buscando = false)
            else it.copy(destino = endereco, textoDestino = endereco.nome, resultadosBusca = emptyList(), buscando = false) }
    }
    fun definirQuando(instante: Long) { invalidarConsulta(); _estado.update { it.copy(quando = instante, horarioFuturo = true) } }
    fun sairAgora() { invalidarConsulta(); _estado.update { it.copy(quando = System.currentTimeMillis(), horarioFuturo = false) } }
    fun calcular() {
        val consulta = _estado.value
        if (consulta.textoOrigem != consulta.origem.nome || consulta.textoDestino != consulta.destino.nome) {
            _estado.update { it.copy(erro = "Busque e selecione os endereços antes de calcular a viagem.") }; return
        }
        if (!MotorNavegacao.valido(consulta.origem.ponto) || !MotorNavegacao.valido(consulta.destino.ponto)) {
            _estado.update { it.copy(erro = "Um dos endereços não possui coordenadas válidas.") }; return
        }
        val partida = if (consulta.horarioFuturo) consulta.quando else System.currentTimeMillis()
        parar(); calculo?.cancel()
        calculo = viewModelScope.launch {
            _estado.update { it.copy(calculando = true, erro = null, rotas = emptyList(), rotaSelecionada = null, navegacao = null, quando = partida) }
            try {
                val rotas = repositorio.calcular(consulta.origem, consulta.destino, partida).map { segurancaSnapshot(it) }
                _estado.update { it.copy(calculando = false, rotas = rotas, rotaSelecionada = rotas.firstOrNull(),
                    erro = if (rotas.isEmpty()) "Nenhuma viagem disponível nesta data e região. Tente outro horário." else null) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _estado.update { it.copy(calculando = false, erro = e.message ?: "Não foi possível calcular. As viagens salvas continuam disponíveis offline.") } }
        }
    }
    /** Mantém roteiro e GPS existentes enquanto o novo planejamento não tiver sucesso. */
    fun recalcular() {
        val atual = _estado.value; val ponto = atual.posicao; val precisao = atual.precisaoMetros; val agora = System.currentTimeMillis()
        if (ponto == null || !MotorNavegacao.valido(ponto) || precisao == null || !precisao.isFinite() ||
            precisao <= 0f || precisao > 45f || agora - ultimaPosicaoEm !in 0L..30_000L) {
            _estado.update { it.copy(erro = "Atualize sua localização e aguarde GPS com precisão de até 45 m para recalcular. O roteiro atual continua disponível.") }; return
        }
        val origem = Endereco("Sua localização", ponto, "GPS: precisão aproximada de ${precisao.toInt()} m")
        calculo?.cancel()
        calculo = viewModelScope.launch {
            _estado.update { it.copy(calculando = true, erro = null) }
            try {
                val novas = repositorio.calcular(origem, atual.destino, agora)
                if (novas.isEmpty() || novas.all { it.aviso.startsWith("OFFLINE:") }) {
                    _estado.update { it.copy(calculando = false, erro = "Não foi possível obter um novo trajeto agora. O roteiro e o GPS atuais foram mantidos.") }
                } else {
                    parar()
                    _estado.update { it.copy(origem = origem, textoOrigem = origem.nome, destino = atual.destino,
                        textoDestino = atual.destino.nome, quando = agora, horarioFuturo = false,
                        rotas = novas, rotaSelecionada = novas.first(), calculando = false,
                        erro = "Novo trajeto calculado da sua localização. Confira as etapas e toque em Seguir viagem com GPS para iniciar.") }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _estado.update { it.copy(calculando = false, erro = "Não foi possível recalcular. O roteiro e o GPS atuais foram mantidos. " + (e.message ?: "Confira a conexão e tente novamente.")) } }
        }
    }
    fun inverter() {
        invalidarConsulta(); busca?.cancel()
        _estado.update { it.copy(origem = it.destino, destino = it.origem, textoOrigem = it.textoDestino,
            textoDestino = it.textoOrigem, resultadosBusca = emptyList(), buscando = false) }
    }
    fun selecionarRota(rota: Rota) {
        calculo?.cancel(); parar()
        _estado.update { it.copy(rotaSelecionada = rota, origem = rota.origem, destino = rota.destino,
            textoOrigem = rota.origem.nome, textoDestino = rota.destino.nome, quando = rota.inicio,
            rotas = if (it.rotas.any { candidata -> candidata.id == rota.id }) it.rotas else listOf(rota),
            navegacao = null, calculando = false, erro = null) }
    }
    fun abrirRotaSalva(rota: Rota) {
        selecionarRota(segurancaSnapshot(rota, true))
        _estado.update { it.copy(horarioFuturo = false) }
    }
    /** Nunca apresenta uma previsão antiga recuperada do disco como dado vivo. */
    private fun segurancaSnapshot(rota: Rota, salva: Boolean = false): Rota {
        if (!salva && !rota.aviso.startsWith("OFFLINE:")) return rota
        val aviso = if (salva) "VIAGEM SALVA: horários e previsões são da consulta original, sem atualização em tempo real. Calcule novamente antes de sair. " + rota.aviso else rota.aviso
        return rota.copy(aviso = aviso, etapas = rota.etapas.map { etapa ->
            if (etapa.tipoDado == TipoDado.PREVISAO || etapa.tipoDado == TipoDado.TEMPO_REAL) etapa.copy(tipoDado = TipoDado.INDISPONIVEL) else etapa
        })
    }
    fun iniciar() {
        val rota = _estado.value.rotaSelecionada ?: return
        if (rota.etapas.isEmpty()) { _estado.update { it.copy(erro = "Esta rota não possui etapas para acompanhar.") }; return }
        persistir { repositorio.salvarRota(rota) }
        _estado.update { it.copy(navegando = true, navegacao = motor.iniciar(rota), erro = null) }
    }
    fun parar() { NavegacaoService.parar(getApplication()); _estado.update { it.copy(navegando = false, navegacao = null) } }
    fun atualizarPosicao(ponto: Ponto, precisao: Float, instante: Long = System.currentTimeMillis()) {
        if (!MotorNavegacao.valido(ponto)) return
        ultimaPosicaoEm = instante
        // Progresso calculado somente pelo serviço: cada leitura conta uma vez.
        _estado.update { it.copy(posicao = ponto, precisaoMetros = precisao) }
    }
    fun avancarEtapa() = trocarEtapa(true)
    fun voltarEtapa() = trocarEtapa(false)
    private fun trocarEtapa(avancar: Boolean) {
        val atual = _estado.value; val rota = atual.rotaSelecionada ?: return
        if (!atual.navegando) return
        val indice = ((atual.navegacao?.etapaAtual ?: 0) + if (avancar) 1 else -1).coerceIn(0, (rota.etapas.size - 1).coerceAtLeast(0))
        if (SessaoNavegacao.ativa.value) NavegacaoService.atualizarEtapa(getApplication(), indice)
        else _estado.update { it.copy(navegacao = motor.definirEtapa(rota, indice)) }
    }
    fun salvarFavorito(endereco: Endereco) = persistir { repositorio.salvarFavorito(endereco) }
    fun removerFavorito(endereco: Endereco) = persistir { repositorio.removerFavorito(endereco) }
    fun salvarViagem(nome: String) {
        val atual = _estado.value
        if (atual.textoOrigem != atual.origem.nome || atual.textoDestino != atual.destino.nome) {
            _estado.update { it.copy(erro = "Busque e selecione os endereços antes de salvar a viagem.") }; return
        }
        if (nome.isBlank()) { _estado.update { it.copy(erro = "Dê um nome para esta viagem.") }; return }
        persistir {
            repositorio.salvarViagemFavorita(ViagemFavorita(nome.trim(), atual.origem, atual.destino))
            atual.rotaSelecionada?.let { repositorio.salvarRota(it) }
        }
    }
    fun removerViagem(viagem: ViagemFavorita) = persistir { repositorio.removerViagemFavorita(viagem) }
    fun salvarRota() { _estado.value.rotaSelecionada?.let { rota -> persistir { repositorio.salvarRota(rota) } } }
    fun abrirViagem(viagem: ViagemFavorita, invertida: Boolean = false) {
        invalidarConsulta()
        val origem = if (invertida) viagem.destino else viagem.origem; val destino = if (invertida) viagem.origem else viagem.destino
        _estado.update { it.copy(origem = origem, destino = destino, textoOrigem = origem.nome, textoDestino = destino.nome,
            quando = System.currentTimeMillis(), horarioFuturo = false, resultadosBusca = emptyList()) }
    }
    fun mudarTema() { val escuro = !_estado.value.temaEscuro; preferencias.edit().putBoolean("escuro", escuro).apply(); _estado.update { it.copy(temaEscuro = escuro) } }
    fun obterGeocodificador(): String = repositorio.obterGeocodificador()
    fun configurarGeocodificador(url: String) { busca?.cancel(); repositorio.configurarGeocodificador(url); _estado.update { it.copy(buscando = false, resultadosBusca = emptyList()) } }
    fun limparErro() { _estado.update { it.copy(erro = null) } }
    private fun invalidarConsulta() { calculo?.cancel(); parar(); _estado.update { it.copy(rotas = emptyList(), rotaSelecionada = null, navegacao = null, calculando = false, erro = null) } }
    private fun persistir(acao: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try { acao(); atualizarSalvos() } catch (e: Exception) { _estado.update { it.copy(erro = e.message ?: "Não foi possível salvar esta alteração.") } }
        }
    }
    private fun temInternet(): Boolean = runCatching {
        val caps = conectividade.getNetworkCapabilities(conectividade.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)
    private fun atualizarConexao() { _estado.update { it.copy(conectado = temInternet()) } }
    override fun onCleared() { if (callbackRegistrado) runCatching { conectividade.unregisterNetworkCallback(callbackRede) }; super.onCleared() }
    private fun atualizarSalvos() {
        try {
            val favoritos = repositorio.favoritos(); val viagens = repositorio.viagensFavoritas(); val historico = repositorio.historico(); val salvas = repositorio.rotasSalvas()
            _estado.update { it.copy(favoritos = favoritos, viagensFavoritas = viagens, historico = historico, salvas = salvas) }
        } catch (_: Exception) { _estado.update { it.copy(erro = "Não foi possível abrir os dados salvos neste aparelho.") } }
    }
}
