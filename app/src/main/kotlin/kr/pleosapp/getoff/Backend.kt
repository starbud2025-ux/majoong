package kr.pleosapp.getoff

import androidx.compose.runtime.Composable
import kr.pleosapp.getoff.core.signals.ClimateController
import kr.pleosapp.getoff.core.signals.LocationSource
import kr.pleosapp.getoff.core.signals.UxRestrictions
import kr.pleosapp.getoff.core.signals.VehicleSignals

/** flavor(fake/pleos)가 제공하는 차량 연결 묶음. UI·컨트롤러는 이 클래스만 안다. */
class Backend(
    val name: String,
    val signals: VehicleSignals,
    val ux: UxRestrictions,
    val location: LocationSource,
    val climate: ClimateController,
    /** 데모 조작 패널(fake 전용). pleos 빌드에서는 null. */
    val demoPanel: (@Composable () -> Unit)? = null,
)
