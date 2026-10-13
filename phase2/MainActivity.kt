package com.pulse.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null

    private fun service(action: String) {
        val intent = Intent(this, BackgroundCallService::class.java).setAction(action)
        if (action == BackgroundCallService.START)
            ContextCompat.startForegroundService(this, intent)
        else startService(intent)
    }

    private fun startCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2001)
            return
        }
        service(BackgroundCallService.START)
    }

    private fun listen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            service(BackgroundCallService.LISTEN)
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2001)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2002)
        setContent {
            val vm: CallViewModel = viewModel()
            val state = vm.state
            val active = CallRuntime.active
            LaunchedEffect(state, active) {
                if (state is CallState.Ringing && !active) startRingtone() else stopRingtone()
            }
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF9DCBFF))) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101A2C)) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("PULSE  ·  BACKGROUND CALL", color = Color(0xFF9DCBFF), letterSpacing = 2.sp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text("PULSE", fontSize = 38.sp, color = Color.White)
                            when {
                                active -> {
                                    Text("통화 중 · 화면을 꺼도 통화 유지", color = Color(0xFF9DCBFF))
                                    Elapsed(CallRuntime.startedAt)
                                    Text(CallRuntime.status, color = Color.White)
                                }
                                state is CallState.Ringing -> {
                                    Text("PULSE 수신 전화", color = Color(0xFF9DCBFF))
                                    Text(state.reason, color = Color.White)
                                }
                                state is CallState.Ended -> Text(state.message, color = Color.White)
                                else -> Text("통화 테스트 준비 완료", color = Color.White)
                            }
                        }
                        when {
                            active -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Button(onClick = ::listen) { Icon(Icons.Default.Mic, null); Text(" 말하기") }
                                Text("화면이 꺼져도 세션은 유지됩니다.", color = Color.LightGray, fontSize = 12.sp)
                                Button(
                                    onClick = { service(BackgroundCallService.STOP); vm.reset() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC74355))
                                ) { Icon(Icons.Default.CallEnd, null); Text(" 종료") }
                            }
                            state is CallState.Ringing -> Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                Button(onClick = { stopRingtone(); vm.decline() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC74355))) { Text("거절") }
                                Button(onClick = { stopRingtone(); startCall() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF278D63))) {
                                    Icon(Icons.Default.Call, null); Text(" 수락")
                                }
                            }
                            else -> Button(onClick = vm::simulateIncomingCall) { Text("테스트 전화 걸기") }
                        }
                        Text("Android 포그라운드 서비스 · 실시간 AI 미연결", color = Color.LightGray, fontSize = 11.sp)
                    }
                }
            }
        }
    }

    private fun startRingtone() {
        if (ringtone?.isPlaying == true) return
        try {
            ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            ringtone?.play()
        } catch (_: Exception) {}
    }
    private fun stopRingtone() { ringtone?.stop(); ringtone = null }
    override fun onStop() { super.onStop(); stopRingtone() }
    override fun onDestroy() { stopRingtone(); super.onDestroy() }
}

@Composable
private fun Elapsed(startedAt: Long) {
    var now by remember(startedAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val seconds = ((now - startedAt) / 1000).coerceAtLeast(0)
    Text("%02d:%02d".format(seconds / 60, seconds % 60), color = Color.White, fontSize = 28.sp)
}
