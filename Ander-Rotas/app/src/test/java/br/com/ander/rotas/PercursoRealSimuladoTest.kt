package br.com.ander.rotas
import org.junit.Assert.*
import org.junit.Test

/** Posições sobre geometria real, sem equivaler a viagem física ou horário de volta. */
class PercursoRealSimuladoTest {
    private fun rota(): Rota = FormatoRotas.lerPlano(javaClass.getResource("/motis-plan-real.json")!!.readText(), ORIGEM_INICIAL, DESTINO_INICIAL).single()
    @Test fun percorreGeometriaSaoPauloCajamarSemFalsoDesvioEConfirmaChegada() { simular(rota()) }
    @Test fun reproduzGpsNoSentidoInversoSemInferirEmbarqueOuHorario() {
        val ida = rota()
        simular(ida.copy(id = "simulacao-reversa", origem = ida.destino, destino = ida.origem,
            etapas = ida.etapas.reversed().map { it.copy(origem = it.destino, destino = it.origem, pontos = it.pontos.reversed(), paradas = it.paradas.reversed()) }))
    }
    private fun simular(rota: Rota) {
        var tempo = rota.inicio
        val motor = MotorNavegacao { tempo }
        motor.iniciar(rota)
        var leituras = 0
        rota.etapas.forEachIndexed { indice, etapa ->
            motor.definirEtapa(rota, indice)
            etapa.pontos.filterIndexed { i, _ -> i % 19 == 0 }.forEach { ponto ->
                tempo += 5_000
                val estado = motor.atualizar(rota, ponto, 8f)
                assertEquals(indice, estado.etapaAtual)
                assertFalse("Geometria da etapa $indice não deve gerar desvio", estado.desvio)
                if (indice != rota.etapas.lastIndex) assertFalse(estado.chegou)
                leituras++
            }
        }
        assertTrue("Percorrer dezenas de posições reais", leituras > 40)
        val final = rota.etapas.last().pontos.last()
        var chegou = false
        repeat(3) { tempo += 5_000; chegou = motor.atualizar(rota, final, 8f).chegou }
        assertTrue(chegou)
    }
}
