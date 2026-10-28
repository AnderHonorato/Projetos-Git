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
    val origem: Endereco = ENDERECO_VAZIO, val destino: Endereco = ENDERECO_VAZIO,
    val textoOrigem: String = "", val textoDestino: String = "",
    val textoBusca: String = "", val erroBusca: String? = null, val buscaConcluida: Boolean = false, val buscaParaParada: Boolean = false,
    val paradasDesejadas: List<Endereco> = emptyList(), val origemAutomatica: Boolean = true,
    val confirmarCancelamento: Boolean = false,
    val quando: Long = System.currentTimeMillis(), val horarioFuturo: Boolean = false,
    val conectado: Boolean = false, val resultadosBusca: List<Endereco> = emptyList(),
    val buscaParaOrigem: Boolean = true, val buscando: Boolean = false, val calculando: Boolean = false,
    val rotas: List<Rota> = emptyList(), val rotaSelecionada: Rota? = null,
    val navegando: Boolean = false, val navegacao: EstadoNavegacao? = null,
    val posicao: Ponto? = null, val precisaoMetros: Float? = null,
    val favoritos: List<Endereco> = emptyList(), val viagensFavoritas: List<ViagemFavorita> = emptyList(),
    val historico: List<Endereco> = emptyList(), val salvas: List<Rota> = emptyList(),
    val temaEscuro: Boolean = false, val erro: String? = null)

