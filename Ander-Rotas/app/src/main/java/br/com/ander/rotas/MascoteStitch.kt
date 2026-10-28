package br.com.ander.rotas

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.util.Locale

/** Quadro independente: matriz ARGB e bitmap nítido, nunca translação de imagem estática. */
internal class SpriteFrame(val width: Int, val height: Int, val pixels: IntArray) {
    val bitmap: ImageBitmap = Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888).asImageBitmap()
}
private data class SpriteAnimation(val frames: List<SpriteFrame>, val durations: List<Long>)
private object StitchSprites {
    private var cached: Map<String,SpriteAnimation>? = null
    @Synchronized fun load(context: Context): Map<String,SpriteAnimation> {
        cached?.let { return it }
        val atlas=context.assets.open("stitch/stitch_atlas.png").use { checkNotNull(BitmapFactory.decodeStream(it)) }
        check(atlas.width==640 && atlas.height==880)
        val states=linkedMapOf("idle" to 6,"andar" to 8,"correr" to 8,"pular" to 5,"acenar" to 6,"apontar" to 4,"sentar" to 4,"dormir" to 4,"celebrar" to 6,"duvida" to 4,"toque" to 4)
        val result=states.entries.mapIndexed { row,(state,count) ->
            val frames=(0 until count).map { column ->
                val pixels=IntArray(80*80);atlas.getPixels(pixels,0,80,column*80,row*80,80,80);SpriteFrame(80,80,pixels)
            }
            val timing=when(state) {
                "idle" -> listOf(800L,220L,340L,80L,90L,650L)
                "correr" -> List(count){75L}
                "andar" -> List(count){130L}
                "dormir" -> List(count){650L}
                "pular","celebrar" -> List(count){130L}
                else -> List(count){180L}
            }
            state to SpriteAnimation(frames,timing)
        }.toMap()
        atlas.recycle();cached=result;return result
    }
}
private val stitchPhrases=listOf("Arraste o mapa para mover.","Use dois dedos para ampliar ou reduzir.","Toque em Ir para iniciar a viagem.","Abra Roteiro para consultar as etapas.")
private val StitchWine=Color(0xFF702B3F)
private val StitchCream=Color(0xFFFFFAF0)

/** Reserve espaço no painel de viagem, sem sobrepor instruções ou controles do mapa.
 * Silenciar oculta balões; minimizar deixa um único alvo de 48dp. Não emite áudio.
 */
