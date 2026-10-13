package com.pulse.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import java.util.Locale

/** On-device Android speech bridge. No cloud AI or background microphone capture. */
class VoiceSession(private val context: Context, private val onStatus: (String) -> Unit) {
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var ready = false
    private var closed = false

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.KOREAN
                if (!closed) onStatus("음성 응답 준비 완료")
            }
        }
    }

    fun greet() = speak("안녕하세요. 펄스입니다. 마이크 버튼을 눌러 이야기해 주세요.")

    fun listen() {
        if (closed) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onStatus("마이크 권한을 허용한 뒤 다시 눌러 주세요.")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onStatus("이 기기에서 음성 인식을 사용할 수 없습니다.")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { onStatus("듣고 있어요…") }
                override fun onBeginningOfSpeech() { onStatus("말씀을 듣는 중…") }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { onStatus("음성 처리 중…") }
                override fun onError(error: Int) { onStatus("인식하지 못했어요. 다시 눌러 주세요. (코드 $error)") }
                override fun onResults(results: Bundle?) {
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (spoken.isNullOrBlank()) { onStatus("말씀을 인식하지 못했어요."); return }
                    onStatus("나: $spoken")
                    speak(replyTo(spoken))
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            sr.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        }
    }

    private fun replyTo(text: String): String = when {
        text.contains("안녕") -> "안녕하세요. 오늘도 함께해요."
        text.contains("공부") -> "지금 가장 중요한 공부 한 가지를 정하고, 15분 동안 집중해 보세요."
        text.contains("프로젝트") -> "프로젝트에서 다음으로 해결할 문제 한 가지를 정해 볼까요?"
        text.contains("시간") -> "시간은 휴대전화 상태 표시줄에서 확인해 주세요. 시계 연동은 다음 버전에서 추가할게요."
        text.contains("누구") || text.contains("뭐 할 수") -> "저는 펄스입니다. 현재는 기본 음성 명령만 지원하고, AI 대화는 아직 연결되지 않았어요."
        else -> "말씀하신 내용은 들었어요. 아직 자유로운 AI 대화는 지원하지 않지만 다음 버전에서 연결할 예정이에요."
    }

    private fun speak(message: String) {
        if (closed) return
        onStatus("PULSE: $message")
        if (ready) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "pulse-reply")
    }

    fun stop() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
    }

    fun release() {
        closed = true
        stop()
        tts?.shutdown()
        tts = null
    }
}
