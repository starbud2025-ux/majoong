@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package kr.pleosapp.getoff.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kr.pleosapp.getoff.core.model.DoorId
import kr.pleosapp.getoff.core.model.SeatPos
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kr.pleosapp.getoff.Backend
import kr.pleosapp.getoff.TtsSpeaker
import kr.pleosapp.getoff.core.engine.*
import kr.pleosapp.getoff.core.model.BeltState
import kr.pleosapp.getoff.core.model.CurbSide
import kr.pleosapp.getoff.core.model.Occupancy
import kr.pleosapp.getoff.core.model.PlannedStep
import kr.pleosapp.getoff.core.model.StepOutcome
import kr.pleosapp.getoff.core.model.StepResult

/*
 * Pleos Connect UI Design Guide 적용 (document.pleos.ai, 2026-11-08 확인)
 *  - 색: 공식 토큰(grayscale basic_00/50/100, informative active/positive/negative), 대비 4.5:1 이상
 *  - 주야간: 시스템 테마 따라 Light/Dark 자동 전환
 *  - 글자: 중립 서체, 최소 12sp, 문장 3줄 이내
 *  - 아이콘: Filled / Outlined 두 스타일만 사용, 항상 텍스트 라벨과 함께(48dp 격자 기준 크기)
 *  - 장식 애니메이션 없음. 처리 중에는 진행 표시
 *  - 한 손 조작: 큰 터치 영역(56dp+), 터치 햅틱
 *  - 세로(1창)·가로(2창) 대응
 * 레이아웃 원칙: 내용은 위에서부터 카드로 쌓고(가운데 떠 있지 않게), 카드 모서리·간격은 8dp 배수.
 * 움직임 원칙(2차 개편): 장식용 반복 애니메이션은 쓰지 않는다. 쓰는 것은 의미가 있는 움직임뿐이다.
 *   화면 전환(페이드+슬라이드), 카드 순차 등장, 터치 눌림, 상태 색 전환, 진행률, 실시간 감지 표시(작은 점 1개의 숨쉬기).
 */
private class Pal(
    val bg: Color, val bgDeep: Color, val surface: Color, val surfaceHi: Color, val line: Color,
    val text: Color, val dim: Color, val accent: Color, val ok: Color, val warn: Color, val danger: Color,
    val btnFill: Color, val onBtnFill: Color,
    /** 히어로 카드 그라데이션(흰 글자 대비 4.5:1 이상) */
    val heroA: Color, val heroB: Color,
)

// 다크: basic_00 #131417 / basic_50 #1D1E21 / basic_100 #313236, informative(dark) #00E074 #0082FF #FF4C28
private val DarkPal = Pal(
    bg = Color(0xFF131417), bgDeep = Color(0xFF0C0D10), surface = Color(0xFF1D1E21), surfaceHi = Color(0xFF26282C), line = Color(0xFF313236),
    text = Color(0xFFFFFFFF), dim = Color(0xFFA4A8AE), accent = Color(0xFF3E94FF),
    ok = Color(0xFF00E074), warn = Color(0xFFFFB020), danger = Color(0xFFFF6A4D),
    btnFill = Color(0xFFE0E1E6), onBtnFill = Color(0xFF131417),
    heroA = Color(0xFF0B3C9E), heroB = Color(0xFF0A5AD8),
)

// 라이트: basic_50 #F7F8FA / basic_00 #FFFFFF / basic_100 #EDEEF2, positive #0064FF. 상태색은 4.5:1을 위해 어두운 변형 사용
private val LightPal = Pal(
    bg = Color(0xFFF7F8FA), bgDeep = Color(0xFFEAEDF3), surface = Color(0xFFFFFFFF), surfaceHi = Color(0xFFEDEEF2), line = Color(0xFFE0E1E6),
    text = Color(0xFF131417), dim = Color(0xFF686A72), accent = Color(0xFF0064FF),
    ok = Color(0xFF007A3F), warn = Color(0xFF8A5A00), danger = Color(0xFFD12B0A),
    btnFill = Color(0xFF131417), onBtnFill = Color(0xFFFFFFFF),
    heroA = Color(0xFF0048C2), heroB = Color(0xFF0064FF),
)

/**
 * 모션 허용 여부. Pleos UI 가이드(Easy & Safe): "운전 중 동영상·애니메이션 이미지·자동 스크롤로 운전자 시선을 유도하면 안 된다".
 * 이 앱은 정차 중에만 화면이 동작하므로 정차 화면에서는 상태를 설명하는 모션을 쓰고,
 * 주행 중(Blocked)에는 반복 모션을 전부 멈춘다.
 */
private val LocalMotion = compositionLocalOf { true }

/** 폭 720dp 이상(가로 2창). 낮은 화면이라 홈·픽업은 스크롤 없이 좌우 2단으로 구성한다. */
private val LocalWide = compositionLocalOf { false }
private val LocalPal = staticCompositionLocalOf { DarkPal }
private val P: Pal @Composable @ReadOnlyComposable get() = LocalPal.current

// 타이포 위계: Headline 30 / Title 22 / Body 18 / Label 16 (모두 12sp 이상)
private const val HEADLINE = 30
private const val TITLE = 22
private const val BODY = 18
private const val LABEL = 16

/**
 * 한글 줄바꿈: 기본 설정은 글자 단위로 끊어서 "확인하/세요"처럼 어절 중간이 잘린다.
 * 글자 사이에 WORD JOINER(U+2060, 보이지 않음)를 넣어 공백에서만 줄이 바뀌게 하고,
 * LineBreak.Heading으로 줄 길이를 고르게 맞춘다. 이 파일의 모든 Text가 이 함수를 쓴다(같은 패키지 함수가 import보다 우선).
 */
private fun String.keepWords(): String {
    val src = this
    return buildString {
        src.forEachIndexed { i, c ->
            append(c)
            val n = src.getOrNull(i + 1)
            if (n != null && !c.isWhitespace() && !n.isWhitespace()) append('⁠')
        }
    }
}