@Composable
fun MascoteStitch(
    estado: String,
    fala: String,
    minimizado: Boolean,
    silencioso: Boolean,
    onMinimizar: () -> Unit,
    onSilenciar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context=LocalContext.current
    val view=LocalView.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val animations=remember(context.applicationContext){StitchSprites.load(context.applicationContext)}
    var resumed by remember(lifecycle){mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    var visible by remember{mutableStateOf(false)}
    var motionEnabled by remember{mutableStateOf(true)}
    var lastPhrase by rememberSaveable{mutableIntStateOf(-1)}
    var touchNumber by remember{mutableIntStateOf(0)}
    var reacting by remember{mutableStateOf(false)}
    var reactionText by remember{mutableStateOf("")}
    var bubbleVisible by remember{mutableStateOf(false)}
    var frameIndex by remember{mutableIntStateOf(0)}
    DisposableEffect(lifecycle,context) {
        val observer=LifecycleEventObserver { _,_ -> resumed=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        fun updateMotion(){motionEnabled=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f}
        val settingsObserver=object:ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean){updateMotion()}
        }
        updateMotion()
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,settingsObserver)
        onDispose{lifecycle.removeObserver(observer);context.contentResolver.unregisterContentObserver(settingsObserver)}
    }
    val active=resumed && visible && !minimizado
    val requestedState=estado.lowercase(Locale.ROOT).takeIf{it in animations}?:"idle"
    val currentState=if(reacting)"toque" else requestedState
    val animation=animations.getValue(currentState)
    LaunchedEffect(currentState,active,motionEnabled) {
        frameIndex=0
        if(active && motionEnabled)while(true){delay(animation.durations[frameIndex]);frameIndex=(frameIndex+1)%animation.frames.size}
    }
    LaunchedEffect(touchNumber,active){if(reacting && active){delay(2800L);reacting=false}}
    LaunchedEffect(fala,touchNumber,active,silencioso) {
        bubbleVisible=active && !silencioso && (fala.isNotBlank() || reacting)
        if(bubbleVisible){delay(6000L);bubbleVisible=false}
    }
    val bubble=if(reacting)reactionText else fala
    val boundsModifier=modifier.onGloballyPositioned { coordinates ->
        val bounds=coordinates.boundsInWindow()
        visible=view.isShown && bounds.width>0f && bounds.height>0f && bounds.overlaps(Rect(0f,0f,view.width.toFloat(),view.height.toFloat()))
    }
    if(minimizado) {
        Box(boundsModifier.size(48.dp).background(StitchCream,CircleShape)
            .clickable(role=Role.Button,onClickLabel="Expandir Stitch",onClick=onMinimizar)
            .semantics{contentDescription="Expandir mascote Stitch"},contentAlignment=Alignment.Center) {
            StitchFrame(animations.getValue("idle").frames[0],Modifier.size(44.dp))
        }
        return
    }
    Column(boundsModifier.widthIn(max=160.dp).width(160.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        if(bubbleVisible && bubble.isNotBlank()) {
            Surface(color=StitchCream,contentColor=StitchWine,shape=RoundedCornerShape(16.dp,16.dp,4.dp,16.dp),shadowElevation=2.dp,modifier=Modifier.fillMaxWidth()) {
                Text(bubble,fontSize=11.sp,lineHeight=15.sp,fontWeight=FontWeight.Medium,textAlign=TextAlign.Center,maxLines=3,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(horizontal=10.dp,vertical=8.dp))
            }
        }
        Box(Modifier.size(108.dp).clickable(role=Role.Button,onClickLabel="Conversar com Stitch") {
            lastPhrase=stitchPhrases.indices.filter{it!=lastPhrase}.random()
            reactionText=stitchPhrases[lastPhrase];reacting=true;touchNumber++
        }.semantics{contentDescription="Stitch, mascote azul em pixel art. Toque para interagir."},contentAlignment=Alignment.Center) {
            StitchFrame(animation.frames[frameIndex.coerceIn(animation.frames.indices)],Modifier.fillMaxSize())
        }
        Row(Modifier.background(StitchCream.copy(alpha=.94f),RoundedCornerShape(24.dp)),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onSilenciar,modifier=Modifier.size(48.dp)) {
                Icon(if(silencioso)Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,if(silencioso)"Mostrar falas do Stitch" else "Silenciar falas do Stitch",tint=StitchWine,modifier=Modifier.size(18.dp))
            }
            IconButton(onClick=onMinimizar,modifier=Modifier.size(48.dp)) {
                Icon(Icons.Outlined.CloseFullscreen,"Minimizar Stitch",tint=StitchWine,modifier=Modifier.size(18.dp))
            }
        }
    }
}
@Composable
private fun StitchFrame(frame:SpriteFrame,modifier:Modifier=Modifier) {
    Canvas(modifier){drawImage(image=frame.bitmap,srcOffset=IntOffset.Zero,srcSize=IntSize(frame.width,frame.height),dstOffset=IntOffset.Zero,dstSize=IntSize(size.width.toInt(),size.height.toInt()),filterQuality=FilterQuality.None)}
}

@Composable
fun IlustracaoMascote(acao:String,modifier:Modifier=Modifier){
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val animacoes=remember(context.applicationContext){StitchSprites.load(context.applicationContext)}
    val animacao=animacoes[acao]?:animacoes.getValue("idle")
    var quadro by remember(acao){mutableIntStateOf(0)}
    var ativo by remember{mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))}
    DisposableEffect(lifecycle){
        val observador=LifecycleEventObserver{_,_->ativo=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
        lifecycle.addObserver(observador)
        onDispose{lifecycle.removeObserver(observador)}
    }
    LaunchedEffect(acao,ativo){if(ativo)while(true){delay(animacao.durations[quadro]);quadro=(quadro+1)%animacao.frames.size}}
    StitchFrame(animacao.frames[quadro],modifier.semantics{contentDescription="Mascote animado"})
}
