# 03. API 매핑과 확인 필요 항목

> 출처: Pleos Playground 공식 문서(개요·Vehicle·NaviHelper)와 공개 저장소 사례(2026-11-04 조사).
> **"사례"로 표시한 것은 다른 팀 저장소가 쓴다고 밝힌 내용이며 공식 확정이 아닙니다.** 코드에 쓰기 전에 실제 SDK(자동완성, KDoc)와 에뮬레이터로 검증하고, 결과를 이 표의 "검증" 열에 기록하세요.

## 1. 기능별 매핑
| 기능 | 필요 정보·동작 | SDK / API | 근거 | 검증 |
|---|---|---|---|---|
| F1 | 기어 P 전환 감지 | Vehicle Gear 도메인 `registerGearSelection` | 사례(Vehicle 2.3.2부터 Gear) | ☐ |
| F1 | 현재 위치 | FusedLocation SDK | 공식(모듈 존재), 사용법은 사례 | ☐ |
| F1 | 구역 대조 | 앱 내부 로직 + 샘플 GeoJSON | 자체 구현 | ☐ |
| F2 | 창문 상태 | Vehicle Window 도메인 | 공식(도메인 존재) | ☐ |
| F2 | 도어 상태 | Vehicle Door 도메인 | 공식 | ☐ |
| F2 | 경고 정보 | Vehicle Safety 도메인 | 공식 | ☐ |
| F2 | 타이어 상태(선택) | Vehicle Tire 도메인 | 공식 | ☐ |
| F3 | 좌석별 탑승 | Vehicle Seat 도메인 | 사례(골든타임이 좌석별 탑승에 사용) | ☐ |
| F4 | 앞유리 제습 | HVAC `setHvacDefroster` | 사례(ClearView) | ☐ |
| F4 | 와이퍼 | Wiper `setWiperSwitch` | 사례(ClearView) | ☐ |
| F4 | 사이드미러 열선 | SideMirror `setHvacSideMirrorHeat` | 공식(도메인), 메서드명은 사례 | ☐ |
| F4 | 핸들 열선 | Steeringwheel 열선 제어 | 공식(도메인) | ☐ |
| 공통 | 음성 안내 | TextToSpeech SDK (ON_DEVICE, 한국어) | 사례 | ☐ |
| 공통 | 주행 제한 감지 | AAOS `CarUxRestrictionsManager` | AAOS 표준 | ☐ |
| 공통 | 지원 여부 | 각 도메인 `checkXXXCapabilities` | 공식 | ☐ |

## 2. 공통 호출 규칙 (공식 문서)
- 도메인 객체는 직접 생성하지 않고 `Vehicle` 인스턴스의 `getXxx()`로 얻는다 (`getDoor()`, `getBrake()` 등).
- 연결은 `initialize()`, 종료는 `release()`. **비동기 콜백이 끝나기 전에 `release()`를 호출하지 않는다.**
- 조회·설정 결과는 IPC라서 `onComplete`·`onFailed` 비동기 콜백으로 온다.
- 상태 변화 구독은 callback(리스너) 등록.

## 3. 퍼미션 (사례 기준 — 승인 범위 확인 필요)
저장소 사례에서 확인된 퍼미션 이름:
```
pleos.car.permission.CAR_LIGHTS   (v1 불필요, 사용 금지)
pleos.car.permission.CAR_WINDOWS
pleos.car.permission.CAR_CLIMATE
pleos.car.permission.CAR_DOORS
pleos.car.permission.CAR_SEATS
pleos.car.permission.CAR_DRIVE
pleos.car.permission.TTS_SERVICE
android.car.permission.CAR_POWERTRAIN   (기어 등 AAOS 속성)
```
- v1에서 **선언할 후보**: CAR_WINDOWS, CAR_DOORS, CAR_SEATS, CAR_CLIMATE(F4), TTS_SERVICE, 위치 관련(FusedLocation 권한 이름은 SDK 문서로 확인).
- 규칙: **Pleos App Market과 사전 협의·승인된 범위 안에서만** 사용 가능. 미승인 퍼미션 선언·호출 시 심사 탈락. 승인 절차는 App Review Guidelines에서 확인.
- 확인 필요: 제어(set) 계열(HVAC, Wiper, SideMirror, 핸들 열선)이 공모전 참가자에게 개방되어 있는지. 일부 사례는 에뮬레이터에서만 제어하고 "디버그 빌드에서만 제어"로 표기함.

