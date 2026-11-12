package kr.pleosapp.getoff.core.engine

import kotlinx.coroutines.CancellationException
import kr.pleosapp.getoff.core.model.*
import kr.pleosapp.getoff.core.signals.ClimateController
import kr.pleosapp.getoff.core.signals.VehicleSignals

class RoutineRunner(
    private val signals: VehicleSignals,
    private val climate: ClimateController,
) {
    /** 지원 여부로 실행/건너뜀을 나눈다(AC4.4). 제어는 하지 않는다. */
    suspend fun plan(steps: List<RoutineStep> = RoutineStep.entries): List<PlannedStep> = steps.map { step ->
        when (val c = signals.capability(step.feature)) {
            Capability.Supported -> PlannedStep.Run(step)
            is Capability.Unsupported -> PlannedStep.Skip(step, c.reason)
            is Capability.PermissionDenied -> PlannedStep.Skip(step, c.reason)
        }
    }

    /**
     * 사용자가 동의한 계획을 순서대로 실행한다. 호출 자체가 동의를 전제로 하며,
     * 컨트롤러는 동의 이벤트 없이 이 함수를 부르지 않는다(AC4.1).
     * 취소되면 이미 [onOutcome]으로 전달된 단계는 그대로 두고 되돌리지 않는다(AC4.3).
     */
    suspend fun run(plan: List<PlannedStep>, onOutcome: (StepOutcome) -> Unit): List<StepOutcome> {
        val out = mutableListOf<StepOutcome>()
        for (p in plan) {
            val o = when (p) {
                is PlannedStep.Skip -> StepOutcome(p.step, null, p.reason)
                is PlannedStep.Run -> StepOutcome(p.step, applySafely(p.step))
            }
            out += o
            onOutcome(o)
        }
        return out
    }

    private suspend fun applySafely(step: RoutineStep): StepResult = try {
        climate.apply(step)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        StepResult.Failed(e.message ?: "알 수 없는 오류")
    }
}
