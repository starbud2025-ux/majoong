package kr.pleosapp.getoff

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kr.pleosapp.getoff.core.signals.Speaker
import java.util.Locale

/**
 * 온디바이스 TTS. v1 fake 빌드는 플랫폼 TextToSpeech를 쓰고, pleos 빌드는 검증 후 Pleos TTS SDK로 교체한다(docs/03).
 * 말한 문장은 [recent]에도 남겨 화면 자막·개발자 패널에서 볼 수 있다.
 */
class TtsSpeaker(context: Context) : Speaker {
    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recent: StateFlow<List<String>> = _recent

    @Volatile private var ready = false
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val r = if (status == TextToSpeech.SUCCESS) tts?.setLanguage(Locale.KOREAN) else null
            ready = r != null && r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override suspend fun speak(text: String) {
        _recent.value = (listOf(text) + _recent.value).take(8)
        if (ready) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, text.hashCode().toString())
    }
}