@Composable
private fun Text(
    text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null, textAlign: TextAlign? = null, lineHeight: TextUnit = TextUnit.Unspecified, maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) = androidx.compose.material3.Text(
    text = text.keepWords(), modifier = modifier, color = color, fontSize = fontSize, fontWeight = fontWeight,
    textAlign = textAlign, lineHeight = lineHeight, maxLines = maxLines, overflow = overflow,
    style = LocalTextStyle.current.copy(lineBreak = LineBreak.Heading),
)

private val CardShape = RoundedCornerShape(24.dp)
private val InnerShape = RoundedCornerShape(16.dp)

@Composable
fun AppRoot(controller: AppController, settings: MutableStateFlow<Settings>, speaker: TtsSpeaker, backend: Backend) {
    val state by controller.state.collectAsState()
    val recent by speaker.recent.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showDev by remember { mutableStateOf(false) }
    var taps by remember { mutableIntStateOf(0) }
    val blocked = state is AppState.Blocked
    val dark = isSystemInDarkTheme()
    val pal = if (dark) DarkPal else LightPal
    val scheme = if (dark) {
        darkColorScheme(background = pal.bg, surface = pal.surface, onBackground = pal.text, onSurface = pal.text, primary = pal.btnFill)
    } else {
        lightColorScheme(background = pal.bg, surface = pal.surface, onBackground = pal.text, onSurface = pal.text, primary = pal.btnFill)
    }

    CompositionLocalProvider(LocalPal provides pal, LocalMotion provides !blocked) {
        MaterialTheme(colorScheme = scheme) {
            // Material3 기본 줄 높이(24sp 고정)는 큰 글자가 두 줄이 되면 겹친다 → 글자 크기에 비례(1.35배)
            ProvideTextStyle(LocalTextStyle.current.copy(lineHeight = 1.35.em)) {
            Surface(Modifier.fillMaxSize(), color = pal.bg) {
                BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(pal.bg, pal.bgDeep)))) {
                    val wide = maxWidth >= 720.dp // 가로(2창) 확장 레이아웃
                    // targetSdk 35는 edge-to-edge → 시스템 바와 겹치지 않게 인셋 적용
                    CompositionLocalProvider(LocalWide provides wide) {
                    Column(
                        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(horizontal = if (wide) 40.dp else 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        TopBar(
                            state = state,
                            showSettings = showSettings && !blocked,
                            onTitleTap = { if (++taps >= 5) { taps = 0; showDev = !showDev } }, // 개발자 패널(docs/04)
                            onSettings = if (blocked) null else ({ showSettings = !showSettings }),
                        )
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            // 화면 전환: 같은 종류의 상태 안에서 값만 바뀌면 전환하지 않고, 화면이 바뀔 때만 페이드+슬라이드
                            val view = Triple(showDev, showSettings && !blocked, state)
                            AnimatedContent(
                                targetState = view,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentKey = { v -> if (v.first) "dev" else if (v.second) "settings" else v.third::class },
                                transitionSpec = {
                                    (fadeIn(tween(320, delayMillis = 90)) + slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 20 }) togetherWith
                                        fadeOut(tween(140))
                                },
                                label = "screen",
                            ) { v ->
                                when {
                                    v.first -> DevPanel(v.third, recent, backend)
                                    v.second -> SettingsScreen(settings)
                                    else -> Screen(v.third, controller::onAction)
                                }
                            }
                        }
                        // 낮은 가로 화면에서는 캡션 줄을 생략(상태 카드가 같은 내용을 이미 보여 준다)
                        if (!wide && recent.isNotEmpty() && !showDev) Caption(recent.first())
                        backend.demoPanel?.let { DemoDock(it) }
                    }
                    }
                    SplashOverlay()
                }
            }
            }
        }
    }
}

/**
 * 시작 화면(스플래시). 앱을 켤 때 한 번만 약 1.6초 보이고 사라진다(반복 모션 없음 → 주행 규정과 무관).
 * 로고 점이 커지며 퍼지는 링 두 개로 "마중 나간다"는 느낌을 준다.
 */
@Composable
private fun SplashOverlay() {
    var visible by remember { mutableStateOf(true) }
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        kotlinx.coroutines.delay(500)
        visible = false
    }
    AnimatedVisibility(visible, enter = EnterTransition.None, exit = fadeOut(tween(380))) {
        Box(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(P.heroA, P.heroB)))
                .clickable(remember { MutableInteractionSource() }, null) { visible = false },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val c = center
                        listOf(0f, 0.28f).forEach { d ->
                            val t = ((p.value - d) / (1f - d)).coerceIn(0f, 1f)
                            drawCircle(Color.White.copy(alpha = 0.55f * (1f - t)), radius = size.minDimension / 2 * (0.35f + 0.65f * t), center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                        }
                        drawCircle(Color.White, radius = size.minDimension * 0.16f * (0.4f + 0.6f * p.value.coerceIn(0f, 1f)), center = c)
                    }
                }
                Text("마중", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.alpha(((p.value - 0.2f) / 0.6f).coerceIn(0f, 1f)))
                Text("아이를 맞이하는 짧은 정차를 챙겨요", fontSize = BODY.sp, color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.alpha(((p.value - 0.4f) / 0.6f).coerceIn(0f, 1f)))
            }
        }
    }
}

/** fake 빌드 전용 데모 패널. 접고 펼 수 있고, 펼쳐도 화면의 일부만 차지한다. */
@Composable
private fun DemoDock(panel: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(InnerShape).background(P.surface)) {
        Text(
            if (open) "데모 패널 닫기" else "데모 패널 열기",
            fontSize = LABEL.sp, color = P.accent, fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp),
        )
        if (open) Box(Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())) { panel() }
    }
}

// ---------------- 상단 바 ----------------

