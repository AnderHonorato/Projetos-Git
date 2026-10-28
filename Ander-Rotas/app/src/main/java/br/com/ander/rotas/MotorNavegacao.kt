package br.com.ander.rotas

import kotlin.math.*

/** Distâncias em metros da etapa atual; o usuário sempre confirma embarques e etapas. */
enum class AvisoParada { NENHUM, TRES, DOIS, ULTIMO, DESEMBARCAR }

data class EstadoNavegacao(val etapaAtual: Int = 0, val metrosAteFim: Double = 0.0,
    val desvio: Boolean = false, val mensagem: String = "Aguardando localização GPS.",
    val chegou: Boolean = false, val alertar: Boolean = false,
    val paradaAtual: Int = 0, val pontosRestantes: Int? = null,
    val proximaParada: String? = null, val avisoParada: AvisoParada = AvisoParada.NENHUM)

/** Motor sem Android/rede; relógio injetável para reproduzir GPS e cooldown. */
class MotorNavegacao(private val agora: () -> Long = System::currentTimeMillis) {
    private var rotaId: String? = null
    private var etapa = 0
    private var fora = 0
    private var dentro = 0
    private var proximas = 0
    private var emDesvio = false
    private var avisouDescida = false
    private var ultimoAlerta: Long? = null
    private var estado = EstadoNavegacao()
    private var paradaAtual = 0
    private var candidatoParada = -1
    private var repeticoesParada = 0
    private val avisosParadas = mutableSetOf<AvisoParada>()