## 4. 확인 필요 항목 (우선순위 순)
| # | 질문 | 확인 방법 | 결과에 따른 결정 |
|---|---|---|---|
| 1 | 제어 계열 퍼미션이 공모전에서 쓸 수 있는가 | 프로젝트 생성 후 사이트의 SDK 사용 신청, 심사 가이드, FAQ | 불가면 F4는 "안내 모드" |
| 2 | Seat 도메인에서 좌석별 **탑승(점유)** 값을 읽을 수 있는가 | API Reference(Seat), `checkSeatCapabilities`, 에뮬레이터 Virtual Console | 불가면 F3은 일반 문구 |
| 3 | 도어 **열림 시도** 이벤트가 있는가 | Door 콜백 목록 | 없으면 UC2 트리거를 "내릴게요" 버튼으로 |
| 4 | Gear 도메인 사용법과 퍼미션 | Vehicle 2.3.2 API Reference | 없으면 AAOS `GEAR_SELECTION` 속성 사용 |
| 5 | 서리 루틴 각 메서드의 정확한 이름·인자·범위 | SDK 클래스 탐색, KDoc | 이름이 다르면 매핑표 수정 |
| 6 | TTS SDK 사용 가능·초기화 방법 | TextToSpeech 문서 | 불가면 화면 안내만 |
| 7 | 앱의 네트워크 사용 허용 여부 | 심사 가이드 | v1은 네트워크 없이 설계(영향 없음) |
| 8 | 외기 온도 신호 존재 여부 | HVAC/CarInfo 도메인 | 없으면 시간·사용자 선택으로 루틴 제안 |
| 9 | 주행 중 성능 기준(CPU·RAM) | 심사 가이드 (제3자 요약: CPU 5% 미만, RAM 400MB 이하, 폴링 최소화) | 콜백 구독 위주, 폴링 금지 |

## 5. 사용하지 않는 것 (v1)
NaviHelper(경로 안내는 범위 밖), ADAS, LLM, STT, Gleo Interface(음성 호출 연동은 v2), Fleet/Vehicle Data API(서버형).

## 6. 구역 데이터
- v1: `assets/restricted_zones_sample.json`에 샘플 구역 5~10곳(원형 또는 폴리곤) 내장. 필드: `id, name, type(소화전/횡단보도/버스정류장/어린이보호구역 등), geometry, activeHours`.
- 실제 공공 데이터 연동 가능 여부는 확인 필요(출처 미확인). 기획서에는 "공공 데이터 연동은 확장"으로만 기술하고, **실제 단속 구역과의 일치를 주장하지 않는다.**

## 7. 공식 문서 검증 결과 (2026-11-08, document.pleos.ai 직접 확인)
출처: Getting Started, Vehicle SDK API Reference(2.3.1 기준 문서), Declaring permission, SDK API Policy, Driving Restriction Policy, App Review Guidelines.
**아래 시그니처는 문서에 적힌 것이며, 실제 SDK 자동완성·에뮬레이터 호출로 다시 확인할 것.**