@Composable
private fun TopBar(state: AppState, showSettings: Boolean, onTitleTap: () -> Unit, onSettings: (() -> Unit)?) {
    val (label, color) = when (state) {
        is AppState.Blocked -> "주행 중" to P.danger
        AppState.Checking -> "확인 중" to P.accent
        else -> "정차 중" to P.ok
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "마중", fontSize = TITLE.sp, fontWeight = FontWeight.SemiBold, color = P.text,
            modifier = Modifier.clickable(remember { MutableInteractionSource() }, null, onClick = onTitleTap),
        )
        Spacer(Modifier.weight(1f))
        Pill(label, color)
        if (onSettings != null) {
            Spacer(Modifier.width(8.dp))
            val click = feedback(onSettings)
            Row(
                Modifier.heightIn(min = 48.dp).clip(CircleShape).clickable(onClick = click).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (showSettings) Icons.Filled.Close else Icons.Outlined.Settings, null, tint = P.accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (showSettings) "닫기" else "설정", fontSize = BODY.sp, color = P.accent, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ---------------- 화면 ----------------

@Composable
private fun Screen(state: AppState, act: (UserAction) -> Unit) {
    when (state) {
        is AppState.Blocked -> CenterMessage(Icons.Filled.Lock, P.danger, Phrases.BLOCKED, state.notice ?: "차가 멈추면 다시 사용할 수 있어요")
        AppState.Checking -> CenterMessage(null, P.accent, "확인하고 있어요", null, busy = true)
        is AppState.ZoneWarning -> Page(footer = {
            PrimaryButton("알겠어요", Modifier.fillMaxWidth()) { act(UserAction.DismissZone) }
        }) {
            // 제목과 같은 말을 반복하지 않고, 구역 종류와 시간대만 보여 준다(음성 문구는 Phrases 그대로)
            val zone = state.hit.zone
            StatusCard(Icons.Filled.Warning, P.warn, "주정차 금지 구역일 수 있어요",
                zone.type.label + (zone.activeHours?.let { " · ${it.label()}" } ?: ""),
                "샘플 데이터 기준이며 실제 단속 구역과 다를 수 있어요")
        }
        is AppState.ExitChecklist -> ChecklistScreen(state, act)
        is AppState.Idle -> HomeScreen(state, act)
        is AppState.PickupActive -> PickupScreen(state, act)
        is AppState.DepartPrep -> DepartPrepScreen(state, act)
        is AppState.RoutineRunning -> RoutineScreen("서리 제거 중", state.plan, state.outcomes, running = true) {}
        is AppState.RoutineDone -> RoutineScreen(Phrases.ROUTINE_DONE, emptyList(), state.outcomes, running = false) {
            PrimaryButton("확인", Modifier.fillMaxWidth()) { act(UserAction.Close) }
        }
    }
}

/** 홈: 픽업 모드가 주인공(히어로 카드), 하차 점검·출발 준비는 보조 타일. */
@Composable
private fun HomeScreen(s: AppState.Idle, act: (UserAction) -> Unit) {
    if (LocalWide.current) {
        // 가로(낮은 화면): 왼쪽 히어로, 오른쪽 보조 타일 두 개를 세로로. 스크롤 없이 한 화면.
        // (상단 상태 알약이 이미 "정차 중"을 알리므로 인사말 줄은 생략해 높이를 카드에 준다)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HeroCard("픽업 모드", "아이가 타고 내리는 순간을 좌석별로 챙겨요", "시작하기", Modifier.weight(1.35f).fillMaxHeight().reveal(1), compact = true) {
                    act(UserAction.PickupStart)
                }
                Column(Modifier.weight(1f).fillMaxHeight().reveal(2), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile(Icons.Outlined.ExitToApp, "내릴게요", "하차 전 점검", primary = false, modifier = Modifier.weight(1f).fillMaxWidth(), compact = true) {
                        act(UserAction.ExitRequested)
                    }
                    ActionTile(Icons.Outlined.Refresh, "출발 준비", "시야 확보 안내", primary = false, modifier = Modifier.weight(1f).fillMaxWidth(), compact = true) {
                        act(UserAction.DepartRequested)
                    }
                }
            }
        }
    } else HomePortrait(s, act)
}

@Composable
private fun HomePortrait(s: AppState.Idle, act: (UserAction) -> Unit) = Page {
    Column(Modifier.reveal(0), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(s.message ?: "정차 중이에요", fontSize = HEADLINE.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        Text("오늘은 어떤 걸 도와드릴까요?", fontSize = BODY.sp, color = P.dim)
    }
    HeroCard(
        title = "픽업 모드",
        desc = "아이가 타고 내리는 순간을\n좌석별로 챙겨요",
        cta = "시작하기",
        modifier = Modifier.reveal(1),
    ) { act(UserAction.PickupStart) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max).reveal(2)) {
        ActionTile(Icons.Outlined.ExitToApp, "내릴게요", "하차 전 점검", primary = false, modifier = Modifier.weight(1f).fillMaxHeight()) {
            act(UserAction.ExitRequested)
        }
        ActionTile(Icons.Outlined.Refresh, "출발 준비", "시야 확보 안내", primary = false, modifier = Modifier.weight(1f).fillMaxHeight()) {
            act(UserAction.DepartRequested)
        }
    }
}

/** 큰 그라데이션 카드. 앱의 대표 기능 진입점. */
@Composable
private fun HeroCard(title: String, desc: String, cta: String, modifier: Modifier = Modifier, compact: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().clip(CardShape)
            .background(Brush.linearGradient(listOf(P.heroA, P.heroB)))
            .pressScale(onClick),
    ) {
        // 천천히 떠다니는 원형 장식(정차 중에만 움직임)
        val motion = LocalMotion.current
        val drift = rememberInfiniteTransition(label = "drift")
        val d1 by drift.animateFloat(-1f, 1f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d1")
        val d2 by drift.animateFloat(1f, -1f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d2")
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 48.dp, y = (-48).dp).size(200.dp)
                .graphicsLayer { if (motion) { translationX = d1 * 16.dp.toPx(); translationY = d2 * 12.dp.toPx() } }
                .clip(CircleShape).background(Color.White.copy(alpha = 0.07f)),
        )
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = 20.dp, y = 56.dp).size(140.dp)
                .graphicsLayer { if (motion) { translationX = d2 * 14.dp.toPx(); translationY = d1 * 10.dp.toPx() } }
                .clip(CircleShape).background(Color.White.copy(alpha = 0.06f)),
        )
        Column(
            Modifier.padding(if (compact) 20.dp else 24.dp).then(if (compact) Modifier.fillMaxHeight() else Modifier),
            verticalArrangement = if (compact) Arrangement.SpaceBetween else Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.Face, Color.White, if (compact) 44.dp else 52.dp, circle = true)
                Spacer(Modifier.width(14.dp))
                Text(title, fontSize = (if (compact) TITLE else HEADLINE).sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Text(desc, fontSize = (if (compact) BODY else TITLE).sp, color = Color.White.copy(alpha = 0.92f), lineHeight = 30.sp, maxLines = 2)
            Box(Modifier.clip(CircleShape).background(Color.White).padding(horizontal = 24.dp, vertical = if (compact) 10.dp else 14.dp)) {
                Text(cta, fontSize = BODY.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0B3C9E), maxLines = 1)
            }
        }
    }
}

