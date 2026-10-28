package br.com.ander.rotas

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.math.pow

/** App-private persistence; no unselected search or GPS is written to disk. */
class RepositorioRotas(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ander_dados_v1", Context.MODE_PRIVATE)
    private val savedFile = File(context.applicationContext.filesDir, "rotas_escolhidas_v1.json")
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS).callTimeout(50, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private val geocodeMemory = LinkedHashMap<String, Pair<Long, List<Endereco>>>()
    private val planningMutex = Mutex()

    suspend fun buscarEnderecos(texto: String, perto: Ponto? = null): List<Endereco> = withContext(Dispatchers.IO) {
        val query=texto.trim().take(240)
        if(query.length<3)return@withContext emptyList()
        val chave="$query:${perto?.latitude}:${perto?.longitude}"
        synchronized(geocodeMemory){geocodeMemory[chave]?.takeIf{System.currentTimeMillis()-it.first<21_600_000}?.let{return@withContext it.second}}
        try {
            val raw=geocodeMutex.withLock {
                val espera=650-(SystemClock.elapsedRealtime()-lastGeocodeAt)
                if(espera>0)delay(espera)
                coroutineContext.ensureActive();lastGeocodeAt=SystemClock.elapsedRealtime()
                val url=obterGeocodificador().toHttpUrl().newBuilder().addQueryParameter("q",query).addQueryParameter("limit","6")
                if(perto!=null && FormatoRotas.valido(perto))url.addQueryParameter("lat",perto.latitude.toString()).addQueryParameter("lon",perto.longitude.toString())
                get(url.build(),600_000)
            }
            val encontrados=FormatoRotas.lerSugestoes(raw)
            synchronized(geocodeMemory){geocodeMemory[chave]=System.currentTimeMillis() to encontrados;while(geocodeMemory.size>32)geocodeMemory.remove(geocodeMemory.keys.first())}
            encontrados
        }catch(e:CancellationException){throw e
        }catch(e:IOException){
            val local=(favoritos()+historico()).distinctBy{it.ponto}.filter{it.nome.contains(query,true)||it.detalhe.contains(query,true)}
            if(local.isEmpty())throw IOException("Não foi possível buscar sugestões. Confira a internet ou escolha um ponto no mapa.",e)
            local
        }
    }
    fun obterGeocodificador():String=prefs.getString("autocomplete_photon",PHOTON)?:PHOTON
    fun configurarGeocodificador(url:String){
        val parsed=url.trim().toHttpUrl()
        require(parsed.isHttps && parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.fragment==null && parsed.query==null){"Informe uma URL HTTPS de busca Photon, sem credenciais ou parâmetros."}
        require(parsed.host!="localhost" && !parsed.host.matches(Regex("[0-9.]+")) && !parsed.host.contains(':')){"Informe um domínio público compatível."}
        require(parsed.toString().length<=512)
        prefs.edit().putString("autocomplete_photon",parsed.toString()).apply()
        synchronized(geocodeMemory){geocodeMemory.clear()}
    }
    suspend fun calcularComParadas(origem:Endereco,destino:Endereco,paradas:List<Endereco>,quando:Long):List<Rota>{
        if(paradas.isEmpty())return calcular(origem,destino,quando)
        require(paradas.size<=8){"Escolha até oito paradas intermediárias."}
        val locais=listOf(origem)+paradas+destino
        val trechos=mutableListOf<Rota>();var partida=quando
        for(i in 0 until locais.lastIndex){
            val trecho=calcular(locais[i],locais[i+1],partida).firstOrNull()?:throw IOException("Nenhuma rota para ${locais[i+1].nome}.")
            if(trecho.aviso.startsWith("OFFLINE:"))throw IOException("Conecte-se para planejar todas as paradas com horários atualizados.")
            require(trecho.inicio>=partida-60_000){"Conexão retornou um horário anterior à chegada."}
            trechos.add(trecho);partida=trecho.fim
        }
        return listOf(Rota(UUID.randomUUID().toString(),origem,destino,trechos.flatMap{it.etapas},
            fonte=trechos.map{it.fonte}.distinct().joinToString(" / "),
            aviso="Viagem com ${paradas.size} parada(s) na ordem escolhida, sem tempo extra de permanência. "+trechos.map{it.aviso}.distinct().joinToString(" "),paradasDesejadas=paradas))
    }

    suspend fun calcular(origem: Endereco, destino: Endereco, quando: Long = System.currentTimeMillis()): List<Rota> =
        planningMutex.withLock {
            withContext(Dispatchers.IO) {
                require(FormatoRotas.valido(origem.ponto) && FormatoRotas.valido(destino.ponto)) { "Escolha pontos válidos no mapa." }
                val url = TRANSITOUS.toHttpUrl().newBuilder()
                    .addQueryParameter("fromPlace", "${origem.ponto.latitude},${origem.ponto.longitude}")
                    .addQueryParameter("toPlace", "${destino.ponto.latitude},${destino.ponto.longitude}")
                    .addQueryParameter("time", Instant.ofEpochMilli(quando).toString())
                    .addQueryParameter("numItineraries", "3")
                    .addQueryParameter("maxPreTransitTime", "1800").addQueryParameter("maxPostTransitTime", "1800")
                    .addQueryParameter("preTransitModes", "WALK").addQueryParameter("postTransitModes", "WALK")
                    .addQueryParameter("directModes", "WALK").addQueryParameter("transitModes", "TRANSIT")
                    .addQueryParameter("detailedTransfers", "true").addQueryParameter("detailedLegs", "true")
                    .addQueryParameter("language", "pt").build()
                try { FormatoRotas.lerPlano(get(url, 8_000_000), origem, destino)
                } catch (e: CancellationException) { throw e
                } catch (e: IOException) {
                    val saved = rotasSalvas().filter { it.origem.ponto == origem.ponto && it.destino.ponto == destino.ponto }
                    if (saved.isEmpty()) throw IOException("Não foi possível consultar as rotas. Confira a internet e tente novamente.", e)
                    saved.map { r -> r.copy(etapas = r.etapas.map { it.copy(tipoDado = TipoDado.INDISPONIVEL) },
                        aviso = "OFFLINE: trajeto salvo em ${Instant.ofEpochMilli(r.consultadaEm)}. Horários antigos, sem recálculo. Confira o serviço antes de sair.") }
                }
            }
        }

    private suspend fun get(url:HttpUrl,limit:Long):String=suspendCancellableCoroutine{cont->
        val call=client.newCall(Request.Builder().url(url).header("User-Agent",USER_AGENT).header("Accept","application/json").build())
        cont.invokeOnCancellation{call.cancel()}
        call.enqueue(object:Callback{
            override fun onFailure(call:Call,e:IOException){if(cont.isActive)cont.resumeWithException(e)}
            override fun onResponse(call:Call,response:Response){
                try{
                    val raw=response.use{
                        if(!it.isSuccessful)throw IOException(if(it.code==429)"Serviço ocupado. Aguarde e tente novamente." else "Serviço indisponível (${it.code}).")
                        val body=it.body?:throw IOException("Resposta vazia.")
                        if(body.contentLength()>limit)throw IOException("Resposta excedeu o limite.")
                        val source=body.source();source.request(limit+1)
                        if(source.buffer.size>limit)throw IOException("Resposta excedeu o limite.")
                        source.readUtf8()
                    }
                    if(cont.isActive)cont.resume(raw)
                }catch(e:Exception){if(cont.isActive)cont.resumeWithException(e)}
            }
        })
    }

    @Synchronized fun salvarRota(rota: Rota) {
        val routes = (listOf(rota) + rotasSalvas().filterNot { it.id == rota.id }).take(10).toMutableList()
        var raw = JSONArray(routes.map(FormatoRotas::rotaJson)).toString()
        while (raw.toByteArray().size > 3_000_000 && routes.size > 1) {
            routes.removeAt(routes.lastIndex); raw = JSONArray(routes.map(FormatoRotas::rotaJson)).toString()
        }
        require(raw.toByteArray().size <= 3_000_000) { "Esta rota é grande demais para salvar." }
        val temp = File(savedFile.parentFile, "rotas_escolhidas.tmp")
        temp.writeText(raw)
        if (!temp.renameTo(savedFile)) { temp.delete(); throw IOException("Não foi possível salvar a rota.") }
        lembrar(rota.origem); lembrar(rota.destino)
    }
    @Synchronized fun rotasSalvas(): List<Rota> = runCatching {
        if (!savedFile.exists() || savedFile.length() > 3_000_000) emptyList()
        else JSONArray(savedFile.readText()).objetos().mapNotNull { runCatching { FormatoRotas.rotaSalva(it) }.getOrNull() }
    }.getOrDefault(emptyList())
    @Synchronized fun favoritos(): List<Endereco> = lerArray("favoritos").objetos().mapNotNull(FormatoRotas::enderecoSalvo)
    @Synchronized fun salvarFavorito(endereco: Endereco) {
        require(FormatoRotas.valido(endereco.ponto))
        salvarEnderecos("favoritos", (listOf(endereco) + favoritos().filterNot { it.ponto == endereco.ponto }).take(100)); lembrar(endereco)
    }
    @Synchronized fun removerFavorito(endereco: Endereco) { salvarEnderecos("favoritos", favoritos().filterNot { it.ponto == endereco.ponto }) }
    @Synchronized fun historico(): List<Endereco> = lerArray("historico").objetos().mapNotNull(FormatoRotas::enderecoSalvo)
    @Synchronized private fun lembrar(endereco: Endereco) {
        salvarEnderecos("historico", (listOf(endereco) + historico().filterNot { it.ponto == endereco.ponto }).take(30))
    }
    private fun salvarEnderecos(key: String, items: List<Endereco>) { prefs.edit().putString(key, JSONArray(items.map(FormatoRotas::enderecoJson)).toString()).apply() }
    @Synchronized fun viagensFavoritas(): List<ViagemFavorita> = lerArray("viagens").objetos().mapNotNull {
        val from = it.optJSONObject("origem")?.let(FormatoRotas::enderecoSalvo)
        val to = it.optJSONObject("destino")?.let(FormatoRotas::enderecoSalvo)
        if (from != null && to != null) ViagemFavorita(it.optString("nome"), from, to) else null
    }
    @Synchronized fun salvarViagemFavorita(v: ViagemFavorita) {
        require(FormatoRotas.valido(v.origem.ponto) && FormatoRotas.valido(v.destino.ponto))
        escreverViagens((listOf(v) + viagensFavoritas().filterNot { it.nome == v.nome }).take(50)); lembrar(v.origem); lembrar(v.destino)
    }
    @Synchronized fun removerViagemFavorita(v: ViagemFavorita) { escreverViagens(viagensFavoritas().filterNot { it == v }) }
    private fun escreverViagens(items: List<ViagemFavorita>) {
        prefs.edit().putString("viagens", JSONArray(items.map {
            JSONObject().put("nome", it.nome).put("origem", FormatoRotas.enderecoJson(it.origem)).put("destino", FormatoRotas.enderecoJson(it.destino))
        }).toString()).apply()
    }
    private fun lerArray(key: String): JSONArray = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrElse { JSONArray() }
    companion object {
        const val PHOTON = "https://photon.komoot.io/api/"
        const val TRANSITOUS = "https://api.transitous.org/api/v6/plan"
        const val USER_AGENT = "AnderRotas/1.1.0 (Android; contato: https://github.com/AnderHonorato)"
        private val geocodeMutex = Mutex()
        private var lastGeocodeAt = 0L
    }
}