### 7.1 확인된 API
| 기능 | API (문서 시그니처) | 퍼미션 |
|---|---|---|
| 도어 상태 | `door.getCurrentDoorPosition(area = DoorArea(DoorValue.ROW_1_LEFT), onComplete: (Int?) -> Unit, onFailed)`, 변화 구독 `registerDoorPosition` | CAR_DOORS |
| 좌석 탑승 | `seat.getCurrentSeatOccupancy(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed)`, 지원 확인 `checkSeatOccupancyCapabilities` | CAR_SEATS (normal) |
| 앞유리 제습 | `hvac.setHvacDefroster(area: WindowArea, value: Boolean, onComplete: (WindowArea, Boolean?) -> Unit, onFailed)`, 상태 `getHvacDefrosterState` / `registerHvacDefrosterState` | 제어 CONTROL_CAR_CLIMATE (normal), 조회 CAR_CLIMATE |
| 와이퍼 | `wiper.setWiperSwitch(area: WindowArea, value: WiperSwitch, onComplete, onFailed)`, 상태 `getCurrentWiperState` / `registerWiperState`, 지원 `checkWiperSwitchCapabilities` | 제어 CONTROL_CAR_WINDOWS (normal), 조회 CAR_WINDOWS |
| 핸들 열선 | `hvac.setHvacSteeringWheelHeat`, 상태 `getHvacSteeringWheelHeatState` (**HVAC 클래스**) | 제어 CONTROL_CAR_CLIMATE, 조회 CAR_CLIMATE |
| 사이드미러 열선 | `hvac.setHvacSideMirrorHeat`, 상태 `getHvacSideMirrorHeatState` (**HVAC 클래스**) | 제어 CONTROL_CAR_MIRRORS, 조회 CAR_MIRRORS |
| TTS | `TextToSpeech.initialize(listener: OnInitializeListener)`, `addEventListener` (SDK v3.3.1) — speak 메서드명은 미확인 | (TTS 문서에서 확인 필요) |

### 7.2 확인 필요 항목 결과
| # | 결과 |
|---|---|
| 1 제어 퍼미션 | **API 문서상 CONTROL_CAR_CLIMATE/WINDOWS/MIRRORS는 보호 수준 normal**. 단, 공모전 참가자에게 개방되는지는 문서로 알 수 없음 → 프로젝트 SDK 신청·App Market 협의에서 사용자가 확인 |
| 2 좌석 탑승 | **가능** (`getCurrentSeatOccupancy`). 차량·에뮬레이터 지원은 `checkSeatOccupancyCapabilities`로 런타임 확인 |
| 3 도어 열림 시도 | **없음**. `registerDoorPosition`/`registerDoorLockState`만 존재 → UC2 트리거는 "내릴게요" 버튼 유지 |
| 4 기어 | **Vehicle SDK(2.3.1 문서)에 Gear 도메인 없음.** SDK API Policy: 기어·속도·외기온도는 **AAOS Car API(표준)** 사용 권장 → `aaos/` 어댑터로 구현 |
| 5 서리 루틴 | 위 7.1대로 메서드명 확인. 사이드미러·핸들 열선은 `HVAC`에 있음(Seat/SideMirror 클래스 아님) |
| 6 TTS | SDK 존재(initialize/리스너). speak 시그니처는 TTS 문서에서 추가 확인 |
| 7 네트워크 | 허용 여부 명시 없음. 위치 데이터 사용 앱은 **이용약관·데이터 처리방침 제출 필요**(Review Guidelines) → 제출물에 개인정보 처리방침 추가 |
| 8 외기 온도 | AAOS Car API 경로(SDK API Policy) |
| 9 성능 | Review Guidelines에 수치 없음("성능 저하 없을 것"). 3rd party 요약의 수치는 미확인 유지 |

### 7.3 정차 전용 정책 (Driving Restriction Policy by Region)
- DO(Distraction Optimization)를 선언하지 않은 앱: **주차(Parking) 허용, 주행(Drive) 불허** → 우리 설계와 일치.
- CarUxRestrictions 연동은 "Driving Restriction Development Guide"에서 확인(미열람).
- Door·Seat·Vehicle settings 범주는 북미에서 주행 중 UX_RESTRICTIONS_NO_SETUP 적용.

### 7.4 퍼미션 승인 (2026-11-08 사용자 승인 완료)
사용자가 승인한 추가 퍼미션: `CONTROL_CAR_CLIMATE`, `CONTROL_CAR_WINDOWS`, `CONTROL_CAR_MIRRORS`, `CAR_MIRRORS`, `CAR_SAFETY`. `app/src/pleos/AndroidManifest.xml`에만 선언(fake 빌드는 퍼미션 없음). **Pleos App Market의 사전 협의·승인 범위는 별도 확인 필요**(미승인 선언 시 심사 탈락). 이름 미확인이라 아직 선언하지 않은 것: TTS 서비스, FusedLocation, AAOS 기어/외기온도.