// ---------------- 움직임 도우미 ----------------

/** 카드가 위로 살짝 떠오르며 순서대로 나타난다(index × 70ms). */
@Composable
private fun Modifier.reveal(index: Int): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        p.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 26.dp.toPx()
    }
}

/** 누르면 살짝 줄었다가 튕겨 돌아오고, 햅틱을 준다. */
@Composable
private fun Modifier.pressScale(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.965f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press",
    )
    val click = feedback(onClick)
    return graphicsLayer { scaleX = scale; scaleY = scale }.clickable(src, null, onClick = click)
}

/** 감지 중임을 알리는 작은 점. 장식이 아니라 "지금 읽고 있어요" 신호라서 이것 하나만 반복한다. */
@Composable
private fun LiveDot(color: Color, size: Dp = 10.dp) {
    val motion = LocalMotion.current
    val t = rememberInfiniteTransition(label = "live")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "liveA")
    Box(Modifier.size(size).graphicsLayer { alpha = if (motion) a else 1f }.clip(CircleShape).background(color))
}

/** 경고·주의 상태의 아이콘 뒤에서 퍼져 나가는 고리. 상태를 알리는 용도이며 주행 중에는 멈춘다. */
@Composable
private fun AlertRing(color: Color, size: Dp, content: @Composable () -> Unit) {
    val motion = LocalMotion.current
    val t = rememberInfiniteTransition(label = "ring")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing)), label = "ringP")
    Box(contentAlignment = Alignment.Center) {
        if (motion) Box(
            Modifier.size(size).graphicsLayer { scaleX = 1f + 0.55f * p; scaleY = 1f + 0.55f * p; alpha = 0.55f * (1f - p) }
                .clip(RoundedCornerShape(size / 3.2f)).background(color),
        )
        content()
    }
}

/** 상태 아이콘이 바뀔 때 튕기며 나타난다(성공·경고 전환을 눈으로 알리기 위함). */
@Composable
private fun Modifier.pop(key: Any): Modifier {
    val s = remember(key) { Animatable(0.55f) }
    LaunchedEffect(key) { s.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    return graphicsLayer { scaleX = s.value; scaleY = s.value }
}

/** 등·하원 픽업 모드: 승차는 좌석별 벨트, 하차는 인도 쪽 문 안내. 하나의 화면이 실시간으로 바뀐다. */
@Composable
private fun PickupScreen(s: AppState.PickupActive, act: (UserAction) -> Unit) {
    val alighting = s.phase == PickupPhase.Alighting
    if (LocalWide.current) {
        // 가로(낮은 화면): 왼쪽 상태+버튼, 오른쪽 차량+좌석. 스크롤 없이 한 화면에 담는다.
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                PickupBanner(s)
                PickupFooter(act)
            }
            Column(Modifier.weight(1.3f).fillMaxHeight().verticalScroll(rememberScrollState())) { SeatCard(s, alighting, wide = true) }
        }
    } else {
        Page(footer = { PickupFooter(act) }) {
            PickupBanner(s)
            SeatCard(s, alighting, wide = false)
        }
    }
}

@Composable
private fun PickupBanner(s: AppState.PickupActive) {
    val alighting = s.phase == PickupPhase.Alighting
    when {
        alighting && s.wrongSideOpen -> StatusCard(Icons.Filled.Warning, P.danger, "차도 쪽 문이에요", "${s.curbSide.label} 문으로 내려 주세요. 반대쪽은 차도예요")
        alighting -> StatusCard(Icons.Filled.CheckCircle, P.ok, "${s.curbSide.label} 문으로 내리세요", Phrases.PASSENGER_TIP)
        else -> when (s.boarding.status) {
            BoardingStatus.Waiting -> StatusCard(Icons.Outlined.Person, P.dim, "탑승을 기다리고 있어요", "뒷좌석에 앉으면 벨트를 확인해요")
            BoardingStatus.Unbuckled -> StatusCard(Icons.Filled.Warning, P.danger, "안전벨트를 확인하세요",
                s.boarding.unbuckled.joinToString(", ") { it.seat.label })
            BoardingStatus.NeedsCheck -> StatusCard(Icons.Filled.Info, P.warn, "안전벨트를 확인하세요",
                if (s.boarding.degraded) "이 차량은 일부 정보를 읽을 수 없어 직접 확인해 주세요" else "읽지 못한 좌석이 있어요")
            BoardingStatus.Ready -> StatusCard(Icons.Filled.CheckCircle, P.ok, "안전벨트를 모두 확인했어요", null)
        }
    }
}

