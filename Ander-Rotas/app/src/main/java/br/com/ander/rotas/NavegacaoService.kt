package br.com.ander.rotas

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.speech.tts.TextToSpeech
import android.media.AudioAttributes
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

data class LeituraLocalizacao(val ponto: Ponto, val precisaoMetros: Float, val rumo: Float?, val velocidade: Float?, val instante: Long)

object SessaoNavegacao {
    private val leituras = MutableSharedFlow<LeituraLocalizacao>(extraBufferCapacity = 8)
    val localizacoes = leituras.asSharedFlow()
    val ativa = MutableStateFlow(false)
    val erro = MutableStateFlow<String?>(null)
    val estado = MutableStateFlow<EstadoNavegacao?>(null)
    val etapa = MutableStateFlow(0)
    @Volatile var rota: Rota? = null
    internal fun emitir(location: Location) {
        if (!location.hasAccuracy() || location.accuracy <= 0) return
        val idade = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        if (idade > 30_000 || idade < -1000) return
        leituras.tryEmit(LeituraLocalizacao(Ponto(location.latitude, location.longitude), location.accuracy,
            if (location.hasBearing()) location.bearing else null, if (location.hasSpeed()) location.speed else null, location.time))
    }
}

/** Acompanhamento iniciado por ação explícita, com notificação e botão encerrar. */
class NavegacaoService : Service(), LocationListener {
    private lateinit var localizador: LocationManager
    private lateinit var avisos: NotificationManager
    private val motor = MotorNavegacao()
    private var rotaAtual: Rota? = null
    private var ultimaNotificacao = 0L
    private var ultimoTexto = ""
    private var ultimoFixNanos = 0L
    private var voz:TextToSpeech?=null
    private var vozPronta=false
    private var concluindo=false
    private val handler=Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        localizador = getSystemService(LocationManager::class.java)
        avisos = getSystemService(NotificationManager::class.java)
        avisos.createNotificationChannel(NotificationChannel(CANAL,"Status da viagem",NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null);enableVibration(false)})
        avisos.createNotificationChannel(NotificationChannel(ALERTAS,"Desembarque e trajeto",NotificationManager.IMPORTANCE_HIGH).apply{enableVibration(true)})
    }
    private fun prepararVoz(){
        if(voz!=null)return
        voz=TextToSpeech(this){status->
                if(status==TextToSpeech.SUCCESS){
                    val resultado=voz?.setLanguage(Locale("pt","BR"))
                    vozPronta=resultado!=null && resultado!=TextToSpeech.LANG_MISSING_DATA && resultado!=TextToSpeech.LANG_NOT_SUPPORTED
                    voz?.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    if(SessaoNavegacao.ativa.value && SessaoNavegacao.rota?.id==rotaAtual?.id && !vozPronta && getSharedPreferences("aparencia",0).getBoolean("voz_permitida",false))SessaoNavegacao.erro.value="Voz em português indisponível. Ative uma voz nas configurações de texto para fala do Android. Os alertas visuais continuam."
                }else if(SessaoNavegacao.ativa.value && SessaoNavegacao.rota?.id==rotaAtual?.id && getSharedPreferences("aparencia",0).getBoolean("voz_permitida",false))SessaoNavegacao.erro.value="Não foi possível iniciar a voz. Os alertas visuais continuam."
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == PARAR) { encerrar(); return START_NOT_STICKY }
        if (intent?.action == ETAPA) {
            rotaAtual?.let {
                val novo = motor.definirEtapa(it, intent.getIntExtra("etapa", 0))
                SessaoNavegacao.estado.value = novo
                SessaoNavegacao.etapa.value = novo.etapaAtual
            }
            return START_NOT_STICKY
        }
        if(intent?.action==INICIAR && rotaAtual!=null)return START_NOT_STICKY
        if (intent?.action != INICIAR) { encerrar(); return START_NOT_STICKY }
        val rota = SessaoNavegacao.rota
        if(rota==null || !SessaoNavegacao.ativa.value || intent.getStringExtra("rota_id")!=rota.id){encerrar();return START_NOT_STICKY}
        if (!temPermissao(this)) {
            SessaoNavegacao.erro.value = "Autorize a localização para acompanhar a viagem."
            encerrar(); return START_NOT_STICKY
        }
        rotaAtual = rota
        ultimoFixNanos = 0L
        SessaoNavegacao.estado.value = motor.iniciar(rota)
        SessaoNavegacao.etapa.value = 0
        try {
            val notificacao = notificacao("Aguardando sinal de localização. Confirme cada etapa ao avançar.")
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notificacao, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(ID, notificacao)
            prepararVoz()
            localizador.removeUpdates(this)
            val preciso = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val fontes = listOfNotNull(if (preciso) LocationManager.GPS_PROVIDER else null, LocationManager.NETWORK_PROVIDER).filter { localizador.isProviderEnabled(it) }
            if (fontes.isEmpty()) {
                SessaoNavegacao.erro.value = "A localização está desativada. Ative o GPS nas configurações do aparelho."
                encerrar(); return START_NOT_STICKY
            }
            fontes.forEach { localizador.requestLocationUpdates(it, 5_000L, 0f, this, Looper.getMainLooper()) }
            SessaoNavegacao.ativa.value = true
            SessaoNavegacao.erro.value = if (preciso) null else "Localização aproximada: os alertas podem ser menos precisos."
        } catch (_: SecurityException) {
            SessaoNavegacao.erro.value = "A permissão de localização foi revogada. Autorize novamente para continuar."
            encerrar()
        } catch (_: IllegalStateException) {
            SessaoNavegacao.erro.value = "Abra o aplicativo para iniciar o acompanhamento da viagem."
            encerrar()
        } catch (_: IllegalArgumentException) {
            SessaoNavegacao.erro.value = "O provedor de localização está indisponível neste aparelho."
            encerrar()
        }
        return START_NOT_STICKY
    }

    override fun onLocationChanged(location: Location) {
        if(concluindo)return
        val idade = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        if (idade > 30_000 || idade < -1000 || !location.hasAccuracy() || location.accuracy <= 0) return
        val rota = rotaAtual ?: return
        if(!SessaoNavegacao.ativa.value || SessaoNavegacao.rota?.id!=rota.id){encerrar();return}
        // Dois provedores podem entregar a mesma observação; não duplicar alertas/progresso.
        if (ultimoFixNanos != 0L && location.elapsedRealtimeNanos - ultimoFixNanos < 2_000_000_000L) {
            if (location.elapsedRealtimeNanos >= ultimoFixNanos) SessaoNavegacao.emitir(location)
            return
        }
        ultimoFixNanos = location.elapsedRealtimeNanos
        val estado = motor.atualizar(rota, Ponto(location.latitude, location.longitude), location.accuracy)
        SessaoNavegacao.estado.value = estado
        SessaoNavegacao.etapa.value = estado.etapaAtual
        SessaoNavegacao.emitir(location)
        val agora = SystemClock.elapsedRealtime()
        if (agora - ultimaNotificacao > 15_000 || estado.mensagem != ultimoTexto) {
            avisos.notify(ID, notificacao(estado.mensagem))
            ultimaNotificacao = agora
            ultimoTexto = estado.mensagem
        }
        if(estado.avisoParada==AvisoParada.TRES){
            @Suppress("DEPRECATION")
            val vibrador=if(Build.VERSION.SDK_INT>=31)getSystemService(VibratorManager::class.java).defaultVibrator else getSystemService(Vibrator::class.java)
            vibrador?.vibrate(VibrationEffect.createWaveform(longArrayOf(0,300,150,300),-1))
        }
        if(estado.avisoParada in setOf(AvisoParada.DOIS,AvisoParada.DESEMBARCAR) || (estado.chegou && estado.alertar))falar(estado.mensagem)
        if (estado.alertar && (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
            val titulo = when { estado.chegou -> "Você chegou!"; estado.desvio -> "Confira o caminho"; else -> "Prepare-se para a próxima etapa" }
            avisos.notify(ID_ALERTA, Notification.Builder(this, ALERTAS).setSmallIcon(R.drawable.ic_navigation)
                .setContentTitle(titulo).setContentText(estado.mensagem).setStyle(Notification.BigTextStyle().bigText(estado.mensagem))
                .setContentIntent(abrirApp()).setAutoCancel(true).build())
        }
        if(estado.chegou){
            concluindo=true;localizador.removeUpdates(this)
            if(vozPronta && getSharedPreferences("aparencia",0).getBoolean("voz_permitida",false))handler.postDelayed({encerrar()},8_000)else encerrar()
        }
    }
    override fun onProviderDisabled(provider: String) { if(SessaoNavegacao.ativa.value && SessaoNavegacao.rota?.id==rotaAtual?.id)SessaoNavegacao.erro.value = "Sinal de localização indisponível. Seu roteiro continua salvo." }
    override fun onProviderEnabled(provider: String) { SessaoNavegacao.erro.value = null }
    @Deprecated("Compatibilidade com versões antigas do Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onBind(intent: Intent?): IBinder? = null
    private fun abrirApp() = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private fun notificacao(texto: String): Notification {
        val parar=PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("confirmar_cancelamento",true).putExtra("rota_id",rotaAtual?.id),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CANAL).setSmallIcon(R.drawable.ic_navigation)
            .setContentTitle("Rotas · viagem").setSubText(rotaAtual?.destino?.nome).setCategory(Notification.CATEGORY_NAVIGATION).setContentText(texto).setStyle(Notification.BigTextStyle().bigText(texto))
            .setContentIntent(abrirApp()).setOngoing(true).setOnlyAlertOnce(true)
            .apply{if(Build.VERSION.SDK_INT>=31)setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)}
            .addAction(Notification.Action.Builder(null, "Cancelar viagem", parar).build()).build()
    }
    private fun falar(texto:String){
        if(vozPronta && getSharedPreferences("aparencia",0).getBoolean("voz_permitida",false))voz?.speak(texto,TextToSpeech.QUEUE_FLUSH,null,"aviso-${SystemClock.elapsedRealtime()}")
    }
    private fun encerrar() {
        ultimoFixNanos = 0L
        if (::localizador.isInitialized) localizador.removeUpdates(this)
        SessaoNavegacao.ativa.value = false
        SessaoNavegacao.rota = null
        if(::avisos.isInitialized){avisos.cancel(ID_ALERTA);avisos.cancel(ID)}
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        if(::avisos.isInitialized){avisos.cancel(ID_ALERTA);avisos.cancel(ID)}
        handler.removeCallbacksAndMessages(null);voz?.stop();voz?.shutdown();voz=null;vozPronta=false
        if (::localizador.isInitialized) localizador.removeUpdates(this)
        SessaoNavegacao.ativa.value = false
        SessaoNavegacao.rota=null
        super.onDestroy()
    }
    companion object {
        private const val INICIAR = "br.com.ander.rotas.INICIAR"
        private const val PARAR = "br.com.ander.rotas.PARAR"
        private const val ETAPA = "br.com.ander.rotas.ETAPA"
        private const val CANAL = "viagem_status_v2"
        private const val ALERTAS = "alertas"
        private const val ID = 100
        private const val ID_ALERTA = 101
        fun limparAvisosSemViagem(context:Context){
            if(!SessaoNavegacao.ativa.value && SessaoNavegacao.rota==null){
                context.getSystemService(NotificationManager::class.java)?.let{it.cancel(ID);it.cancel(ID_ALERTA)}
                SessaoNavegacao.erro.value=null
            }
        }
        fun temPermissao(context: Context) = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        fun iniciar(context: Context, rota: Rota) {
            if(SessaoNavegacao.ativa.value || SessaoNavegacao.rota!=null)return
            SessaoNavegacao.ativa.value=true;SessaoNavegacao.erro.value=null
            SessaoNavegacao.rota = rota
            context.startForegroundService(Intent(context, NavegacaoService::class.java).setAction(INICIAR).putExtra("rota_id",rota.id))
        }
        fun parar(context: Context) { context.stopService(Intent(context, NavegacaoService::class.java)); SessaoNavegacao.ativa.value = false; SessaoNavegacao.rota = null;context.getSystemService(NotificationManager::class.java)?.cancel(ID_ALERTA);SessaoNavegacao.estado.value=null }
        fun atualizarEtapa(context: Context, indice: Int) {
            if (SessaoNavegacao.ativa.value) context.startService(Intent(context, NavegacaoService::class.java).setAction(ETAPA).putExtra("etapa", indice))
        }
        /** Leitura pontual por ação na tela; remove listeners em até20segundos. */
        fun solicitarLocalizacao(context: Context, callback: (LeituraLocalizacao?) -> Unit) {
            if (!temPermissao(context)) { callback(null); return }
            val lm = context.getSystemService(LocationManager::class.java)
            val handler = Handler(Looper.getMainLooper())
            var terminou = false
            lateinit var listener: LocationListener
            fun concluir(leitura: LeituraLocalizacao?) {
                if (terminou) return
                terminou = true
                lm.removeUpdates(listener)
                callback(leitura)
            }
            listener = object: LocationListener {
                override fun onLocationChanged(location: Location) {
                    val idade = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
                    if (idade > 30_000 || idade < -1000 || !location.hasAccuracy() || location.accuracy <= 0) return
                    concluir(LeituraLocalizacao(Ponto(location.latitude, location.longitude), location.accuracy, if(location.hasBearing()) location.bearing else null, if(location.hasSpeed()) location.speed else null, location.time))
                }
                @Deprecated("Compatibilidade Android") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            try {
                val preciso = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val provedores = listOfNotNull(if(preciso) LocationManager.GPS_PROVIDER else null, LocationManager.NETWORK_PROVIDER).filter { lm.isProviderEnabled(it) }
                if (provedores.isEmpty()) { concluir(null); return }
                provedores.forEach { lm.requestLocationUpdates(it, 1000, 0f, listener, Looper.getMainLooper()) }
                handler.postDelayed({ concluir(null) }, 20_000)
            } catch (_: SecurityException) { concluir(null) }
            catch (_: IllegalArgumentException) { concluir(null) }
        }
    }
}
