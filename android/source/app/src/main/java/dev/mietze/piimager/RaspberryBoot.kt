package dev.mietze.piimager

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Native, skippable intro: no downloads or simulated initialization tasks. */
@Composable
fun RaspberryBoot(onFinished: () -> Unit) {
 val entry = remember { Animatable(0f) }
 val done by rememberUpdatedState(onFinished)
 LaunchedEffect(Unit) {
  if (!ValueAnimator.areAnimatorsEnabled()) { done(); return@LaunchedEffect }
  entry.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
  delay(550)
  done()
 }
 Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
  Box(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp)) {
   Text("NATIVE ANDROID / ${ReleaseInfo.LABEL}", letterSpacing = 2.sp,
    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary,
    modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
   Column(Modifier.align(Alignment.Center).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Icon(painterResource(R.drawable.ic_raspberry_mark), "Raspberry Manager logo", tint = Color.Unspecified,
     modifier = Modifier.size(136.dp).graphicsLayer {
      alpha = entry.value; scaleX = 0.8f + 0.2f * entry.value; scaleY = scaleX
      translationY = (1f - entry.value) * 18f
     })
    Spacer(Modifier.height(24.dp))
    Text("Raspberry\nManager", fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black,
     textAlign = TextAlign.Center, modifier = Modifier.graphicsLayer { alpha = entry.value })
    Spacer(Modifier.height(12.dp))
    Text("Your Pi. Your workspace.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
    Spacer(Modifier.height(26.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
     listOf("FLASH", "CONNECT", "MANAGE").forEach {
      Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
       Text(it, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
      }
     }
    }
   }
   Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
    TextButton(onClick = onFinished) { Text("Open workspace") }
    Spacer(Modifier.height(14.dp))
    Text("by Veyra · ${ReleaseInfo.VERSION}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
   }
  }
 }
}

@Composable
fun AppStartSettings() {
 val prefs = LocalContext.current.getSharedPreferences("pi_manager_v1", 0)
 var boot by remember { mutableStateOf(prefs.getBoolean("boot_logo", true)) }
 var chooser by remember { mutableStateOf(prefs.getBoolean("startup_chooser", true)) }
 Text("App start", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
 SettingSwitch("Raspberry boot logo", "Short native animation. Respects disabled system animations.", boot) {
  boot = it; prefs.edit().putBoolean("boot_logo", it).apply()
 }
 SettingSwitch("Startup action chooser", "Ask whether to flash a drive or open the manager.", chooser) {
  chooser = it; prefs.edit().putBoolean("startup_chooser", it).apply()
 }
}

@Composable
fun SettingSwitch(title: String, detail: String, checked: Boolean, changed: (Boolean) -> Unit) {
 Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
  Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
   Column(Modifier.weight(1f)) {
    Text(title, fontWeight = FontWeight.SemiBold)
    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
   }
   Spacer(Modifier.width(12.dp))
   Switch(checked = checked, onCheckedChange = changed)
  }
 }
}