/** 위에서 내려다본 차: 좌석·열린 문을 한눈에. 텍스트 상태는 좌석 카드가 그대로 말해 준다. */
@Composable
private fun SeatCard(s: AppState.PickupActive, alighting: Boolean, wide: Boolean) {
    Column(
        Modifier.fillMaxWidth().clip(CardShape).background(P.surface).border(BorderStroke(1.dp, P.line), CardShape).padding(16.dp).reveal(1),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 낮은 가로 화면에서는 헤더 줄을 생략하고 좌석 카드에 높이를 준다
        if (!wide) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("뒷좌석", fontSize = LABEL.sp, color = P.dim, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            LiveDot(if (alighting) P.warn else P.ok)
            Spacer(Modifier.width(8.dp))
            Text("실시간", fontSize = LABEL.sp, color = P.dim)
        }
        if (wide) {
            // 차량 그림과 좌석 카드를 가로로 나란히
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                CarMap(s.boarding.seats, s.openRearDoors, s.curbSide, Modifier.weight(0.9f).height(112.dp).align(Alignment.CenterVertically))
                s.boarding.seats.forEach { SeatSlot(it, Modifier.weight(1f).fillMaxHeight()) }
            }
        } else {
            CarMap(s.boarding.seats, s.openRearDoors, s.curbSide, Modifier.fillMaxWidth().height(150.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max)) {
                s.boarding.seats.forEach { SeatSlot(it, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun PickupFooter(act: (UserAction) -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
    PrimaryButton("모두 내렸어요", Modifier.weight(1f), small = LocalWide.current) { act(UserAction.PickupDone) }
    SecondaryButton("종료", Modifier.width(120.dp)) { act(UserAction.PickupCancel) }
}

private data class SeatLook(val icon: ImageVector, val tone: Color, val status: String)

@Composable
private fun seatLook(st: SeatStatus): SeatLook = when {
    st.occupancy == Occupancy.Unknown -> SeatLook(Icons.Outlined.Info, P.warn, "확인 불가")
    st.occupancy == Occupancy.Empty -> SeatLook(Icons.Outlined.Person, P.dim, "비어 있음")
    st.belt == BeltState.Buckled -> SeatLook(Icons.Filled.Person, P.ok, "벨트 착용")
    st.belt == BeltState.Unbuckled -> SeatLook(Icons.Filled.Person, P.danger, "벨트 미착용")
    else -> SeatLook(Icons.Filled.Person, P.warn, "벨트 확인 불가")
}

@Composable
private fun SeatSlot(st: SeatStatus, modifier: Modifier) {
    val look = seatLook(st)
    val alert = st.occupancy == Occupancy.Occupied && st.belt != BeltState.Buckled
    // 상태가 바뀌면 색이 부드럽게 섞이고, 경고가 되는 순간 살짝 튀어 오른다
    val tone by animateColorAsState(look.tone, tween(380), label = "seatTone")
    val border by animateColorAsState(if (alert) look.tone else P.line, tween(380), label = "seatBorder")
    val scale by animateFloatAsState(if (alert) 1.04f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "seatScale")
    Column(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(InnerShape).background(P.surfaceHi)
            .border(BorderStroke(if (alert) 2.dp else 1.dp, border), InnerShape)
            .padding(horizontal = 6.dp, vertical = if (LocalWide.current) 8.dp else 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (LocalWide.current) 4.dp else 10.dp),
    ) {
        Text(st.seat.label.removePrefix("뒷좌석 "), fontSize = LABEL.sp, color = P.dim, maxLines = 1)
        IconBadge(look.icon, tone, if (LocalWide.current) 40.dp else 56.dp, circle = true)
        Text(look.status, fontSize = LABEL.sp, color = tone, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/**
 * 위에서 본 차량 그림. 앞쪽이 위. 뒷줄 3석을 상태 색으로 칠하고, 열린 뒷문은 바깥으로 벌어져 보인다.
 * 인도 쪽(내리는 쪽) 문에는 초록 표시, 차도 쪽 문이 열리면 빨강으로 보인다.
 */
@Composable
private fun CarMap(seats: List<SeatStatus>, openDoors: Set<DoorId>, curb: CurbSide, modifier: Modifier) {
    val line = P.line
    val body = P.surfaceHi
    val okC = P.ok
    val dangerC = P.danger
    val dim = P.dim
    val tones = seats.associate { it.seat to seatLook(it).tone }
    // 좌석 색 전환
    val rl by animateColorAsState(tones[SeatPos.REAR_LEFT] ?: dim, tween(380), label = "rl")
    val rc by animateColorAsState(tones[SeatPos.REAR_CENTER] ?: dim, tween(380), label = "rc")
    val rr by animateColorAsState(tones[SeatPos.REAR_RIGHT] ?: dim, tween(380), label = "rr")
    // 문 열림 각도(0~1)
    val leftOpen by animateFloatAsState(if (DoorId.REAR_LEFT in openDoors) 1f else 0f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow), label = "dl")
    val rightOpen by animateFloatAsState(if (DoorId.REAR_RIGHT in openDoors) 1f else 0f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow), label = "dr")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val carW = minOf(w * 0.5f, h * 0.9f); val carH = h
        val left = (w - carW) / 2f
        val r = CornerRadius(carW * 0.28f)
        // 차체
        drawRoundRect(body, Offset(left, 0f), Size(carW, carH), r)
        drawRoundRect(line, Offset(left, 0f), Size(carW, carH), r, style = Stroke(2.dp.toPx()))
        // 앞유리·뒷유리
        drawRoundRect(line, Offset(left + carW * 0.12f, carH * 0.17f), Size(carW * 0.76f, carH * 0.10f), CornerRadius(8.dp.toPx()))
        drawRoundRect(line, Offset(left + carW * 0.14f, carH * 0.90f), Size(carW * 0.72f, carH * 0.05f), CornerRadius(6.dp.toPx()))
        // 앞좌석 2개(흐리게), 뒷좌석 3개(상태색)
        val sw = carW * 0.22f; val sh = carH * 0.17f
        fun seat(cx: Float, top: Float, c: Color, a: Float) =
            drawRoundRect(c.copy(alpha = a), Offset(left + carW * cx - sw / 2, top), Size(sw, sh), CornerRadius(8.dp.toPx()))
        seat(0.30f, carH * 0.34f, dim, 0.35f); seat(0.70f, carH * 0.34f, dim, 0.35f)
        seat(0.24f, carH * 0.62f, rl, 0.95f); seat(0.50f, carH * 0.62f, rc, 0.95f); seat(0.76f, carH * 0.62f, rr, 0.95f)
        // 뒷문: 열린 만큼 바깥으로 벌어진다. 인도 쪽 = 초록, 차도 쪽 = 빨강
        fun door(openAmt: Float, isLeft: Boolean, doorId: DoorId) {
            val x = if (isLeft) left else left + carW
            val y0 = carH * 0.55f; val len = carH * 0.28f
            val color = if (doorId == curb.rearDoor) okC else dangerC
            val ex = x + (if (isLeft) -1 else 1) * len * 0.75f * openAmt
            val ey = y0 + len * (1f - 0.35f * openAmt)
            drawLine(if (openAmt > 0.02f) color else line, Offset(x, y0), Offset(ex, ey), strokeWidth = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
        door(leftOpen, true, DoorId.REAR_LEFT); door(rightOpen, false, DoorId.REAR_RIGHT)
        // 인도 쪽 표시선
        val curbX = if (curb == CurbSide.RIGHT) w - 2.dp.toPx() else 2.dp.toPx()
        drawLine(okC.copy(alpha = 0.55f), Offset(curbX, carH * 0.15f), Offset(curbX, carH * 0.85f), strokeWidth = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
private fun ChecklistScreen(s: AppState.ExitChecklist, act: (UserAction) -> Unit) {
    var passengerView by remember { mutableStateOf(false) }
    if (passengerView) {
        Page {
            StatusCard(Icons.Filled.Info, P.warn, "함께 내리시는 분께", Phrases.PASSENGER_TIP)
            SecondaryButton("돌아가기", Modifier.fillMaxWidth()) { passengerView = false }
        }
        return
    }
    Page {
        SectionTitle("내리기 전에 확인하세요")
        // 진행률: 확인할 때마다 막대가 차오른다
        val total = s.items.size
        val doneCount = s.items.count { it.id in s.acknowledged }
        val progress by animateFloatAsState(if (total == 0) 1f else doneCount / total.toFloat(), tween(500, easing = FastOutSlowInEasing), label = "progress")
        Column(Modifier.fillMaxWidth().reveal(0), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (doneCount == total) "모두 확인했어요" else "${total}개 중 ${doneCount}개 확인", fontSize = BODY.sp, color = P.dim)
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(P.surfaceHi)) {
                Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(Brush.horizontalGradient(listOf(P.accent, P.ok))))
            }
        }
        if (s.timedOut) Text("일부 항목은 시간 안에 읽지 못했어요", fontSize = BODY.sp, color = P.warn)
        s.items.forEachIndexed { index, item ->
            val done = item.id in s.acknowledged
            val unknown = item.status == ItemStatus.Unverifiable
            val toneTarget = if (done) P.ok else if (unknown) P.warn else P.accent
            val tone by animateColorAsState(toneTarget, tween(380), label = "itemTone")
            val rear = item.kind == ItemKind.REAR
            Box(Modifier.reveal(index + 1)) {
                ListCard {
                    Row(verticalAlignment = Alignment.Top) {
                        IconBadge(if (done) Icons.Filled.CheckCircle else if (unknown) Icons.Outlined.Info else Icons.Filled.Warning, tone, 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            if (unknown) Text("확인 불가", fontSize = LABEL.sp, color = P.warn, fontWeight = FontWeight.Medium)
                            Text(item.text, fontSize = TITLE.sp, color = if (done) P.dim else P.text, maxLines = 4)
                        }
                    }
                    // 확인하면 버튼이 접히며 사라진다
                    AnimatedVisibility(!done, enter = fadeIn() + expandVertically(), exit = fadeOut(tween(160)) + shrinkVertically(tween(260))) {
                        PrimaryButton(if (rear) "모두 내렸어요" else "확인했어요", Modifier.fillMaxWidth(), small = true) {
                            act(if (rear) UserAction.AllRearExited else UserAction.Acknowledge(item.id))
                        }
                    }
                }
            }
        }
        if (s.rearItem != null) GhostButton("동승자용 안내 보기") { passengerView = true }
    }
}

@Composable
private fun DepartPrepScreen(s: AppState.DepartPrep, act: (UserAction) -> Unit) = Page(footer = {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        if (!s.guideOnly) PrimaryButton("시작", Modifier.weight(1f)) { act(UserAction.ConsentRoutine) }
        SecondaryButton(if (s.guideOnly) "확인" else "다음에", Modifier.weight(1f)) { act(UserAction.DeclineRoutine) }
    }
}) {
    SectionTitle(if (s.guideOnly) "직접 해 주세요" else "서리 제거를 시작할까요?")
    if (s.guideOnly) Text("차량 제어를 쓸 수 없어 안내만 드려요", fontSize = BODY.sp, color = P.warn)
    s.plan.forEach {
        when (it) {
            is PlannedStep.Run -> ListCard { StepRow(Icons.Outlined.CheckCircle, P.accent, it.step.label, null) }
            is PlannedStep.Skip -> ListCard { StepRow(Icons.Outlined.Info, P.warn, it.step.label, "${it.reason} · ${it.step.manualHint}") }
        }
    }
}

@Composable
private fun RoutineScreen(
    title: String, plan: List<PlannedStep>, outcomes: List<StepOutcome>, running: Boolean,
    footer: @Composable () -> Unit,
) = Page(footer = { footer() }) {
    SectionTitle(title)
    val done = outcomes.associateBy { it.step }
    val steps = if (plan.isNotEmpty()) plan.map { it.step } else outcomes.map { it.step }
    steps.forEach { step ->
        val o = done[step]
        val r = o?.result
        // 확인하지 못한 결과는 성공으로 표시하지 않는다(AC4.2).
        ListCard {
            when {
                o == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    if (running) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), color = P.accent, strokeWidth = 3.dp)
                    } else IconBadge(Icons.Outlined.CheckCircle, P.dim, 48.dp)
                    Spacer(Modifier.width(14.dp)); Text(step.label, fontSize = TITLE.sp)
                }
                r == StepResult.Success -> StepRow(Icons.Filled.CheckCircle, P.ok, step.label, "완료")
                r == null -> StepRow(Icons.Outlined.Info, P.warn, step.label, "건너뜀 (${o.skippedReason ?: ""}) · ${step.manualHint}")
                r is StepResult.Failed -> StepRow(Icons.Filled.Warning, P.danger, step.label, "실패 (${r.reason}) · ${step.manualHint}")
                r == StepResult.Unverified -> StepRow(Icons.Filled.Info, P.warn, step.label, "켜졌는지 확인하지 못했어요 · ${step.manualHint}")
                else -> StepRow(Icons.Outlined.Info, P.warn, step.label, "지원하지 않아요 · ${step.manualHint}")
            }
        }
    }
}

/** 설명이 없는 한 줄짜리는 아이콘과 세로 가운데 정렬, 설명이 있으면 위쪽 정렬. */
@Composable
private fun StepRow(icon: ImageVector, tone: Color, title: String, detail: String?) = Row(
    verticalAlignment = if (detail == null) Alignment.CenterVertically else Alignment.Top,
) {
    IconBadge(icon, tone, 48.dp)
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
        Text(title, fontSize = TITLE.sp)
        if (detail != null) Text(detail, fontSize = LABEL.sp, color = tone, maxLines = 3)
    }
}

@Composable
private fun SettingsScreen(settings: MutableStateFlow<Settings>) {
    val s by settings.collectAsState()
    Page {
        SectionTitle("설정")
        Text("안내", fontSize = LABEL.sp, color = P.dim, fontWeight = FontWeight.Medium)
        ToggleRow("음성 안내", s.voice) { settings.value = s.copy(voice = it) }
        ToggleRow("등·하원 픽업 모드", s.pickup) { settings.value = s.copy(pickup = it) }
        ChoiceRow("정차 방향 (내리는 쪽)", CurbSide.entries, s.curbSide, { it.label }) { settings.value = s.copy(curbSide = it) }
        Text("하차 전 점검", fontSize = LABEL.sp, color = P.dim, fontWeight = FontWeight.Medium)
        ToggleRow("주정차 구역 경고", s.zoneWarning) { settings.value = s.copy(zoneWarning = it) }
        ToggleRow("창문 점검", s.windows) { settings.value = s.copy(windows = it) }
        ToggleRow("도어 점검", s.doors) { settings.value = s.copy(doors = it) }
        ToggleRow("뒷좌석 점검", s.rear) { settings.value = s.copy(rear = it) }
        Text("출발 전", fontSize = LABEL.sp, color = P.dim, fontWeight = FontWeight.Medium)
        ToggleRow("시야 확보 루틴", s.routine) { settings.value = s.copy(routine = it) }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    ListCard(minHeight = 72.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = TITLE.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = value,
                onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onChange(it) },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = P.accent, checkedThumbColor = Color.White,
                    uncheckedTrackColor = P.surfaceHi, uncheckedThumbColor = P.dim, uncheckedBorderColor = P.line,
                ),
            )
        }
    }
}

