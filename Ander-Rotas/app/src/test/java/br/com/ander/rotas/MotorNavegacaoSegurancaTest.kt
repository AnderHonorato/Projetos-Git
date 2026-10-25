package br.com.ander.rotas

import org.junit.Assert.*
import org.junit.Test

/** Leituras rejeitadas interrompem, e nunca confirmam, os alertas. */
class MotorNavegacaoSegurancaTest {
    private val inicio = Ponto(-23.5, -46.6)
    private val fim = Ponto(-23.5, -46.59)
    private val fora = Ponto(-23.497, -46.595)
    private fun rota(modo: String = "WALK", quantidade: Int = 1) = Rota(
        "seguranca", Endereco("A", inicio), Endereco("B", fim),
        List(quantidade) {
            Etapa(modo, origem = "A", destino = "B", inicio = 0, fim = 60_000,
                pontos = listOf(inicio, fim))
        }
    )

    @Test fun leituraImprecisaInterrompeSequenciaQueConfirmariaDesvio() {
        val motor = MotorNavegacao(); val rota = rota()
        repeat(2) { assertFalse(motor.atualizar(rota, fora, 5f).desvio) }
        assertFalse(motor.atualizar(rota, fora, 46f).alertar)
        repeat(2) { assertFalse(motor.atualizar(rota, fora, 5f).desvio) }
        assertTrue(motor.atualizar(rota, fora, 5f).desvio)
    }

    @Test fun leituraImprecisaInterrompeSequenciaQueConfirmariaChegada() {
        val motor = MotorNavegacao(); val rota = rota()
        repeat(2) { assertFalse(motor.atualizar(rota, fim, 5f).chegou) }
        assertFalse(motor.atualizar(rota, fim, 100f).chegou)
        repeat(2) { assertFalse(motor.atualizar(rota, fim, 5f).chegou) }
        assertTrue(motor.atualizar(rota, fim, 5f).chegou)
    }

    @Test fun precisaoAusenteInfinitaOuNegativaNuncaConfirmaAlerta() {
        for (precisao in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val motor = MotorNavegacao(); val rota = rota("BUS")
            repeat(5) {
                val estado = motor.atualizar(rota, fim, precisao)
                assertFalse("precisao=$precisao", estado.alertar)
                assertFalse("precisao=$precisao", estado.chegou)
                assertFalse("precisao=$precisao", estado.desvio)
            }
        }
    }

    @Test fun indicesManuaisFicamDentroDasEtapasESemPularEtapaAutomaticamente() {
        val motor = MotorNavegacao(); val rota = rota(quantidade = 3)
        assertEquals(0, motor.definirEtapa(rota, Int.MIN_VALUE).etapaAtual)
        assertEquals(2, motor.definirEtapa(rota, Int.MAX_VALUE).etapaAtual)
        assertEquals(2, motor.avancarEtapa(rota).etapaAtual)
        repeat(4) { motor.voltarEtapa(rota) }
        repeat(5) {
            val estado = motor.atualizar(rota, fim, 5f)
            assertEquals(0, estado.etapaAtual); assertFalse(estado.chegou)
        }
    }

    @Test fun trocarEtapaLimpaConfirmacoesDeChegadaDaEtapaAnterior() {
        val motor = MotorNavegacao(); val rota = rota(quantidade = 2)
        repeat(4) { motor.atualizar(rota, fim, 5f) }
        motor.avancarEtapa(rota)
        repeat(2) { assertFalse(motor.atualizar(rota, fim, 5f).chegou) }
        assertTrue(motor.atualizar(rota, fim, 5f).chegou)
    }

    @Test fun rotaSemEtapasNaoCausaCrashNemAlertaEmComandosManuais() {
        val motor = MotorNavegacao(); val vazia = rota().copy(etapas = emptyList())
        val estados = listOf(motor.iniciar(vazia), motor.avancarEtapa(vazia),
            motor.voltarEtapa(vazia), motor.definirEtapa(vazia, 10), motor.atualizar(vazia, fim, 5f))
        estados.forEach {
            assertEquals(0, it.etapaAtual); assertFalse(it.alertar); assertFalse(it.chegou)
            assertEquals(0.0, it.metrosAteFim, 0.0)
        }
    }

    @Test fun chegadaAposAvisoDeDescidaNotificaUmaVezMesmoDentroDoCooldown() {
        var tempo = 0L
        val motor = MotorNavegacao { tempo }; val rota = rota("BUS")
        val antesDaParada = Ponto(-23.5, -46.592)
        motor.atualizar(rota, antesDaParada, 5f)
        assertTrue(motor.atualizar(rota, antesDaParada, 5f).alertar)
        tempo = 20_000L
        repeat(2) { assertFalse(motor.atualizar(rota, fim, 5f).chegou) }
        val chegada = motor.atualizar(rota, fim, 5f)
        assertTrue(chegada.chegou)
        assertTrue("Aviso de descida recente não pode silenciar a chegada", chegada.alertar)
        tempo = 180_000L
        repeat(3) { assertFalse(motor.atualizar(rota, fim, 5f).alertar) }
    }
}