class RotasViewModel @JvmOverloads constructor(application: Application,
    private val fonteSugestoes: (suspend (String,Ponto?) -> List<Endereco>)? = null) : AndroidViewModel(application) {
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
                textoOrigem = rota.origem.nome, textoDestino = rota.destino.nome, paradasDesejadas=rota.paradasDesejadas, origemAutomatica=false, navegando=true,
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
    private fun podePlanejar():Boolean {
        if(!_estado.value.navegando && !SessaoNavegacao.ativa.value && SessaoNavegacao.rota==null)return true
        _estado.update{it.copy(erro="Conclua ou cancele a viagem atual antes de alterar destinos e paradas.")};return false
    }
    fun prepararBusca(paraOrigem:Boolean,parada:Boolean=false){
        if(!podePlanejar())return
        busca?.cancel();_estado.update{it.copy(buscaParaOrigem=paraOrigem,buscaParaParada=parada,textoBusca="",resultadosBusca=emptyList(),buscando=false,erro=null,erroBusca=null,buscaConcluida=false)}
    }
    fun fecharBusca(){busca?.cancel();_estado.update{it.copy(buscando=false,resultadosBusca=emptyList(),buscaParaParada=false,erroBusca=null,buscaConcluida=false)}}
    fun editarTexto(texto:String,paraOrigem:Boolean){
        if(!podePlanejar())return
        busca?.cancel();val consulta=texto.take(240);val locais=listaLocais(consulta)
        _estado.update{it.copy(textoBusca=consulta,buscaParaOrigem=paraOrigem,buscando=consulta.trim().length>=2,
            resultadosBusca=if(consulta.trim().length<2)locais else (locais+it.resultadosBusca).distinctBy{e->e.ponto}.take(8),
            erro=null,erroBusca=null,buscaConcluida=false)}
        if(consulta.trim().length>=2)buscar(consulta,paraOrigem)
    }
    private fun listaLocais(texto:String):List<Endereco>{
        val e=_estado.value;val consulta=texto.trim()
        return (e.favoritos+e.historico).distinctBy{it.ponto}.filter{consulta.isBlank()||it.nome.contains(consulta,true)||it.detalhe.contains(consulta,true)}.take(6)
    }
    fun buscar(texto:String,paraOrigem:Boolean){
        if(!podePlanejar())return
        busca?.cancel();val consulta=texto.trim();if(consulta.length<2)return
        busca=viewModelScope.launch{
            _estado.update{it.copy(buscando=true,buscaParaOrigem=paraOrigem,erroBusca=null,buscaConcluida=false)}
            try{
                delay(300)
                val encontrados=fonteSugestoes?.invoke(consulta,_estado.value.posicao)?:repositorio.buscarEnderecos(consulta,_estado.value.posicao)
                ensureActive();val resultados=(listaLocais(consulta)+encontrados).distinctBy{it.ponto}.take(8)
                _estado.update{it.copy(resultadosBusca=resultados,buscando=false,buscaConcluida=true,
                    erroBusca=if(resultados.isEmpty())"Nenhum endereço encontrado. Tente rua e cidade ou escolha no mapa."else null)}
            }catch(e:CancellationException){throw e
            }catch(e:Exception){
                val locais=listaLocais(consulta)
                _estado.update{it.copy(resultadosBusca=locais,buscando=false,buscaConcluida=true,
                    erroBusca="Não foi possível consultar endereços agora. Verifique a internet e tente novamente.")}
            }
        }
    }
    fun selecionarEndereco(endereco:Endereco,paraOrigem:Boolean){
        if(!podePlanejar())return
        busca?.cancel()
        if(_estado.value.buscaParaParada){
            if(_estado.value.paradasDesejadas.size>=8){_estado.update{it.copy(erro="Escolha até oito paradas intermediárias.")};return}
            invalidarConsulta();_estado.update{it.copy(paradasDesejadas=it.paradasDesejadas+endereco,buscaParaParada=false,resultadosBusca=emptyList(),buscando=false)}
        }else{
            invalidarConsulta();_estado.update{if(paraOrigem)it.copy(origem=endereco,textoOrigem=endereco.nome,origemAutomatica=false,resultadosBusca=emptyList(),buscando=false)
                else it.copy(destino=endereco,textoDestino=endereco.nome,resultadosBusca=emptyList(),buscando=false)}
        }
    }
    fun removerParada(indice:Int){
        if(!podePlanejar() || indice !in _estado.value.paradasDesejadas.indices)return
        invalidarConsulta();_estado.update{it.copy(paradasDesejadas=it.paradasDesejadas.filterIndexed{i,_->i!=indice})}
    }
    fun moverParada(de:Int,para:Int){
        if(!podePlanejar())return
        val lista=_estado.value.paradasDesejadas.toMutableList()
        if(de !in lista.indices || para !in lista.indices || de==para)return
        lista.add(para,lista.removeAt(de));invalidarConsulta();_estado.update{it.copy(paradasDesejadas=lista)}
    }
    fun usarLocalizacaoComoOrigem(){
        if(!podePlanejar())return
        invalidarConsulta();_estado.update{it.copy(origemAutomatica=true,origem=ENDERECO_VAZIO,textoOrigem="")}
    }
    fun definirQuando(instante: Long) { if(!podePlanejar())return; invalidarConsulta(); _estado.update { it.copy(quando = instante, horarioFuturo = true) } }
    fun sairAgora() { if(!podePlanejar())return; invalidarConsulta(); _estado.update { it.copy(quando = System.currentTimeMillis(), horarioFuturo = false) } }
    fun calcular() {
        if(!podePlanejar())return
        val consulta = _estado.value
        if (consulta.origem.nome.isBlank() || consulta.destino.nome.isBlank() || consulta.textoOrigem != consulta.origem.nome || consulta.textoDestino != consulta.destino.nome) {
            _estado.update { it.copy(erro = "Busque e selecione os endereços antes de calcular a viagem.") }; return
        }
        if (!MotorNavegacao.valido(consulta.origem.ponto) || !MotorNavegacao.valido(consulta.destino.ponto)) {
            _estado.update { it.copy(erro = "Um dos endereços não possui coordenadas válidas.") }; return
        }
        val partida = if (consulta.horarioFuturo) consulta.quando else System.currentTimeMillis()
        calculo?.cancel()
        calculo = viewModelScope.launch {
            _estado.update { it.copy(calculando = true, erro = null, rotas = emptyList(), rotaSelecionada = null, navegacao = null, quando = partida) }
            try {
                val rotas = repositorio.calcularComParadas(consulta.origem,consulta.destino,consulta.paradasDesejadas,partida).map { segurancaSnapshot(it) }
                _estado.update { it.copy(calculando = false, rotas = rotas, rotaSelecionada = rotas.firstOrNull(),
                    erro = if (rotas.isEmpty()) "Nenhuma viagem disponível nesta data e região. Tente outro horário." else null) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { _estado.update { it.copy(calculando = false, erro = e.message ?: "Não foi possível calcular. As viagens salvas continuam disponíveis offline.") } }
        }
    }
    /** Mantém roteiro e GPS existentes enquanto o novo planejamento não tiver sucesso. */
    fun recalcular() {
        if(!podePlanejar())return
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
        if(!podePlanejar())return
        invalidarConsulta(); busca?.cancel()
        _estado.update { it.copy(origem = it.destino, destino = it.origem, textoOrigem = it.textoDestino,
            textoDestino = it.textoOrigem, origemAutomatica=false, paradasDesejadas=it.paradasDesejadas.reversed(), resultadosBusca = emptyList(), buscando = false) }
    }
    fun selecionarRota(rota: Rota) {
        if(!podePlanejar())return
        calculo?.cancel()
        _estado.update { it.copy(rotaSelecionada = rota, origem = rota.origem, destino = rota.destino,
            textoOrigem = rota.origem.nome, textoDestino = rota.destino.nome, quando = rota.inicio, origemAutomatica=false,paradasDesejadas=rota.paradasDesejadas,
            rotas = if (it.rotas.any { candidata -> candidata.id == rota.id }) it.rotas else listOf(rota),
            navegacao = null, calculando = false, erro = null) }
    }
    fun abrirRotaSalva(rota: Rota) {
        if(!podePlanejar())return
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
        if(!podePlanejar())return
        busca?.cancel();calculo?.cancel()
        val rota = _estado.value.rotaSelecionada ?: return
        if (rota.etapas.isEmpty()) { _estado.update { it.copy(erro = "Esta rota não possui etapas para acompanhar.") }; return }
        persistir { repositorio.salvarRota(rota) }
        _estado.update{it.copy(navegando=true,navegacao=motor.iniciar(rota),erro=null,resultadosBusca=emptyList(),buscando=false)}
        try{NavegacaoService.iniciar(getApplication(),rota)}catch(e:Exception){NavegacaoService.parar(getApplication());_estado.update{it.copy(navegando=false,navegacao=null,erro="Não foi possível iniciar. Confira o GPS e tente novamente.")}}
    }
    fun solicitarCancelamento(){if(_estado.value.navegando)_estado.update{it.copy(confirmarCancelamento=true)}else if(_estado.value.navegacao?.chegou==true)parar()}
    fun manterViagem(){_estado.update{it.copy(confirmarCancelamento=false)}}
    fun parar(){busca?.cancel();calculo?.cancel();NavegacaoService.parar(getApplication());_estado.update{it.copy(navegando=false,navegacao=null,rotaSelecionada=null,rotas=emptyList(),confirmarCancelamento=false,calculando=false,buscando=false)}}
    fun atualizarPosicao(ponto: Ponto, precisao: Float, instante: Long = System.currentTimeMillis()) {
        if (!MotorNavegacao.valido(ponto)) return
        ultimaPosicaoEm = instante
        // Progresso calculado somente pelo serviço: cada leitura conta uma vez.
        _estado.update{
            val usar=it.origemAutomatica && it.origem.nome.isBlank() && !it.navegando && precisao.isFinite() && precisao>0f && precisao<=100f
            val origem=if(usar)Endereco("Minha localização",ponto,"GPS · precisão aproximada ±${precisao.toInt()} m · você pode editar")else it.origem
            it.copy(posicao=ponto,precisaoMetros=precisao,origem=origem,textoOrigem=origem.nome)
        }
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
            repositorio.salvarViagemFavorita(ViagemFavorita(nome.trim(), atual.origem, atual.destino,atual.paradasDesejadas))
            atual.rotaSelecionada?.let { repositorio.salvarRota(it) }
        }
    }
    fun removerViagem(viagem: ViagemFavorita) = persistir { repositorio.removerViagemFavorita(viagem) }
    fun salvarRota() { _estado.value.rotaSelecionada?.let { rota -> persistir { repositorio.salvarRota(rota) } } }
    fun abrirViagem(viagem: ViagemFavorita, invertida: Boolean = false) {
        if(!podePlanejar())return
        invalidarConsulta()
        val origem = if (invertida) viagem.destino else viagem.origem; val destino = if (invertida) viagem.origem else viagem.destino
        _estado.update { it.copy(origem = origem, destino = destino, textoOrigem = origem.nome, textoDestino = destino.nome,
            quando = System.currentTimeMillis(), horarioFuturo = false, resultadosBusca = emptyList(), origemAutomatica=false,paradasDesejadas=if(invertida)viagem.paradasDesejadas.reversed()else viagem.paradasDesejadas) }
    }
    fun mudarTema() { val escuro = !_estado.value.temaEscuro; preferencias.edit().putBoolean("escuro", escuro).apply(); _estado.update { it.copy(temaEscuro = escuro) } }
    fun obterGeocodificador(): String = repositorio.obterGeocodificador()
    fun configurarGeocodificador(url: String) { busca?.cancel(); repositorio.configurarGeocodificador(url); _estado.update { it.copy(buscando = false, resultadosBusca = emptyList()) } }
    fun limparErro() { _estado.update { it.copy(erro = null) } }
    private fun invalidarConsulta() { calculo?.cancel(); _estado.update { it.copy(rotas = emptyList(), rotaSelecionada = null, navegacao = null, calculando = false, erro = null) } }
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