    fun iniciar(rota: Rota): EstadoNavegacao {
        rotaId = rota.id; etapa = 0; ultimoAlerta = null
        return reiniciarEtapa(rota)
    }
    fun definirEtapa(rota: Rota, indice: Int): EstadoNavegacao {
        if (rotaId != rota.id) iniciar(rota)
        etapa = indice.coerceIn(0, (rota.etapas.size - 1).coerceAtLeast(0))
        return reiniciarEtapa(rota)
    }
    fun avancarEtapa(rota: Rota): EstadoNavegacao {
        if (rotaId != rota.id) iniciar(rota)
        return definirEtapa(rota, etapa + 1)
    }
    fun voltarEtapa(rota: Rota): EstadoNavegacao {
        if (rotaId != rota.id) iniciar(rota)
        return definirEtapa(rota, etapa - 1)
    }
    private fun reiniciarEtapa(rota: Rota): EstadoNavegacao {
        fora = 0; dentro = 0; proximas = 0; emDesvio = false; avisouDescida = false
        paradaAtual=0; candidatoParada=-1; repeticoesParada=0; avisosParadas.clear()
        val atual = rota.etapas.getOrNull(etapa)
        estado = EstadoNavegacao(etapa, atual?.let { comprimento(it.pontos) } ?: 0.0,
            mensagem = if (atual == null) "Rota sem etapas para acompanhar." else "Confirme o sentido e aguarde um GPS preciso.")
        return estado
    }
    fun atualizar(rota: Rota, posicao: Ponto, precisaoMetros: Float): EstadoNavegacao {
        if (rotaId != rota.id) iniciar(rota)
        val atual = rota.etapas.getOrNull(etapa) ?: return estado
        if (!valido(posicao) || !precisaoMetros.isFinite() || precisaoMetros <= 0f || precisaoMetros > 45f) {
            fora = 0; dentro = 0; proximas = 0; repeticoesParada = 0
            estado = estado.copy(alertar = false, avisoParada = AvisoParada.NENHUM, mensagem = "GPS impreciso. Aguarde um local com sinal melhor.")
            return estado
        }
        if (atual.pontos.size < 2 || atual.pontos.any { !valido(it) }) {
            estado = estado.copy(alertar = false, avisoParada = AvisoParada.NENHUM, mensagem = "Etapa sem geometria suficiente. Confira as paradas e avance manualmente.")
            return estado
        }
        val projecao = projetar(posicao, atual.pontos)
        if (projecao.distancia > max(80.0, precisaoMetros * 2.0)) {
            fora++; dentro = 0; proximas = 0
        } else { dentro++; fora = 0 }
        if (fora >= 3) emDesvio = true
        if (dentro >= 2) emDesvio = false
        val transporte = atual.modo.uppercase() !in setOf("WALK", "FOOT", "WALKING", "CAMINHADA", "A_PÉ", "A PE")
        val pertoFim = if(transporte) precisaoMetros<=20f && distancia(posicao,atual.pontos.last())<=15.0 && projecao.restante<=20.0
            else distancia(posicao, atual.pontos.last()) <= 40.0 && projecao.restante <= 65.0
        proximas = if (pertoFim && !emDesvio) proximas + 1 else 0
        val chegou = etapa == rota.etapas.lastIndex && proximas >= 3
        // Não estimar número de paradas a partir dos metros: usar apenas a lista publicada.
        val paradas = atual.paradas.takeIf { transporte && it.size >= 3 && it.all { e -> valido(e.ponto) } }
        val distanciasParadas = paradas?.map { projetar(it.ponto, atual.pontos).restante }
        val contagemValida = distanciasParadas != null && distanciasParadas.zipWithNext().all { (a,b) -> a > b }
        var restantes: Int? = null
        var proxima: String? = null
        var avisoParada = AvisoParada.NENHUM
        if (paradas != null && distanciasParadas != null && contagemValida) {
            if (!emDesvio && dentro >= 1) {
                val candidato = distanciasParadas.indexOfLast { it >= projecao.restante - 15.0 }.coerceAtLeast(0)
                    .coerceAtMost(if (proximas >= 3) paradas.lastIndex else paradas.lastIndex - 1)
                if (candidato == candidatoParada) repeticoesParada++ else { candidatoParada=candidato; repeticoesParada=1 }
                if (repeticoesParada >= 2 || (pertoFim && proximas >= 3)) paradaAtual=max(paradaAtual,candidato)
            } else repeticoesParada=0
            restantes=paradas.lastIndex-paradaAtual
            proxima=paradas.getOrNull(paradaAtual+1)?.nome
            val marco=when {
                !emDesvio && proximas>=3 -> AvisoParada.DESEMBARCAR
                restantes==1 -> AvisoParada.ULTIMO
                restantes==2 -> AvisoParada.DOIS
                restantes==3 -> AvisoParada.TRES
                else -> AvisoParada.NENHUM
            }
            if (!emDesvio && dentro>=2 && (repeticoesParada>=2 || proximas>=3) && marco!=AvisoParada.NENHUM && avisosParadas.add(marco)) avisoParada=marco
        }
        if(transporte && !emDesvio && proximas>=3 && avisosParadas.add(AvisoParada.DESEMBARCAR))avisoParada=AvisoParada.DESEMBARCAR
        val descida = !contagemValida && transporte && !avisouDescida && !emDesvio && dentro >= 2 && projecao.restante <= 350.0
        val instante = agora()
        val podeAlertar = ultimoAlerta?.let { instante - it >= 90_000L } ?: true
        // Chegada é uma transição independente: o aviso anterior de descida não a silencia.
        val alerta = avisoParada != AvisoParada.NENHUM || (chegou && !estado.chegou) || ((emDesvio || descida) && podeAlertar)
        if (alerta) { ultimoAlerta = instante; if (descida) avisouDescida = true }
        val mensagem = when {
            chegou && transporte -> "Você chegou a ${atual.destino}. Confira se o veículo parou neste ponto e desça com segurança."
            chegou -> "Você chegou ao destino. Confira o endereço ao redor."
            emDesvio -> "Você parece fora desta etapa. Confira o mapa e o sentido antes de continuar."
            proximas>=3 && transporte -> "Você chegou a ${atual.destino}. Confira se o veículo parou neste ponto e desça com segurança."
            restantes!=null && restantes>0 -> if(restantes==1) "Próximo ponto: ${atual.destino}. Prepare-se para descer." else "Faltam $restantes pontos para ${atual.destino}. Próximo: $proxima."
            pertoFim && transporte -> "Prepare-se para descer em ${atual.destino}. Confirme a próxima etapa."
            descida || (avisouDescida && projecao.restante <= 350.0) -> "Faltam cerca de ${projecao.restante.toInt()} m para ${atual.destino}. Prepare-se para descer."
            pertoFim -> "Você está perto de ${atual.destino}. Confirme a próxima etapa."
            else -> "Siga para ${atual.destino}. A troca de etapa é manual."
        }
        estado = EstadoNavegacao(etapa, projecao.restante, emDesvio, mensagem, chegou, alerta, paradaAtual, restantes, proxima, avisoParada)
        return estado
    }
    private data class Projecao(val distancia: Double, val restante: Double)
    private fun projetar(ponto: Ponto, linha: List<Ponto>): Projecao {
        val tamanhos = linha.zipWithNext { a, b -> distancia(a, b) }
        var melhor = Double.POSITIVE_INFINITY
        var restante = tamanhos.sum()
        var apos = restante
        for (i in tamanhos.indices) {
            val tamanho = tamanhos[i]; apos -= tamanho
            val a = linha[i]; val b = linha[i + 1]
            val angular = distancia(a, ponto) / RAIO
            val diferenca = rumo(a, ponto) - rumo(a, b)
            val cruzada = asin((sin(angular) * sin(diferenca)).coerceIn(-1.0, 1.0)) * RAIO
            val aoLongo = atan2(sin(angular) * cos(diferenca), cos(angular)) * RAIO
            val progresso = aoLongo.coerceIn(0.0, tamanho)
            val afastamento = when {
                tamanho < 0.01 || aoLongo < 0 -> distancia(a, ponto)
                aoLongo > tamanho -> distancia(b, ponto)
                else -> abs(cruzada)
            }
            if (afastamento < melhor) { melhor = afastamento; restante = apos + tamanho - progresso }
        }
        return Projecao(melhor, restante.coerceAtLeast(0.0))
    }
    companion object {
        private const val RAIO = 6_371_008.8
        fun valido(ponto: Ponto): Boolean = ponto.latitude.isFinite() && ponto.longitude.isFinite() &&
            ponto.latitude in -90.0..90.0 && ponto.longitude in -180.0..180.0
        fun distancia(a: Ponto, b: Ponto): Double {
            val lat1 = Math.toRadians(a.latitude); val lat2 = Math.toRadians(b.latitude)
            val h = sin((lat2 - lat1) / 2).pow(2) + cos(lat1) * cos(lat2) * sin(Math.toRadians(b.longitude - a.longitude) / 2).pow(2)
            return 2 * RAIO * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }
        private fun rumo(a: Ponto, b: Ponto): Double {
            val lat1 = Math.toRadians(a.latitude); val lat2 = Math.toRadians(b.latitude); val lon = Math.toRadians(b.longitude - a.longitude)
            return atan2(sin(lon) * cos(lat2), cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(lon))
        }
        private fun comprimento(pontos: List<Ponto>): Double = pontos.zipWithNext { a, b -> distancia(a, b) }.sum()
    }
}
