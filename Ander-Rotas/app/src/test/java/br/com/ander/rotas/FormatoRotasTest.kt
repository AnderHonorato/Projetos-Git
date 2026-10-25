package br.com.ander.rotas

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.Instant

class FormatoRotasTest {
    private val from = Endereco("Origem pública de teste", Ponto(-23.4771421, -46.567436))
    private val to = Endereco("Destino público de teste", Ponto(-23.341389, -46.8411005))
    private fun fixture(): String = javaClass.getResource("/motis-plan-real.json")!!.readText()

    @Test fun `plano real preserva geometria sentido paradas e horario UTC`() {
        val route = FormatoRotas.lerPlano(fixture(), from, to).single()
        val bus = route.etapas.single { it.linha == "426" }
        assertEquals("BUS", bus.modo)
        assertEquals(40, bus.paradas.size)
        assertEquals(1087, bus.pontos.size)
        assertEquals(87, route.etapas.first().pontos.size)
        assertTrue(bus.pontos.all { it.latitude in -24.0..-23.0 && it.longitude in -47.0..-46.0 })
        assertTrue(bus.sentido.isNotBlank())
        assertEquals(TipoDado.PROGRAMADO, bus.tipoDado)
        assertEquals(Instant.parse("2026-10-26T09:46:00Z").toEpochMilli(), route.inicio)
        assertEquals(Instant.parse("2026-10-26T12:05:00Z").toEpochMilli(), route.fim)
        assertEquals(from.nome, route.etapas.first().origem)
        assertEquals(to.nome, route.etapas.last().destino)
    }
    @Test fun `salvamento JSON preserva rota para consulta offline`() {
        val route = FormatoRotas.lerPlano(fixture(), from, to).single()
        assertEquals(route, FormatoRotas.rotaSalva(JSONObject(FormatoRotas.rotaJson(route).toString())))
    }
    @Test fun `realTime representa previsao e nao posicao de veiculo`() {
        val json = JSONObject(fixture())
        json.getJSONArray("itineraries").getJSONObject(0).getJSONArray("legs").getJSONObject(1).put("realTime", true)
        assertEquals(TipoDado.PREVISAO, FormatoRotas.lerPlano(json.toString(), from, to).single().etapas[1].tipoDado)
    }
    @Test fun `viagem cancelada nao e oferecida`() {
        val json = JSONObject(fixture())
        json.getJSONArray("itineraries").getJSONObject(0).getJSONArray("legs").getJSONObject(1).put("cancelled", true)
        assertTrue(FormatoRotas.lerPlano(json.toString(), from, to).isEmpty())
    }
    @Test fun `transferencia cancelada real tambem impede oferta da rota`() {
        val raw = javaClass.getResource("/motis-plan-cancelled-transfer-real.json")!!.readText()
        assertTrue(FormatoRotas.lerPlano(raw, from, to).isEmpty())
    }
    @Test fun `geometria ausente nao vira reta ficticia`() {
        val json = JSONObject(fixture())
        json.getJSONArray("itineraries").getJSONObject(0).getJSONArray("legs").getJSONObject(0)
            .put("legGeometry", JSONObject().put("points", "").put("precision", 6).put("length", 0))
        assertTrue(FormatoRotas.lerPlano(json.toString(), from, to).single().etapas.first().pontos.isEmpty())
    }
    @Test fun `numero ausente fica explicitamente aproximado`() {
        val raw = JSONArray().put(JSONObject().put("lat", "-23.3410193").put("lon", "-46.8413569")
            .put("name", "Rua Seis").put("display_name", "Rua Seis, Vila Mariotti, Jordanésia, Cajamar")
            .put("address", JSONObject().put("road", "Rua Seis"))).toString()
        val address = FormatoRotas.lerEnderecos(raw).single()
        assertEquals("Rua Seis", address.nome)
        assertTrue(address.detalhe.contains("número não confirmado"))
    }
    @Test fun `polilinha respeita precisao declarada`() {
        val known = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        assertEquals(Ponto(38.5, -120.2), FormatoRotas.decodificar(known, 5).first())
        assertEquals(Ponto(3.85, -12.02), FormatoRotas.decodificar(known, 6).first())
    }
    @Test(expected = IOException::class) fun `resposta sem contrato nao vira rota inventada`() { FormatoRotas.lerPlano("{}", from, to) }
    @Test(expected = IllegalArgumentException::class) fun `polilinha truncada e rejeitada`() { FormatoRotas.decodificar("_", 6) }
}