/** 선택지 중 하나를 고르는 줄. 선택된 항목은 색뿐 아니라 ✓ 글자로도 표시한다. */
@Composable
private fun <T> ChoiceRow(label: String, options: List<T>, selected: T, name: (T) -> String, onSelect: (T) -> Unit) {
    val haptic = LocalHapticFeedback.current
    // 라벨이 길어 칩 옆에서 두 줄로 접히지 않도록, 라벨을 위에 두고 칩을 아래에 같은 폭으로 나란히 둔다
    ListCard(minHeight = 72.dp) {
        Text(label, fontSize = TITLE.sp, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            options.forEach { o ->
                val on = o == selected
                val bg by animateColorAsState(if (on) P.btnFill else P.surfaceHi, tween(240), label = "choiceBg")
                Box(
                    Modifier.weight(1f).heightIn(min = 52.dp).clip(CircleShape).background(bg)
                        .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSelect(o) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text((if (on) "✓ " else "") + name(o), fontSize = BODY.sp, fontWeight = FontWeight.Medium, color = if (on) P.onBtnFill else P.text, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DevPanel(state: AppState, recent: List<String>, backend: Backend) = Page {
    SectionTitle("개발자 패널")
    Mono("backend: ${backend.name}")
    Mono("state: ${state::class.simpleName}")
    Mono("detail: ${state.toString().take(300)}")
    Mono("최근 음성:")
    recent.forEach { Mono("  · $it") }
    Mono("※ 위치 좌표는 기록하지 않습니다.")
}

// ---------------- 공통 컴포넌트 ----------------

/** 화면 본문: 위에서부터 카드를 쌓고, 길면 스크롤한다. */
/**
 * 화면 본문: 위에서부터 카드를 쌓고, 길면 본문만 스크롤한다.
 * [footer]는 스크롤 영역 밖 하단에 고정되어, 글이 길어져 줄이 늘어나도 주요 버튼이 잘리거나 밀려나지 않는다.
 */
@Composable
private fun Page(footer: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxSize()) {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
    )
    if (footer != null) Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = footer)
}

@Composable
private fun SectionTitle(text: String) = Text(text, fontSize = HEADLINE.sp, fontWeight = FontWeight.SemiBold, maxLines = 3)

/** 아이콘 + 제목 + 설명을 가진 상태 카드. 아이콘은 항상 텍스트와 함께 쓴다. */
@Composable
private fun StatusCard(icon: ImageVector, tone: Color, title: String, sub: String?, note: String? = null) {
    // 상태가 바뀌면 색이 부드럽게 넘어가고, 텍스트는 크로스페이드된다
    val t by animateColorAsState(tone, tween(380), label = "statusTone")
    Row(
        Modifier.fillMaxWidth().clip(CardShape)
            .background(Brush.horizontalGradient(listOf(t.copy(alpha = 0.14f), P.surface)))
            .border(BorderStroke(1.dp, t.copy(alpha = 0.35f)), CardShape).padding(20.dp).reveal(0),
        verticalAlignment = Alignment.Top,
    ) {
        val alert = tone == P.warn || tone == P.danger
        val compact = LocalWide.current // 낮은 가로 화면: 작게
        val badge = if (compact) 48.dp else 64.dp
        if (alert) AlertRing(t, badge) { IconBadge(icon, t, badge, modifier = Modifier.pop(icon)) }
        else IconBadge(icon, t, badge, modifier = Modifier.pop(icon))
        Spacer(Modifier.width(16.dp))
        AnimatedContent(
            targetState = Triple(title, sub, note), modifier = Modifier.weight(1f), label = "statusText",
            transitionSpec = { fadeIn(tween(260, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
        ) { (ti, su, no) ->
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
                Text(ti, fontSize = (if (compact) TITLE else HEADLINE).sp, fontWeight = FontWeight.SemiBold, maxLines = 3)
                if (su != null) Text(su, fontSize = (if (compact) BODY else TITLE).sp, color = P.dim, maxLines = 3)
                if (no != null) Text(no, fontSize = BODY.sp, color = P.dim, maxLines = 3)
            }
        }
    }
}

/** 가운데 정렬 상태 표시(주행 중 차단, 확인 중처럼 다른 조작이 없는 화면). */
@Composable
private fun CenterMessage(icon: ImageVector?, tone: Color, title: String, sub: String?, busy: Boolean = false) = Column(
    Modifier.fillMaxSize().padding(bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
) {
    if (busy) CircularProgressIndicator(Modifier.size(56.dp), color = tone, strokeWidth = 5.dp)
    else if (icon != null) IconBadge(icon, tone, 88.dp, circle = true)
    Spacer(Modifier.height(24.dp))
    Text(title, fontSize = HEADLINE.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 3)
    if (sub != null) {
        Spacer(Modifier.height(8.dp))
        Text(sub, fontSize = TITLE.sp, color = P.dim, textAlign = TextAlign.Center, maxLines = 3)
    }
}

@Composable
private fun ListCard(minHeight: Dp = 0.dp, content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().heightIn(min = minHeight).clip(CardShape).background(P.surface)
        .border(BorderStroke(1.dp, P.line), CardShape).padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
)

@Composable
private fun IconBadge(icon: ImageVector, tone: Color, size: Dp, circle: Boolean = false, modifier: Modifier = Modifier) = Box(
    modifier.size(size).clip(if (circle) CircleShape else RoundedCornerShape(size / 3.2f)).background(tone.copy(alpha = 0.16f)),
    contentAlignment = Alignment.Center,
) { Icon(icon, null, tint = tone, modifier = Modifier.size(size * 0.55f)) }

@Composable
private fun Pill(text: String, color: Color) = Row(
    Modifier.clip(CircleShape).background(P.surface).border(BorderStroke(1.dp, P.line), CircleShape).padding(horizontal = 14.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    val c by animateColorAsState(color, tween(380), label = "pill")
    LiveDot(c)
    Spacer(Modifier.width(8.dp))
    Text(text, fontSize = LABEL.sp, color = P.text, fontWeight = FontWeight.Medium)
}

@Composable
private fun Caption(text: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(Icons.Outlined.Info, null, tint = P.dim, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    // 한 줄 고정: 문장이 길어져도 하단 높이가 변하지 않아 위쪽 화면이 밀리지 않는다
    Text(text, fontSize = LABEL.sp, color = P.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Mono(text: String) = Text(text, fontSize = LABEL.sp, color = P.dim)

/** 홈의 큰 액션 타일: 아이콘 + 제목 + 한 줄 설명. 한 손으로 누르기 쉬운 큰 면적. */
@Composable
private fun ActionTile(icon: ImageVector, title: String, desc: String, primary: Boolean, modifier: Modifier, compact: Boolean = false, onClick: () -> Unit) {
    val bg = if (primary) P.btnFill else P.surface
    val fg = if (primary) P.onBtnFill else P.text
    val sub = if (primary) P.onBtnFill.copy(alpha = 0.72f) else P.dim
    if (compact) {
        // 낮은 화면용: 아이콘 옆에 글자를 가로로
        Row(
            modifier.clip(CardShape).background(bg).border(BorderStroke(1.dp, P.line), CardShape).pressScale(onClick).padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, if (primary) P.onBtnFill else P.accent, 44.dp, circle = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = TITLE.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
                Text(desc, fontSize = LABEL.sp, color = sub, maxLines = 1)
            }
        }
        return
    }
    Column(
        modifier.clip(CardShape).background(bg)
            .then(if (primary) Modifier else Modifier.border(BorderStroke(1.dp, P.line), CardShape))
            .pressScale(onClick).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon, if (primary) P.onBtnFill else P.accent, 52.dp, circle = true)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = TITLE.sp, fontWeight = FontWeight.SemiBold, color = fg)
            Text(desc, fontSize = LABEL.sp, color = sub, maxLines = 2)
        }
    }
}

/** 모든 버튼은 터치 시 햅틱 피드백(UI 가이드: haptic 또는 audio feedback). */
@Composable
private fun feedback(onClick: () -> Unit): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
}

@Composable
private fun PrimaryButton(label: String, modifier: Modifier = Modifier, small: Boolean = false, onClick: () -> Unit) = Button(
    onClick = feedback(onClick),
    modifier = modifier.heightIn(min = if (small) 56.dp else 64.dp),
    // 버튼 자체의 눌림 모션은 Material 리플을 그대로 쓴다
    shape = CircleShape,
    colors = ButtonDefaults.buttonColors(containerColor = P.btnFill, contentColor = P.onBtnFill),
    contentPadding = PaddingValues(horizontal = 24.dp),
) { Text(label, fontSize = if (small) BODY.sp else TITLE.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }

@Composable
private fun SecondaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) = Button(
    onClick = feedback(onClick),
    modifier = modifier.heightIn(min = 64.dp),
    shape = CircleShape,
    colors = ButtonDefaults.buttonColors(containerColor = P.surfaceHi, contentColor = P.text),
    contentPadding = PaddingValues(horizontal = 24.dp),
) { Text(label, fontSize = TITLE.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }

@Composable
private fun GhostButton(label: String, onClick: () -> Unit) = TextButton(onClick = feedback(onClick), modifier = Modifier.heightIn(min = 56.dp)) {
    Text(label, fontSize = BODY.sp, color = P.accent, fontWeight = FontWeight.Medium)
}
