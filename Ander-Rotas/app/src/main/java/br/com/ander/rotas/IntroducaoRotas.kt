package br.com.ander.rotas

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IntroducaoRotas(concluir:()->Unit){
    var pagina by rememberSaveable{mutableIntStateOf(0)}
    val titulos=listOf("Buscar endereço","Mapa e paradas","Acompanhar a viagem")
    val textos=listOf(
        "Permita a localização para preencher a origem. Você pode editá-la. Digite o destino e toque em uma sugestão.",
        "Arraste o mapa para mover e use dois dedos para ampliar. Adicione paradas antes de iniciar e segure para ordenar.",
        "Toque em Ir. O painel mostra sua localização e o próximo ponto. A três pontos há vibração; a dois, voz autorizada. Cancelar exige confirmação."
    )
    val vinho=Color(0xFF8F2D4A)
    MaterialTheme(colorScheme=lightColorScheme(primary=vinho,onPrimary=Color.White,background=Color(0xFFF8EEE3),surface=Color(0xFFFFFAF5),onSurface=Color(0xFF2F2522),onSurfaceVariant=Color(0xFF75615B)),typography=Tipografia){
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=24.dp,vertical=10.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Simbolo("rota",Modifier.size(26.dp));Spacer(Modifier.width(9.dp));Text("Rotas",Modifier.weight(1f),fontSize=21.sp,fontWeight=FontWeight.Bold)
                    TextButton(onClick=concluir){Text("Pular")}
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)){
                    AnimatedContent(targetState=pagina,label="Instruções ilustradas"){passo->
                        IlustracaoInstrucao(passo,Modifier.fillMaxWidth().height(286.dp))
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                        repeat(3){i->Box(Modifier.width(if(i==pagina)26.dp else 8.dp).height(6.dp).background(if(i==pagina)vinho else vinho.copy(alpha=.16f),CircleShape))}
                    }
                    Text("${pagina+1} de 3",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(titulos[pagina],fontSize=29.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold)
                    Text(textos[pagina],fontSize=16.sp,lineHeight=24.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(pagina==0)Text("A localização é solicitada depois destas instruções.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top=12.dp,bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){
                    if(pagina>0)OutlinedButton(onClick={pagina--},modifier=Modifier.height(50.dp),shape=RoundedCornerShape(14.dp)){Text("Anterior")}
                    Button(onClick={if(pagina<2)pagina++ else concluir()},modifier=Modifier.weight(1f).height(50.dp),shape=RoundedCornerShape(14.dp)){Text(if(pagina==2)"Começar"else"Próximo",fontWeight=FontWeight.Bold);Spacer(Modifier.width(9.dp));Simbolo("seta",Modifier.size(18.dp),MaterialTheme.colorScheme.onPrimary)}
                }
            }
        }
    }
}

@Composable private fun IlustracaoInstrucao(pagina:Int,modifier:Modifier){
    val vinho=Color(0xFF8F2D4A);val verde=Color(0xFF2C7369);val papel=Color(0xFFFFFAF5)
    Box(modifier.background(Color(0xFFEEDFD4),RoundedCornerShape(24.dp)).semantics{contentDescription=when(pagina){0->"Imagem: origem por GPS e sugestões de destino";1->"Imagem: mapa com caminho e paradas ordenadas";else->"Imagem: painel com local atual e próximo ponto"}}){
        Canvas(Modifier.fillMaxSize()){
            val w=size.width;val h=size.height
            val ruas=Color(0xFFFFFAF5)
            drawLine(ruas,Offset(0f,h*.2f),Offset(w,h*.64f),32.dp.toPx(),StrokeCap.Round)
            drawLine(ruas,Offset(w*.18f,0f),Offset(w*.38f,h),25.dp.toPx(),StrokeCap.Round)
            drawLine(ruas,Offset(w*.8f,0f),Offset(w*.52f,h),29.dp.toPx(),StrokeCap.Round)
            if(pagina==1){
                val rota=Path().apply{moveTo(w*.16f,h*.72f);cubicTo(w*.4f,h*.75f,w*.18f,h*.3f,w*.47f,h*.36f);cubicTo(w*.72f,h*.42f,w*.65f,h*.17f,w*.85f,h*.2f)}
                drawPath(rota,Color.White,style=Stroke(11.dp.toPx(),cap=StrokeCap.Round))
                drawPath(rota,vinho,style=Stroke(5.dp.toPx(),cap=StrokeCap.Round))
                listOf(Offset(w*.16f,h*.72f),Offset(w*.47f,h*.36f),Offset(w*.85f,h*.2f)).forEach{drawCircle(Color.White,10.dp.toPx(),it);drawCircle(verde,6.dp.toPx(),it)}
            }
        }
        when(pagina){
            0->Column(Modifier.align(Alignment.TopCenter).padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                Surface(shape=RoundedCornerShape(12.dp),color=papel,shadowElevation=3.dp){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Simbolo("gps",Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Origem pelo GPS",fontSize=12.sp)}}
                Surface(shape=RoundedCornerShape(12.dp),color=papel,shadowElevation=3.dp){Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){Simbolo("busca",Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Digite o destino",fontSize=13.sp,fontWeight=FontWeight.Bold)}
                    HorizontalDivider(color=vinho.copy(alpha=.12f))
                    Text("Rua e cidade",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Estação ou terminal",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }}
            }
            1->Row(Modifier.align(Alignment.BottomStart).padding(18.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                listOf("1","2","3").forEach{n->Surface(shape=RoundedCornerShape(10.dp),color=papel,shadowElevation=2.dp){Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){Text("≡",color=vinho);Spacer(Modifier.width(7.dp));Text(n,fontWeight=FontWeight.Bold)}}}
            }
            else->Surface(Modifier.align(Alignment.TopStart).padding(16.dp).fillMaxWidth(.84f),shape=RoundedCornerShape(4.dp),color=papel,shadowElevation=4.dp){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){Simbolo("onibus",Modifier.size(23.dp));Spacer(Modifier.width(8.dp));Text("3 pontos",fontSize=17.sp,fontWeight=FontWeight.Bold)}
                LinearProgressIndicator(progress={.4f},modifier=Modifier.fillMaxWidth())
                Text("LOCAL ATUAL",fontSize=8.sp,color=vinho,fontWeight=FontWeight.Bold)
                Text("Ponto anterior",fontSize=12.sp)
                HorizontalDivider(color=vinho.copy(alpha=.13f))
                Text("PRÓXIMO PONTO",fontSize=8.sp,color=vinho,fontWeight=FontWeight.Bold)
                Text("Próxima parada",fontSize=13.sp,fontWeight=FontWeight.SemiBold)
            }}
        }
        IlustracaoMascote(when(pagina){0->"acenar";1->"apontar";else->"celebrar"},Modifier.align(Alignment.BottomEnd).padding(end=8.dp,bottom=4.dp).size(if(pagina==1)142.dp else 116.dp))
    }
}