## 8. 추가 검증 (2026-11-08, 2차)
| 항목 | 결과 |
|---|---|
| CarUxRestrictions | AAOS `CarUxRestrictionsManager.registerListener()` 후 `onUxRestrictionsChanged()`로 수신. 플랫폼이 지역 규정에 맞는 `ActiveRestrictions`를 전달하므로 앱은 받은 값만 적용. DO 미선언 앱은 주행 중 사용 불가(플랫폼 기본) |
| TTS | `ai.pleos.playground.tts.TextToSpeech(context, Mode.ON_DEVICE)` → `initialize()` → `addEventListener()` → (필요 시 `setLocale()`) → `speak(text)` / `stop()` → `removeEventListener()`, `release()`. **ON_DEVICE는 네트워크·앱 등록 불필요**(HYBRID가 기본값이므로 반드시 ON_DEVICE 지정). 퍼미션 `pleos.car.permission.TTS_SERVICE` |
| FusedLocation | `isSupported()` 확인 → `initialize()` → `getLastKnownLocation()`(마지막 위치, null 가능) 또는 `registerFusedLocationCallback()` → `release()`. 결과 `LocationData`(위도·경도·고도·방향·confidence·timestamp). 퍼미션 `pleos.car.permission.FUSED_LOCATION`. Android 위치 권한 동작은 SDK 문서가 보장하지 않음 |
| SDK 버전(문서 기준) | Vehicle 2.3.1, TextToSpeech 3.3.1, FusedLocation 1.1.2. (CLAUDE.md의 2.3.2/1.1.3은 문서에서 확인 안 됨 → 실제 Maven 해석 결과로 확정) |
| 구역 경고 설계 | UC1은 `getLastKnownLocation()` 1회 호출이면 충분(폴링·지속 구독 불필요) |

### 8.1 Pleos Connect UI Design Guide 적용 (반영 위치: `ui/Screens.kt`)
- 색: 공식 토큰 사용(다크 `#131417/#1D1E21/#313236`, informative `#00E074/#0082FF/#FF4C28`), 텍스트·아이콘 대비 4.5:1 이상. 주야간 자동(시스템 테마). 경고(주의)색은 토큰에 없어 앰버 사용 → **디자이너 확인 필요**
- 글자: 중립 서체, 최소 12sp, 핵심 32/24/20/16sp, 문장 3줄 이내. 공식 서체(Asta Sans)는 시스템 UI 서체라 앱에 포함하지 않음
- 모션(2026-11-09 원문 재확인으로 정정): Easy & Safe 원칙 원문은 "운전 중 동영상, 애니메이션 이미지, 텍스트 자동 스크롤과 같이 운전자의 시선을 유도하면 안 됩니다". 금지 대상은 **운전 중**의 시선 유도다. Animation 파운데이션은 "애니메이션은 사용자에게 상태를 설명하기 위해 쓴다"(Linear: 정적 로딩, Tension: 긴 로딩). 정차 전용 앱이므로 정차 화면의 상태 설명 모션은 허용, 주행 중(Blocked)에는 반복 모션을 멈춘다. 이전에 "장식 애니메이션 전부 제거"라고 적은 것은 과도한 요약이었다. 출처: document.pleos.ai connect/guide/docs-design (Introduction, Foundations/Animation)
- 아이콘은 텍스트 라벨과 함께, 터치 햅틱 피드백, 한 손 조작(터치 영역 56~68dp)
- 방향: `screenOrientation` 미지정(세로 1창 기본) + 폭 720dp 이상이면 측면 패널 추가(가로 2창)
- 다국어: 가이드는 다국어 설계 권장. 현재 문구는 한국어 상수(core `Phrases`) → 영어 지원은 v2

### 8.2 SDK 사용 신청 상태 (문서 기준, "Create a Project and Request SDK Access")
- **Not Available Yet(신청 불가, Coming Soon)**: FusedLocation, TextToSpeech, SpeechToText, **Vehicle Control**
- **Ready to Use(신청 없이 사용)**: Vehicle State, NaviHelper
- 영향(실제 내 프로젝트 화면의 현재 상태로 재확인 필요 — 로그인 필요):
  - F4 서리 루틴(제어 계열) → Vehicle Control이 계속 불가면 **안내 모드 확정**(코드는 이미 degrade 구현됨)
  - F1 위치 → FusedLocation 불가면 AAOS `LocationManager` 등 대체 또는 "위치 확인 불가" 안내(AC1.3 경로)
  - 음성 → TTS SDK 불가면 플랫폼 `TextToSpeech`(현재 fake 빌드 방식) 사용
