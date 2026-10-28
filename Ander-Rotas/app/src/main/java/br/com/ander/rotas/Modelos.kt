package br.com.ander.rotas

import java.io.Serializable

data class Ponto(val latitude: Double, val longitude: Double): Serializable
data class Endereco(val nome: String, val ponto: Ponto, val detalhe: String = ""): Serializable
enum class TipoDado { PROGRAMADO, PREVISAO, TEMPO_REAL, INDISPONIVEL }
data class Etapa(
    val modo: String,
    val linha: String = "",
    val sentido: String = "",
    val origem: String,
    val destino: String,
    val inicio: Long,
    val fim: Long,
    val pontos: List<Ponto>,
    val paradas: List<Endereco> = emptyList(),
    val tipoDado: TipoDado = TipoDado.PROGRAMADO,
    val agencia: String = "",
    val distanciaMetros: Double = 0.0
): Serializable
data class Rota(
    val id: String,
    val origem: Endereco,
    val destino: Endereco,
    val etapas: List<Etapa>,
    val consultadaEm: Long = System.currentTimeMillis(),
    val fonte: String = "Transitous · fontes GTFS por região",
    val aviso: String = "Os horários podem mudar. Confira o sentido antes de embarcar.",
    val paradasDesejadas: List<Endereco> = emptyList()
): Serializable {
    val inicio: Long get() = etapas.firstOrNull()?.inicio ?: consultadaEm
    val fim: Long get() = etapas.lastOrNull()?.fim ?: consultadaEm
    val duracaoMinutos: Int get() = ((fim-inicio)/60000).toInt().coerceAtLeast(0)
}
data class ViagemFavorita(val nome: String, val origem: Endereco, val destino: Endereco): Serializable

// Centros de vias retornados pelo Nominatim; os números de porta não estão confirmados.
val ORIGEM_INICIAL = Endereco("Rua Silveira Tavares, 61", Ponto(-23.4771045,-46.5668804), "Parque Edu Chaves, São Paulo · número não localizado; ponto aproximado da rua")
val DESTINO_INICIAL = Endereco("Rua Seis, 65", Ponto(-23.3410193,-46.8413569), "Jordanésia, Cajamar · número não localizado; ponto aproximado da rua")

val ENDERECO_VAZIO = Endereco("", Ponto(Double.NaN, Double.NaN))
