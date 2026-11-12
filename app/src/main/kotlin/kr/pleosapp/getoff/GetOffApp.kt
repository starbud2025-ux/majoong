package kr.pleosapp.getoff

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kr.pleosapp.getoff.core.engine.AppController
import kr.pleosapp.getoff.core.engine.Settings
import kr.pleosapp.getoff.core.zone.InMemoryZoneRepository

class GetOffApp : Application() {
    lateinit var backend: Backend; private set
    lateinit var speaker: TtsSpeaker; private set
    lateinit var controller: AppController; private set
    val settings = MutableStateFlow(Settings())

    override fun onCreate() {
        super.onCreate()
        backend = createBackend(this)
        speaker = TtsSpeaker(this)
        val zones = assets.open("restricted_zones_sample.json").bufferedReader().use { ZoneJson.parse(it.readText()) }
        // Main.immediate는 단일 스레드라 컨트롤러의 단일 이벤트 루프 전제를 만족한다.
        controller = AppController(
            signals = backend.signals, ux = backend.ux, location = backend.location,
            zones = InMemoryZoneRepository(zones), speaker = speaker, climate = backend.climate,
            settings = settings, scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
    }
}
