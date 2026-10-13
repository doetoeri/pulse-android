package com.pulse.assistant

import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var voice: VoiceSession? = null
    private var status by mutableStateOf("음성 기능 준비 중")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else getSystemService(VIBRATOR_SERVICE) as Vibrator
        voice = VoiceSession(this) { status = it }
        setContent {
            val vm: CallViewModel = viewModel()
            val state = vm.state
            LaunchedEffect(state) {
                if (state is CallState.Ringing) startAlert() else stopAlert()
            }
            DisposableEffect(state is CallState.Active) {
                if (state is CallState.Active) voice?.greet() else voice?.stop()
                onDispose { }
            }
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF9DCBFF))) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101A2C)) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("PULSE", color = Color(0xFF9DCBFF), letterSpacing = 3.sp)
                            Text("PHASE 02 · VOICE", color = Color.LightGray, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF9DCBFF), modifier = Modifier.size(88.dp))
                            Text("PULSE", color = Color.White, fontSize = 34.sp)
                            when (state) {
                                CallState.Idle -> Text("한국어 음성 통화 테스트", color = Color.White)
                                is CallState.Ringing -> {
                                    Text("수신 전화", color = Color(0xFF9DCBFF))
                                    Text(state.reason, color = Color.White)
                                }
                                is CallState.Active -> {
                                    Text("통화 중 · 로컬 음성 엔진", color = Color(0xFF9DCBFF))
                                    Elapsed(state.startedAt)
                                    Text(status, color = Color.White)
                                }
                                is CallState.Ended -> Text(state.message, color = Color.White)
                            }
                        }
                        when (state) {
                            CallState.Idle -> Button(onClick = vm::simulateIncomingCall) { Text("테스트 전화 받기") }
                            is CallState.Ringing -> Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Button(onClick = vm::decline, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC74355))) {
                                    Icon(Icons.Default.CallEnd, null); Text(" 거절")
                                }
                                Button(onClick = vm::accept, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF278D63))) {
                                    Icon(Icons.Default.Call, null); Text(" 수락")
                                }
                            }
                            is CallState.Active -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Button(onClick = { voice?.listen() }, enabled = !state.muted) {
                                    Icon(Icons.Default.Mic, null); Text(" 말하기")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(onClick = vm::toggleMute) { Text(if (state.muted) "음소거 해제" else "음소거") }
                                    OutlinedButton(onClick = vm::toggleSpeaker) { Text(if (state.speaker) "스피커 켜짐" else "스피커") }
                                }
                                Button(onClick = { voice?.stop(); vm.end() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC74355))) {
                                    Icon(Icons.Default.CallEnd, null); Text(" 통화 종료")
                                }
                            }
                            is CallState.Ended -> Button(onClick = vm::reset) { Text("처음으로") }
                        }
                        Text("로컬 음성 명령 · 클라우드 AI 미연결", color = Color.LightGray, fontSize = 11.sp)
                    }
                }
            }
        }
    }

    private fun startAlert() {
        stopAlert()
        try { ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.also { it.play() } } catch (_: Exception) {}
        try { vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 450, 300, 1200), 0)) } catch (_: Exception) {}
    }
    private fun stopAlert() {
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
        try { vibrator?.cancel() } catch (_: Exception) {}
    }
    override fun onStop() { super.onStop(); stopAlert(); voice?.stop() }
    override fun onDestroy() { voice?.release(); super.onDestroy() }
}

@Composable
private fun Elapsed(startedAt: Long) {
    var now by remember(startedAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val seconds = ((now - startedAt) / 1000).coerceAtLeast(0)
    Text("%02d:%02d".format(seconds / 60, seconds % 60), color = Color.White, fontSize = 28.sp)
}