- 프로젝트 정보의 **Project ID(Key)**는 App Market Console 심사 요청 시 입력. Client ID/Secret은 서버 연결형 SDK(TTS 등록, STT)용 — ON_DEVICE TTS는 불필요

### 8.3 내 프로젝트 SDK 상태 (2026-11-08 21:04 프로젝트 "내리기 전에" 생성 후 화면 확인)
- **사용 가능**: Vehicle State, NaviHelper
- **Coming Soon(신청 불가)**: FusedLocation, TextToSpeech, SpeechToText, **Vehicle Control**
- 결정:
  - F4 서리 루틴: **Vehicle Control이 열릴 때까지 안내 모드가 기본**. 제어 코드는 capability 결과로 자동 전환되도록 유지하되 제출·시연에서는 "안내 모드"로 정직하게 표기. 제어 퍼미션(CONTROL_CAR_*)은 개방 전에는 선언해도 동작하지 않을 수 있음 → 심사 리스크이므로 **제출 APK(pleos 변형)에서는 CONTROL_CAR_* 선언 보류 권장**(사용자 결정 필요)
  - F1 위치: FusedLocation 불가 → AAOS 표준 위치(`LocationManager`) 경로를 검토하거나 "위치 확인 불가" 안내(AC1.3)
  - 음성: TTS SDK 불가 → 플랫폼 TextToSpeech(현재 구현) 유지
  - F2/F3 하차 점검: Vehicle State(Door/Window/Seat/Safety) 기반이라 영향 없음(핵심 기능으로 유지)

## 9. 등·하원 픽업 모드(메인 기능) API 검증 (2026-11-08, 문서 기준 — 에뮬레이터 재검증 필요)
| 필요 정보 | API (문서 시그니처) | 퍼미션 | 상태 |
|---|---|---|---|
| 좌석별 탑승 | `seat.getCurrentSeatOccupancy(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed)` | CAR_SEATS (normal) | 확인 |
| 좌석별 안전벨트 | `seat.getCurrentSeatBeltBuckledState(area: SeatArea, onComplete: (Boolean?) -> Unit, onFailed)`; 벨트 미착용 경고 `hasSeatBeltNotBuckledWarning` | CAR_SEATS (normal) | 확인 |
| 좌석 구분 | `SeatArea(vararg values: SeatValue)`, `SeatValue`: `ROW_1/2/3` × `LEFT/CENTER/RIGHT` → 뒷줄(ROW_2) 좌·중·우 개별 조회 가능 | - | 확인 |
| 도어 열림 | `door.getCurrentDoorPosition(area = DoorArea(DoorValue.ROW_1_LEFT), onComplete: (Int?), onFailed)`, `registerDoorPosition` | CAR_DOORS | 확인 |
| 도어 잠금 | `registerDoorLockState` / `unregisterDoorLockState` + `DoorLockStateListener.onDoorLockStateUpdated`, `checkDoorLockCapabilities` | CAR_DOORS | 확인(getter 이름은 미열람) |
| 지원 여부 | `checkSeatOccupancyCapabilities`, `checkDoorPositionCapabilities`, `checkDoorLockCapabilities` | 각 도메인 | 확인 |
| 차일드락 | **전용 API 없음 확인**. `TelltaleType`에 `CHILD_PRESENCE_DETECTION_OFF`, `TRUNK_OPEN` 등 경고등 항목이 있음(경고등 조회 경로는 미확인) | CAR_SAFETY(추정) | 부분 |

- 모두 **조회(CAR_*)** 계열이라 제어 퍼미션 없이 동작 → Vehicle Control이 "Coming Soon"이어도 영향 없음.
- 심사 가이드(User Privacy & Security): 아동 개인정보는 보호자 사전 고지·동의 필요. **우리 앱은 아동 정보를 수집·저장하지 않는다(좌석 점유 불리언만 메모리에서 사용)**고 기획서·처리방침에 명시.
- App Categories의 "Kids"는 아동 대상 콘텐츠 분류 → 우리는 보호자(운전자) 대상이므로 **모빌리티 서비스 유지**.