internal fun JSONArray.objetos(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

/** External JSON is checked here; no fabricated schedules, stops or straight-line geometry. */
internal object FormatoRotas {
    fun valido(p: Ponto) = p.latitude.isFinite() && p.longitude.isFinite() && p.latitude in -90.0..90.0 && p.longitude in -180.0..180.0
    private fun ponto(j: JSONObject): Ponto? = Ponto(j.optDouble("lat", Double.NaN), j.optDouble("lon", Double.NaN)).takeIf(::valido)
    private fun hora(s: String): Long = Instant.parse(s).toEpochMilli()
    fun lerEnderecos(raw: String): List<Endereco> = try {
        JSONArray(raw).objetos().mapNotNull { j ->
            val p = ponto(j) ?: return@mapNotNull null
            val a = j.optJSONObject("address") ?: JSONObject()
            val house = a.optString("house_number")
            val street = a.optString("road").ifBlank { j.optString("name") }
            val name = if (street.isBlank()) j.optString("display_name") else street + if (house.isNotBlank()) ", $house" else ""
            val precision = if (house.isBlank()) "Aproximação da rua/região; número não confirmado" else "Endereço mapeado no OpenStreetMap; confira o ponto"
            Endereco(name, p, "$precision · ${j.optString("display_name")}")
        }.distinctBy { it.ponto }.take(6)
    } catch (e: Exception) { throw IOException("O serviço retornou endereços inválidos.", e) }

    fun lerSugestoes(raw:String):List<Endereco> = try{
        JSONObject(raw).getJSONArray("features").objetos().mapNotNull{feature->
            val coords=feature.optJSONObject("geometry")?.optJSONArray("coordinates")?:return@mapNotNull null
            if(coords.length()<2)return@mapNotNull null
            val ponto=Ponto(coords.optDouble(1,Double.NaN),coords.optDouble(0,Double.NaN))
            if(!valido(ponto))return@mapNotNull null
            val p=feature.optJSONObject("properties")?:return@mapNotNull null
            val nome=p.optString("name").ifBlank{p.optString("street")}
            if(nome.isBlank())return@mapNotNull null
            val numero=p.optString("housenumber")
            Endereco(nome+if(numero.isNotBlank())", $numero" else "",ponto,
                listOf(p.optString("street"),p.optString("district"),p.optString("city"),p.optString("state"),p.optString("country"),
                    if(numero.isBlank())"Número não confirmado · confira no mapa" else "Endereço mapeado · confira no mapa")
                    .filter{it.isNotBlank()}.distinct().joinToString(" · "))
        }.distinctBy{it.ponto}.take(6)
    }catch(e:Exception){throw IOException("O serviço retornou sugestões inválidas.",e)}

    fun lerPlano(raw: String, origem: Endereco, destino: Endereco): List<Rota> = try {
        val root = JSONObject(raw)
        if (!root.has("itineraries")) throw IOException("Resposta de rotas inválida.")
        (root.getJSONArray("itineraries").objetos() + root.optJSONArray("direct")?.objetos().orEmpty()).mapNotNull { j ->
            val legs = j.getJSONArray("legs").objetos()
            if (legs.any { it.optBoolean("cancelled") }) return@mapNotNull null
            val etapas = legs.map { leg ->
                val from = leg.getJSONObject("from"); val to = leg.getJSONObject("to")
                val inicio = hora(leg.getString("startTime")); val fim = hora(leg.getString("endTime")); require(fim >= inicio)
                val points = leg.optJSONObject("legGeometry")?.let { decodificar(it.optString("points"), it.optInt("precision", 6)) }.orEmpty()
                val stops = (listOf(from) + leg.optJSONArray("intermediateStops")?.objetos().orEmpty() + to)
                    .mapNotNull { stop -> ponto(stop)?.let { Endereco(stop.optString("name"), it, stop.optString("stopId")) } }
                Etapa(leg.getString("mode"), leg.optString("routeShortName").ifBlank { leg.optString("displayName") }, leg.optString("headsign"),
                    from.optString("name").let { if (it == "START") origem.nome else it }, to.optString("name").let { if (it == "END") destino.nome else it },
                    inicio, fim, points, stops, if (leg.optBoolean("realTime")) TipoDado.PREVISAO else TipoDado.PROGRAMADO,
                    leg.optString("agencyName"), leg.optDouble("distance", 0.0))
            }
            if (etapas.isEmpty()) return@mapNotNull null
            val agencias = etapas.map { it.agencia }.filter { it.isNotBlank() }.distinct()
            val avisoArtesp = if (agencias.any { it.contains("ARTESP", true) }) " ARTESP inclui tempos interpolados." else ""
            Rota(UUID.randomUUID().toString(), origem, destino, etapas,
                fonte = "Transitous / MOTIS" + if (agencias.isEmpty()) "" else " · ${agencias.joinToString(" / ")}",
                aviso = "GTFS pode estar desatualizado; confirme a operação. Horários programados e eventuais previsões estão identificados por etapa.$avisoArtesp Pontos sem desenho não têm geometria publicada. Confira o sentido antes de embarcar.")
        }.distinctBy { it.etapas.map { e -> "${e.linha}:${e.origem}:${e.inicio}" } }
            .sortedWith(compareBy<Rota> { it.fim }.thenBy { it.duracaoMinutos }).take(5)
    } catch (e: IOException) { throw e
    } catch (e: Exception) { throw IOException("O serviço retornou uma rota inválida. Tente novamente.", e) }

    fun decodificar(encoded: String, precision: Int): List<Ponto> {
        require(precision in 0..7 && encoded.length <= 2_000_000)
        var index = 0; var lat = 0L; var lon = 0L
        val result = ArrayList<Ponto>()
        fun next(): Long {
            var value = 0L; var shift = 0
            while (true) {
                require(index < encoded.length && shift <= 60)
                val b = encoded[index++].code - 63; require(b in 0..63)
                value = value or ((b and 31).toLong() shl shift); shift += 5
                if (b < 32) break
            }
            return if (value and 1L != 0L) (value shr 1).inv() else value shr 1
        }
        val scale = 10.0.pow(precision)
        while (index < encoded.length) {
            lat += next(); lon += next()
            val p = Ponto(lat / scale, lon / scale); require(valido(p)); result.add(p)
        }
        return result
    }

    fun enderecoJson(e: Endereco) = JSONObject().put("nome", e.nome).put("lat", e.ponto.latitude).put("lon", e.ponto.longitude).put("detalhe", e.detalhe)
    fun enderecoSalvo(j: JSONObject): Endereco? = ponto(j)?.let { Endereco(j.optString("nome"), it, j.optString("detalhe")) }
    fun rotaJson(r: Rota): JSONObject = JSONObject().put("id", r.id).put("origem", enderecoJson(r.origem)).put("destino", enderecoJson(r.destino))
        .put("paradasDesejadas",JSONArray(r.paradasDesejadas.map(::enderecoJson)))
        .put("consultadaEm", r.consultadaEm).put("fonte", r.fonte).put("aviso", r.aviso).put("etapas", JSONArray(r.etapas.map { e ->
            JSONObject().put("modo", e.modo).put("linha", e.linha).put("sentido", e.sentido).put("origem", e.origem).put("destino", e.destino)
                .put("inicio", e.inicio).put("fim", e.fim).put("tipoDado", e.tipoDado.name).put("agencia", e.agencia).put("distancia", e.distanciaMetros)
                .put("pontos", JSONArray(e.pontos.map { JSONArray(listOf(it.latitude, it.longitude)) })).put("paradas", JSONArray(e.paradas.map(::enderecoJson)))
        }))
    fun rotaSalva(j: JSONObject) = Rota(j.getString("id"), enderecoSalvo(j.getJSONObject("origem"))!!, enderecoSalvo(j.getJSONObject("destino"))!!,
        j.getJSONArray("etapas").objetos().map { e ->
            val ps = e.getJSONArray("pontos")
            Etapa(e.getString("modo"), e.optString("linha"), e.optString("sentido"), e.getString("origem"), e.getString("destino"), e.getLong("inicio"), e.getLong("fim"),
                (0 until ps.length()).map { i -> Ponto(ps.getJSONArray(i).getDouble(0), ps.getJSONArray(i).getDouble(1)).also { require(valido(it)) } },
                e.getJSONArray("paradas").objetos().mapNotNull(::enderecoSalvo), TipoDado.valueOf(e.getString("tipoDado")), e.optString("agencia"), e.optDouble("distancia"))
        }, j.getLong("consultadaEm"), j.getString("fonte"), j.getString("aviso"), j.optJSONArray("paradasDesejadas")?.objetos()?.mapNotNull(::enderecoSalvo).orEmpty())
}
