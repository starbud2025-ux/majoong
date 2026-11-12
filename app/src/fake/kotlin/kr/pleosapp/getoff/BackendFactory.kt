package kr.pleosapp.getoff

import android.app.Application
import kr.pleosapp.getoff.core.fake.FakeVehicle

@Suppress("UNUSED_PARAMETER")
fun createBackend(app: Application): Backend {
    val v = FakeVehicle().apply { stepDelayMs = 700 }
    return Backend(
        name = "fake",
        signals = v, ux = v, location = v, climate = v,
        demoPanel = { DemoPanel(v) },
    )
}