## 10. 실제 SDK(AAR) 직접 검증 (2026-11-09) — 문서보다 이 절이 우선
Pleos Maven(`nexus-playground.pleos.ai`)은 공개 읽기가 가능해 `Vehicle-2.3.2.aar`를 받아 `javap`와 Android lint로 시그니처·퍼미션을 확인했다. 최신 버전은 Vehicle **2.3.2**(메타데이터: 2.0.3, 2.3.2).

| 항목 | 문서(§7~9) | 실제 AAR 결과 |
|---|---|---|
| 기어 | Vehicle SDK에 Gear 없음, AAOS 사용 | **`Vehicle.gear` 있음**: `getCurrentGearSelectionState`, `registerGearSelection(GearSelectionListener.onSelected(VehicleGear))`. 값 `GEAR_PARK/DRIVE/REVERSE/NEUTRAL/1~9`. **퍼미션 `CAR_DRIVE` 필요**(lint) → 사용자 승인 후 pleos 매니페스트에 선언 |
| 핸들·사이드미러 열선 | HVAC 클래스 | **`SteeringWheel.setHvacSteeringWheelHeat(Int)`, `SideMirror.setHvacSideMirrorHeat(MirrorArea, Int)`**. 단계 Int의 의미 미확인 → **제어하지 않고 수동 안내** |
| 도어·창문 위치 | Int | 의미(0=닫힘?) 문서·AAR에 없음. `check*PositionCapabilities`의 `AreaBounds.minValue`를 "닫힘"으로 가정 → **에뮬레이터 검증 필요** |
| 경고등 | Safety 경고 | `Safety.registerTelltales/getTelltalesInfo` 있고 `TelltaleType.DOOR_OPEN/TRUNK_OPEN` 있으나 **퍼미션 `PRIVATE_CAR_SAFETY` 필요** → 사용 안 함(사용자 결정). 점검은 "경고 정보 확인 불가" |
| 제어 | normal 퍼미션 | `HVAC.setHvacDefroster(WindowArea, Boolean)`, `Wiper.setWiperSwitch(WindowArea, WiperSwitch.MIST)`는 존재. 단 Vehicle Control SDK는 Coming Soon(§8.3) |
| 좌석 | 확인 | `Seat.getCurrentSeatOccupancy/BeltBuckledState`, 리스너, `check…Capabilities` 모두 확인(앱은 CAR_SEATS) |

구현: `app/src/pleos/.../BackendFactory.kt`(pleos 변형만 SDK 의존). `drivingBlocked = (gear != P)`이며 읽지 못하면 차단. `CarUxRestrictions`는 `android.car` 접근 경로 미확인이라 아직 연결하지 않았다. FusedLocation은 신청 불가라 위치 없음(AC1.3 경로). 제어 결과는 제습만 상태를 다시 읽어 확인하고, 와이퍼 1회는 `Unverified`로 보고한다.

## 11. 프로젝트 SDK 상태 재확인 (2026-11-09 저녁, Pleos 사이트 로그인 후 직접 확인)
| SDK | 상태 |
|---|---|
| Vehicle State | 11개 항목 모두 **사용 가능**(차량 주행 정보·도어/트렁크·안전 상태·좌석·사이드미러·창문 포함) |
| Vehicle Control | 8개 항목 모두 **준비 중** → 서리 루틴 제어는 안내 모드 유지 |
| FusedLocation | 정밀 위치 정보 조회 **준비 중** → 구역 경고는 "위치 확인 불가" |
| TextToSpeech | 이번에는 미확인(11/8: 준비 중) |
- 기어 조회(`CAR_DRIVE`)는 "차량 주행 정보 조회"(사용 가능)에 해당하는 것으로 보이나 항목 설명에 기어가 명시되지는 않음 → 에뮬레이터에서 확인.
- 프로젝트 이름은 아직 "내리기 전에"(2026-11-08 생성). 앱 이름을 "마중"으로 바꾸려면 프로젝트 정보에서 수정(사용자 몫).
- 프로젝트 정보 화면에 SDK 클라이언트 ID·시크릿과 테스트용 CRN(데브박스 등록 adb 명령)이 있다. **시크릿은 문서·코드에 옮기지 않는다.**
