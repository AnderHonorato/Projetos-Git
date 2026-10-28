package br.com.ander.rotas
import org.junit.Assert.*
import org.junit.Test
class AlertasParadasTest {
    private val pontos=(0..5).map{Ponto(0.0,it*.002)}
    private val paradas=pontos.mapIndexed{i,p->Endereco("Ponto $i",p)}
    private val rota=Rota("bus",paradas.first(),paradas.last(),listOf(Etapa("BUS",origem="A",destino="B",inicio=0,fim=1000,pontos=pontos,paradas=paradas)))
    @Test fun tresPontosVibraUmaVez() {
        val m=MotorNavegacao();m.atualizar(rota,pontos[2],5f)
        val e=m.atualizar(rota,pontos[2],5f)
        assertEquals(3,e.pontosRestantes);assertEquals(AvisoParada.TRES,e.avisoParada);assertTrue(e.alertar)
        assertEquals(AvisoParada.NENHUM,m.atualizar(rota,pontos[2],5f).avisoParada)
    }
    @Test fun doisPontosNaoSaoSuprimidosPeloCooldown() {
        val m=MotorNavegacao();repeat(2){m.atualizar(rota,pontos[2],5f)}
        m.atualizar(rota,pontos[3],5f)
        val e=m.atualizar(rota,pontos[3],5f)
        assertEquals(2,e.pontosRestantes);assertEquals(AvisoParada.DOIS,e.avisoParada);assertTrue(e.alertar)
    }
    @Test fun ultimoPontoNaoAnunciaChegadaAntesDeAlcancarDesembarque() {
        val m=MotorNavegacao();repeat(2){m.atualizar(rota,pontos[4],5f)}
        assertFalse(m.atualizar(rota,pontos[4],5f).chegou)
        assertFalse(m.atualizar(rota,pontos[5],5f).chegou)
        assertFalse(m.atualizar(rota,pontos[5],5f).chegou)
        val e=m.atualizar(rota,pontos[5],5f)
        assertTrue(e.chegou);assertEquals(AvisoParada.DESEMBARCAR,e.avisoParada);assertTrue(e.mensagem.contains("Desça"))
    }
    @Test fun semParadasNaoInventaContagem() {
        val r=rota.copy(etapas=rota.etapas.map{it.copy(paradas=emptyList())});val m=MotorNavegacao()
        repeat(4){assertNull(m.atualizar(r,pontos[2],5f).pontosRestantes)}
    }
    @Test fun gpsRuimNaoAvancaNemRepeteVoz() {
        val m=MotorNavegacao();repeat(3){m.atualizar(rota,pontos[1],5f)}
        repeat(4){val e=m.atualizar(rota,pontos[4],150f);assertEquals(1,e.paradaAtual);assertEquals(AvisoParada.NENHUM,e.avisoParada)}
    }
}
