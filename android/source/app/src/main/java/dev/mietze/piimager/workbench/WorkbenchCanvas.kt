package dev.mietze.piimager.workbench

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.*

internal val Berry=Color(0xFFE94775)
internal val BerryDark=Color(0xFF922246)
internal val WbBackground=Color(0xFF09090D)
internal fun categoryColor(category:String):Color=when(category){
    "Operating systems"->Color(0xFFF7799C);"Boot"->Color(0xFFE94775);"Minecraft"->Color(0xFFADCD78)
    "Storage"->Color(0xFFC999B1);"Network"->Color(0xFFE7A8C2);"Drivers"->Color(0xFFCE85BC)
    "Runtimes"->Color(0xFFEEB889);"Services"->Color(0xFFCDA1DC);else->Color(0xFFB8ADB4)
}
@Composable internal fun WorkbenchCanvas(vm:WorkbenchVm,modifier:Modifier=Modifier,onSize:(Float,Float)->Unit,onTap:(String?)->Unit,onOpen:(String)->Unit){
    val density=LocalDensity.current.density
    val tap by rememberUpdatedState(onTap);val open by rememberUpdatedState(onOpen)
    val document=vm.project;val view=vm.view;val selected=vm.selected
    var lastTapTime by remember{mutableLongStateOf(0)};var lastTapId by remember{mutableStateOf<String?>(null)}
    Canvas(modifier.fillMaxSize().onSizeChanged{onSize(it.width/density,it.height/density)}
        .semantics{contentDescription="Free Raspberry workbench: ${document.nodes.size} blocks, ${document.links.size} connections. Drag blocks; pinch to zoom. Use Blocks list for accessible selection."}
        .pointerInput(vm,density){
            awaitEachGesture{
                val first=awaitFirstDown(requireUnconsumed=false)
                val world=WbGeometry.world(first.position.x/density,first.position.y/density,vm.view)
                val hit=WbGeometry.hit(vm.project.nodes,world.first,world.second)
                var moved=false;var dragging=false;var transformed=false;var distance=0f
                val startTime=first.uptimeMillis;var endTime=startTime
                do{
                    val event=awaitPointerEvent();endTime=event.changes.maxOfOrNull{it.uptimeMillis}?:endTime
                    val fingers=event.changes.count{it.pressed}
                    if(fingers>1){
                        if(dragging){vm.endDrag();dragging=false}
                        val c=event.calculateCentroid(useCurrent=false);val delta=event.calculatePan();val z=event.calculateZoom()
                        if(c.x.isFinite()&&c.y.isFinite()){vm.zoom(z,c.x/density,c.y/density);vm.pan(delta.x/density,delta.y/density)}
                        moved=true;transformed=true
                    }else if(fingers==1){
                        val change=event.changes.first{it.pressed};val delta=change.position-change.previousPosition
                        distance+=delta.getDistance()
                        if(distance>viewConfiguration.touchSlop){
                            moved=true
                            if(hit!=null&&!transformed){
                                if(!dragging){vm.beginDrag(hit.id);dragging=true}
                                vm.drag(delta.x/(density*vm.view.zoom),delta.y/(density*vm.view.zoom))
                            }else vm.pan(delta.x/density,delta.y/density)
                        }
                    }
                    if(moved)event.changes.forEach{it.consume()}
                }while(event.changes.any{it.pressed})
                if(dragging)vm.endDrag()
                if(!moved){
                    if(hit!=null&&(endTime-startTime>500||(lastTapId==hit.id&&endTime-lastTapTime<320))){tap(hit.id);open(hit.id);lastTapId=null}
                    else{tap(hit?.id);lastTapId=hit?.id;lastTapTime=endTime}
                }
            }
        }){
        drawRect(WbBackground)
        val d=density;val z=view.zoom;val px=view.panX*d;val py=view.panY*d
        if(document.grid){
            val step=max(16*d,32*d*z);val ox=((px%step)+step)%step;val oy=((py%step)+step)%step
            var x=ox;while(x<size.width){var y=oy;while(y<size.height){drawCircle(Color(0xFF31212C),1.0f*d,Offset(x,y));y+=step};x+=step}
        }
        val labelPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{typeface=Typeface.create("sans-serif",Typeface.NORMAL)}
        fun DrawScope.label(text:String,x:Float,y:Float,sp:Float,color:Color,bold:Boolean=false,width:Float=180f){
            labelPaint.textSize=sp*d;labelPaint.color=color.toArgb();labelPaint.typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL)
            var t=text.replace('\n',' ');while(t.isNotEmpty()&&labelPaint.measureText(t)>width*d)t=t.dropLast(1)
            if(t.length<text.length&&t.length>2)t=t.dropLast(2)+"…"
            drawContext.canvas.nativeCanvas.drawText(t,x*d,y*d,labelPaint)
        }
        withTransform({translate(px,py);scale(z,z,Offset.Zero)}){
            val nodes=document.nodes.associateBy{it.id}
            // Groups are visual ownership frames, independent of boot order.
            document.nodes.filter{it.group.isNotBlank()}.groupBy{it.group}.forEach{(name,items)->
                val l=items.minOf{it.x}-14;val t=items.minOf{it.y}-35;val r=items.maxOf{it.x}+WB_NODE_W+14;val b=items.maxOf{it.y}+WB_NODE_H+14
                drawRoundRect(Color(0xFF20131D).copy(alpha=.45f),Offset(l*d,t*d),Size((r-l)*d,(b-t)*d),CornerRadius(20*d))
                drawRoundRect(Color(0xFF513041),Offset(l*d,t*d),Size((r-l)*d,(b-t)*d),CornerRadius(20*d),style=Stroke(d))
                label(name,l+12,t+21,11f,Color(0xFFC89FB4),width=(r-l-24))
            }
            document.links.forEach{e->val a=nodes[e.from];val b=nodes[e.to];if(a!=null&&b!=null){
                val from=Offset((a.x+WB_NODE_W/2)*d,(a.y+WB_NODE_H)*d);val to=Offset((b.x+WB_NODE_W/2)*d,b.y*d)
                val gap=max(44*d,abs(to.y-from.y)*.4f);val path=Path().apply{moveTo(from.x,from.y);cubicTo(from.x,from.y+gap,to.x,to.y-gap,to.x,to.y)}
                val color=when(e.relation){WbRelation.CONFLICT->Color(0xFFFF876D);WbRelation.FALLBACK->Color(0xFFE6C47D);WbRelation.OPTIONAL->Color(0xFF887687);else->Berry.copy(alpha=.8f)}
                drawPath(path,color,style=Stroke(2*d))
                drawPath(Path().apply{moveTo(to.x-4*d,to.y-7*d);lineTo(to.x,to.y);lineTo(to.x+4*d,to.y-7*d)},color,style=Stroke(2*d))
                if(z>.45f)label(e.relation.label,(from.x+to.x)/(2*d)+6,(from.y+to.y)/(2*d),9.5f,color,width=90f)
            }}
            document.nodes.forEach{n->
                val x=n.x*d;val y=n.y*d;val k=WbCatalog.kind(n.type);val accent=categoryColor(k.category);val chosen=n.id in selected
                drawRoundRect(Color.Black.copy(alpha=.3f),Offset(x+2*d,y+5*d),Size(WB_NODE_W*d,WB_NODE_H*d),CornerRadius(19*d))
                drawRoundRect(if(chosen)Color(0xFF351D2B)else Color(0xFF211A21),Offset(x,y),Size(WB_NODE_W*d,WB_NODE_H*d),CornerRadius(18*d))
                drawRoundRect(if(chosen)Berry else accent.copy(alpha=if(n.enabled).45f else .14f),Offset(x,y),Size(WB_NODE_W*d,WB_NODE_H*d),CornerRadius(18*d),style=Stroke((if(chosen)2.3f else 1f)*d))
                drawRoundRect(accent.copy(alpha=.18f),Offset(x+14*d,y+13*d),Size(8*d,8*d),CornerRadius(3*d))
                label(k.category.uppercase(),n.x+30,n.y+22,9.5f,accent,bold=true,width=150f)
                label(n.name,n.x+14,n.y+49,15f,if(n.enabled)Color(0xFFFFF1F6)else Color(0xFF96888F),true)
                val second=when{n.type.startsWith("mc.")->"${n.value("port")} · ${if(n.type=="mc.java")"TCP"else"UDP"} · ${n.value("maxPlayers")} players"
                    n.number("reserveMiB")>0->"${n.number("reserveMiB")} MiB reserved"
                    n.type=="note"->n.value("text");n.conditions.isNotEmpty()->"${n.conditions.size} activation conditions";else->k.exportMode}
                label(second,n.x+14,n.y+71,10.5f,Color(0xFFB8A5AE))
                label(listOfNotNull(if(!n.enabled)"OFF"else"ON",if(n.locked)"LOCKED"else null,if(n.optional)"OPTIONAL"else null,if(n.attachment!=null)"FILE"else null).joinToString(" · "),n.x+14,n.y+94,9f,Color(0xFFBB8099),width=180f)
                drawCircle(accent,4*d,Offset(x+WB_NODE_W*d/2,y));drawCircle(accent,4*d,Offset(x+WB_NODE_W*d/2,y+WB_NODE_H*d))
            }
            if(document.nodes.isEmpty()){
                label("YOUR RASPBERRY WORKBENCH",32f,64f,18f,Color(0xFFF4D7E3),true,310f)
                label("Add an OS, boot component or Minecraft server.",32f,94f,12f,Color(0xFFB79CAB),false,330f)
                label("Drag anywhere. Connect blocks. Make it yours.",32f,116f,12f,Color(0xFFB79CAB),false,330f)
            }
        }
        if(document.minimap&&document.nodes.isNotEmpty()){
            val w=116*d;val h=74*d;val left=size.width-w-12*d;val top=size.height-h-12*d
            drawRoundRect(Color(0xEE191018),Offset(left,top),Size(w,h),CornerRadius(12*d))
            val minX=document.nodes.minOf{it.x};val minY=document.nodes.minOf{it.y};val worldW=document.nodes.maxOf{it.x}+WB_NODE_W-minX;val worldH=document.nodes.maxOf{it.y}+WB_NODE_H-minY
            val scale=min((w-16*d)/worldW,(h-16*d)/worldH)
            document.nodes.forEach{n->drawRoundRect(if(n.id in selected)Berry else Color(0xFF86556C),Offset(left+8*d+(n.x-minX)*scale,top+8*d+(n.y-minY)*scale),Size(max(3*d,WB_NODE_W*scale),max(2*d,WB_NODE_H*scale)),CornerRadius(2*d))}
        }
    }
}
