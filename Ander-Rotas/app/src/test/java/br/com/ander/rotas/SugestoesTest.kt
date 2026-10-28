package br.com.ander.rotas
import org.junit.Assert.*
import org.junit.Test
class SugestoesTest {
    @Test fun leGeoJsonECoordenadasEmOrdemCorreta(){
        val r=FormatoRotas.lerSugestoes("""{"features":[{"geometry":{"coordinates":[-46.6,-23.5]},"properties":{"name":"Rua A","housenumber":"12","city":"São Paulo"}}]}""")
        assertEquals(1,r.size);assertEquals("Rua A, 12",r[0].nome);assertEquals(Ponto(-23.5,-46.6),r[0].ponto);assertTrue(r[0].detalhe.contains("São Paulo"))
    }
    @Test fun filtraCoordenadasInvalidasENaoDuplicaSugestoes(){
        val r=FormatoRotas.lerSugestoes("""{"features":[{"geometry":{"coordinates":[999,90]},"properties":{"name":"Inválido"}},{"geometry":{"coordinates":[-46.6,-23.5]},"properties":{"name":"Rua A"}},{"geometry":{"coordinates":[-46.6,-23.5]},"properties":{"name":"Rua A"}}]}""")
        assertEquals(1,r.size);assertTrue(r[0].detalhe.contains("Número não confirmado"))
    }
    @Test fun preservaParadasDesejadasAoSalvarEReabrirRota(){
        val a=Endereco("A",Ponto(-23.5,-46.6));val b=Endereco("B",Ponto(-23.4,-46.5));val c=Endereco("C",Ponto(-23.45,-46.55))
        val r=Rota("r",a,b,listOf(Etapa("WALK",origem="A",destino="B",inicio=0,fim=1000,pontos=listOf(a.ponto,b.ponto))),paradasDesejadas=listOf(c))
        assertEquals(r,FormatoRotas.rotaSalva(FormatoRotas.rotaJson(r)))
        val antigo=FormatoRotas.rotaJson(r);antigo.remove("paradasDesejadas");assertTrue(FormatoRotas.rotaSalva(antigo).paradasDesejadas.isEmpty())
    }
}
