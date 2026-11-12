package kr.pleosapp.getoff.core.model

/** F4 서리 제거 루틴 단계. 실행 순서는 선언 순서. */
enum class RoutineStep(val label: String, val feature: Feature, val manualHint: String) {
    DEFROSTER("앞유리 제습", Feature.DEFROSTER, "공조 패널에서 앞유리 성에 제거를 켜 주세요"),
    WIPER_ONCE("와이퍼 1회", Feature.WIPER, "와이퍼 레버를 한 번 당겨 주세요"),
    SIDE_MIRROR_HEAT("사이드미러 열선", Feature.SIDE_MIRROR_HEAT, "사이드미러 열선 버튼을 눌러 주세요"),
    STEERING_HEAT("핸들 열선", Feature.STEERING_HEAT, "핸들 열선 버튼을 눌러 주세요"),
}

sealed interface StepResult {
    data object Success : StepResult
    data object NotSupported : StepResult
    data class Failed(val reason: String) : StepResult
    /** 명령은 보냈지만 콜백으로 상태를 확인하지 못함. 성공으로 표시하지 않는다(AC4.2). */
    data object Unverified : StepResult
}

sealed interface PlannedStep {
    val step: RoutineStep
    data class Run(override val step: RoutineStep) : PlannedStep
    data class Skip(override val step: RoutineStep, val reason: String) : PlannedStep
}

data class StepOutcome(val step: RoutineStep, val result: StepResult?, val skippedReason: String? = null)
