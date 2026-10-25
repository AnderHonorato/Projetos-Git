package br.com.ander.rotas

import org.junit.Assert.*
import org.junit.Test

class MotorNavegacaoTest {
    private val a = Ponto(0.0, 0.0)
    private val meio = Ponto(0.0, 0.005)
    private val b = Ponto(0.0, 0.01)
    private fun etapa(pontos: List<Ponto> = listOf(a, b), modo: String = "WALK") = Etapa(
        modo = modo, origem = "A", destino = "B", inicio = 1000, fim = 2000, pontos = pontos)
    private fun rota(etapas: List<Etapa> = listOf(etapa()), id: String = "ida") = Rota(
        id, Endereco("A", a), Endereco("B", b), etapas)
    @Test fun distanciaGeodesicaEmMetros() {
        assertEquals(1111.95, MotorNavegacao.distancia(a, b), 0.1)
        assertEquals(0.0, MotorNavegacao.distancia(a, a), 0.001)
    }
    @Test fun medeProgressoNaPolilinhaNaoEmLinhaRetaAteODestino() {
        val curva = rota(listOf(etapa(listOf(a, Ponto(0.01, 0.0), Ponto(0.01, 0.01)))))
        val estado = MotorNavegacao().atualizar(curva, Ponto(0.005, 0.0), 5f)
        assertEquals(1667.9, estado.metrosAteFim, 1.0); assertFalse(estado.desvio)
    }
    @Test fun ruidoIsoladoNaoProduzDesvioNemAlerta() {
        val motor = MotorNavegacao(); val rota = rota()
        assertFalse(motor.atualizar(rota, Ponto(0.003, 0.005), 5f).desvio)
        assertFalse(motor.atualizar(rota, meio, 5f).desvio)
        assertFalse(motor.atualizar(rota, Ponto(0.003, 0.005), 5f).alertar)
    }
    @Test fun gpsRuimNaoContaParaDesvioOuChegada() {
        val motor = MotorNavegacao(); val rota = rota()
        repeat(6) { val e = motor.atualizar(rota, Ponto(0.003, 0.005), 150f); assertFalse(e.desvio); assertFalse(e.alertar) }
        repeat(6) { assertFalse(motor.atualizar(rota, b, 150f).chegou) }
    }
    @Test fun desvioExigeTresLeiturasECooldownPermiteNovoAviso() {
        var tempo = 0L; val motor = MotorNavegacao { tempo }; val rota = rota(); val fora = Ponto(0.003, 0.005)
        assertFalse(motor.atualizar(rota, fora, 5f).desvio); assertFalse(motor.atualizar(rota, fora, 5f).desvio)
        val terceiro = motor.atualizar(rota, fora, 5f); assertTrue(terceiro.desvio); assertTrue(terceiro.alertar)
        tempo = 30_000; assertFalse(motor.atualizar(rota, fora, 5f).alertar)
        tempo = 90_000; assertTrue(motor.atualizar(rota, fora, 5f).alertar)
    }
    @Test fun retornoAoTrajetoExigeDuasObservacoes() {
        val motor = MotorNavegacao(); val rota = rota()
        repeat(3) { motor.atualizar(rota, Ponto(0.003, 0.005), 5f) }
        assertTrue(motor.atualizar(rota, meio, 5f).desvio); assertFalse(motor.atualizar(rota, meio, 5f).desvio)
    }
    @Test fun horariosPassadosNaoConfirmamEmbarqueNemMudamEtapa() {
        val motor = MotorNavegacao { Long.MAX_VALUE / 2 }; val rota = rota(listOf(etapa(), etapa(listOf(b, a), "BUS")))
        repeat(8) { val e = motor.atualizar(rota, b, 5f); assertEquals(0, e.etapaAtual); assertFalse(e.chegou) }
        assertEquals(1, motor.avancarEtapa(rota).etapaAtual); assertEquals(0, motor.voltarEtapa(rota).etapaAtual)
    }
    @Test fun chegadaExigeTresLeiturasPrecisasNaUltimaEtapa() {
        val motor = MotorNavegacao(); val rota = rota()
        assertFalse(motor.atualizar(rota, b, 5f).chegou); assertFalse(motor.atualizar(rota, b, 5f).chegou)
        assertTrue(motor.atualizar(rota, b, 5f).chegou)
    }
    @Test fun avisoAntesDeDescerExigeRepeticaoENaoRepeteNaEtapa() {
        var tempo = 0L; val motor = MotorNavegacao { tempo }; val rota = rota(listOf(etapa(modo = "BUS"))); val proximo = Ponto(0.0, 0.008)
        assertFalse(motor.atualizar(rota, proximo, 5f).alertar)
        val aviso = motor.atualizar(rota, proximo, 5f); assertTrue(aviso.alertar); assertTrue(aviso.mensagem.contains("descer"))
        tempo = 180_000; assertFalse(motor.atualizar(rota, proximo, 5f).alertar)
    }
    @Test fun idaEVoltaUtilizamSentidoDaGeometria() {
        val ida = rota(); val volta = rota(listOf(etapa(listOf(b, a))), "volta"); val motor = MotorNavegacao(); val p = Ponto(0.0, 0.002)
        assertEquals(889.56, motor.atualizar(ida, p, 5f).metrosAteFim, 0.2)
        assertEquals(222.39, motor.atualizar(volta, p, 5f).metrosAteFim, 0.2)
        assertFalse(motor.atualizar(volta, p, 5f).chegou)
    }
    @Test fun novaRotaLimpaDesvioEProgressoAnterior() {
        val motor = MotorNavegacao(); repeat(3) { motor.atualizar(rota(), Ponto(0.003, 0.005), 5f) }
        val nova = rota(id = "nova"); assertFalse(motor.atualizar(nova, meio, 5f).desvio)
        assertEquals(0, motor.definirEtapa(nova, 99).etapaAtual)
    }
    @Test fun geometriaInsuficienteECoordenadasInvalidasNaoGeramFalsoDestino() {
        val motor = MotorNavegacao(); val rota = rota(listOf(etapa(listOf(a))))
        repeat(4) { val e = motor.atualizar(rota, a, 5f); assertFalse(e.chegou); assertFalse(e.alertar) }
        assertFalse(motor.atualizar(rota(), Ponto(Double.NaN, 0.0), 5f).chegou)
        assertFalse(motor.atualizar(rota(), b, Float.NaN).chegou)
    }
    @Test fun cruzaAntimeridianoPeloArcoCurto() {
        val rota = rota(listOf(etapa(listOf(Ponto(0.0, 179.99), Ponto(0.0, -179.99)))))
        val estado = MotorNavegacao().atualizar(rota, Ponto(0.0, 180.0), 5f)
        assertEquals(1111.95, estado.metrosAteFim, 0.5); assertFalse(estado.desvio)
    }
}
