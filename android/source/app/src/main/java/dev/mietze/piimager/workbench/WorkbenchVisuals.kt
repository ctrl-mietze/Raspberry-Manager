package dev.mietze.piimager.workbench

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal val wbCategoryOrder = listOf(
    "Operating systems","Boot","Storage","Drivers","Network","Runtimes","Minecraft","Services","Notes"
)
internal fun wbCategoryRank(category:String):Int = wbCategoryOrder.indexOf(category).let { if(it<0) 999 else it }

private fun artIcon(type:String):ImageVector = when(type) {
    "os.pi64" -> Icons.Default.Memory
    "os.ubuntu" -> Icons.Default.Terminal
    "os.dietpi" -> Icons.Default.Speed
    "os.libreelec" -> Icons.Default.Movie
    "os.homeassistant" -> Icons.Default.Home
    "os.kali" -> Icons.Default.Security
    "os.android" -> Icons.Default.Android
    "os.custom" -> Icons.Default.Image
    "boot.a","boot.b" -> Icons.Default.PowerSettingsNew
    "boot.kernel" -> Icons.Default.Memory
    "boot.initramfs" -> Icons.Default.Inventory2
    "boot.firmware" -> Icons.Default.DeveloperBoard
    "boot.file" -> Icons.Default.Description
    "boot.patch" -> Icons.Default.Build
    "boot.selector" -> Icons.Default.AccountTree
    "boot.config" -> Icons.Default.Tune
    "boot.cmdline" -> Icons.Default.Terminal
    "driver.usb" -> Icons.Default.Usb
    "driver.wifi" -> Icons.Default.Wifi
    "driver.bluetooth" -> Icons.Default.Bluetooth
    "driver.audio" -> Icons.Default.VolumeUp
    "driver.display" -> Icons.Default.Tv
    "driver.storage" -> Icons.Default.Storage
    "driver.gpu" -> Icons.Default.Memory
    "driver.sensor" -> Icons.Default.Sensors
    "driver.overlay" -> Icons.Default.Layers
    "driver.gpio" -> Icons.Default.DeveloperBoard
    "storage.fat32" -> Icons.Default.SdStorage
    "storage.ext4" -> Icons.Default.Storage
    "storage.swap" -> Icons.Default.SwapHoriz
    "storage.volume" -> Icons.Default.Folder
    "storage.free" -> Icons.Default.SpaceBar
    "storage.recovery" -> Icons.Default.HealthAndSafety
    "net.ethernet" -> Icons.Default.SettingsEthernet
    "net.wifi" -> Icons.Default.Wifi
    "net.dns" -> Icons.Default.Public
    "net.firewall" -> Icons.Default.Security
    "net.vpn" -> Icons.Default.VpnKey
    "net.proxy" -> Icons.Default.SwapCalls
    "net.access" -> Icons.Default.AdminPanelSettings
    "runtime.java" -> Icons.Default.Terminal
    "runtime.docker" -> Icons.Default.AllInbox
    "runtime.python" -> Icons.Default.Code
    "runtime.node" -> Icons.Default.DataObject
    "service.ssh" -> Icons.Default.Terminal
    "service.vnc" -> Icons.Default.DesktopWindows
    "service.monitor" -> Icons.Default.MonitorHeart
    "service.backup" -> Icons.Default.Backup
    "service.watchdog" -> Icons.Default.Watch
    "service.mount" -> Icons.Default.Storage
    "service.update" -> Icons.Default.SystemUpdate
    "service.init" -> Icons.Default.Settings
    "service.remote" -> Icons.Default.Cloud
    "server.web" -> Icons.Default.Language
    "server.database" -> Icons.Default.Storage
    "server.files" -> Icons.Default.Folder
    "server.media" -> Icons.Default.Movie
    "server.game" -> Icons.Default.SportsEsports
    "service.custom" -> Icons.Default.Extension
    "note" -> Icons.Default.Notes
    "mc.java" -> Icons.Default.Terminal
    "mc.bukkit" -> Icons.Default.Extension
    "mc.nukkit" -> Icons.Default.ViewInAr
    "mc.bedrock" -> Icons.Default.ViewInAr
    "mc.mod.forge" -> Icons.Default.Build
    "mc.mod.fabric" -> Icons.Default.Texture
    else -> Icons.Default.Extension
}

@Composable internal fun WbKindArt(type:String,category:String,modifier:Modifier=Modifier.size(50.dp)) {
    if(type=="minecraft" || type=="mc.bedrock") {
        MinecraftCubeArt(modifier)
        return
    }
    val color=categoryColor(category)
    Surface(modifier=modifier,shape=RoundedCornerShape(16.dp),color=color.copy(alpha=.14f),
        border=androidx.compose.foundation.BorderStroke(1.dp,color.copy(alpha=.30f))) {
        Box(contentAlignment=Alignment.Center) {
            Icon(artIcon(type),null,tint=color,modifier=Modifier.size(27.dp))
        }
    }
}

@Composable internal fun MinecraftCubeArt(modifier:Modifier=Modifier.size(50.dp)) {
    Surface(modifier=modifier,shape=RoundedCornerShape(16.dp),color=Color(0xFF172016),
        border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF8CBF62).copy(alpha=.5f))) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val w=size.width; val h=size.height
            val top=Path().apply { moveTo(w*.5f,0f); lineTo(w,h*.22f); lineTo(w*.5f,h*.44f); lineTo(0f,h*.22f); close() }
            val left=Path().apply { moveTo(0f,h*.22f); lineTo(w*.5f,h*.44f); lineTo(w*.5f,h); lineTo(0f,h*.76f); close() }
            val right=Path().apply { moveTo(w,h*.22f); lineTo(w*.5f,h*.44f); lineTo(w*.5f,h); lineTo(w,h*.76f); close() }
            drawPath(left,Color(0xFF7A4E38)); drawPath(right,Color(0xFF563626)); drawPath(top,Color(0xFF80B95F))
            drawLine(Color(0xFFA6D67E),Offset(w*.08f,h*.22f),Offset(w*.5f,h*.40f),strokeWidth=2f)
            drawCircle(Color(0xFF4A7E3A),radius=2.2f,center=Offset(w*.33f,h*.15f))
            drawCircle(Color(0xFF9FD172),radius=1.7f,center=Offset(w*.68f,h*.17f))
        }
    }
}

@Composable internal fun MinecraftFamilyArt(java:Boolean,modifier:Modifier=Modifier.size(46.dp)) {
    if(java) Surface(modifier=modifier,shape=RoundedCornerShape(15.dp),color=Color(0xFF3A1820)) {
        Box(contentAlignment=Alignment.Center){Icon(Icons.Default.Terminal,null,tint=Color(0xFFFFB59A),modifier=Modifier.size(26.dp))}
    } else MinecraftCubeArt(modifier)
}
