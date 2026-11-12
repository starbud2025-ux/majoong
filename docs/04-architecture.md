# 04. 아키텍처

## 원칙
1. **core는 순수 Kotlin** (Android·Pleos 의존 없음) — 단위 테스트와 Fake 데모의 기반
2. **Pleos 의존은 어댑터에만** — SDK 이름이 바뀌어도 수정 범위가 작다
3. **Fake 차량으로 모든 시나리오 재현** — 에뮬레이터·GPU 없이도 데모
4. **콜백 기반, 폴링 금지**

## 모듈
```
pleos-app/
  core/                      # 순수 Kotlin (JVM 모듈)
    model/                   # VehicleSnapshot, Zone, ChecklistItem, RoutineStep
    signals/                 # 인터페이스(포트)
    engine/                  # 상태 머신, 점검 로직, 루틴 실행기
    zone/                    # 구역 대조(원·폴리곤)
  app/                       # Android 앱
    pleos/                   # Vehicle / FusedLocation / TTS 어댑터
    aaos/                    # CarUxRestrictions, 기어 속성(폴백)
    fake/                    # FakeVehicle, 시나리오 실행기
    service/                 # 포그라운드 서비스(필요 시)
    ui/                      # Jetpack Compose 화면
    assets/restricted_zones_sample.json
```
`core`가 `signals`의 인터페이스를 정의하고, `pleos`와 `fake`가 각각 구현한다. 빌드 변형(flavor): `pleos`(실제 SDK) / `fake`(SDK 없이 실행). SDK 저장소 접근이 안 되는 환경에서도 fake 빌드가 돌아야 한다.

## 포트(인터페이스) 초안
```kotlin
interface VehicleSignals {
    val gear: Flow<Gear>                      // P, D, R, N, Unknown
    val windows: Flow<Map<Seat, Open>>        // 열림 여부, 읽지 못하면 Unknown
    val doors: Flow<Map<DoorId, DoorState>>
    val rearOccupancy: Flow<Occupancy>        // Occupied / Empty / Unknown
    val warnings: Flow<List<Warning>>
    val climate: Flow<ClimateState>           // 제습, 열선 상태
    suspend fun capability(feature: Feature): Capability
}

interface ClimateController {                 // F4 제어
    suspend fun apply(step: RoutineStep): StepResult   // 결과는 상태 확인 후 반환
}

interface LocationSource { val location: Flow<GeoPoint?> }
interface Speaker { suspend fun speak(text: String) }
interface UxRestrictions { val drivingBlocked: Flow<Boolean> }
interface ZoneRepository { suspend fun zonesNear(p: GeoPoint, radiusM: Int): List<Zone> }
```
- `Unknown`은 "정상"이 아니다. UI·로직 모두 구분한다(AC2.3).
- `StepResult`는 `Success / NotSupported / Failed(reason) / Unverified`.

## 상태 머신
```
Driving ──(gear→P)──▶ Parked
Parked  ──▶ ZoneCheck ──▶ (경고 or 통과) ──▶ ExitChecklist
ExitChecklist ──(모두 확인)──▶ Idle
Idle ──("출발 준비")──▶ DepartPrep ──(동의)──▶ RoutineRunning ──▶ RoutineDone
어느 상태든 (gear≠P 또는 drivingBlocked) ──▶ Blocked(제어·조작 중단)
Blocked ──(gear→P, 제한 해제)──▶ Parked
```
- `Blocked` 진입 시: 진행 중 루틴 취소, 변경한 설정은 되돌리지 않고 알림.
- 모든 전이는 `engine`의 단일 이벤트 루프(단일 스레드 디스패처)에서 처리 → 경쟁 상태 방지.

## 화면
| 화면 | 내용 |
|---|---|
| Home(정차) | 현재 상태 한 줄, "내릴게요" / "출발 준비" 큰 버튼 |
| ZoneWarning | 구역 종류·시간대, "알겠어요" |
| ExitChecklist | 이상 항목 체크리스트, 뒷좌석 동승자용 안내 전환 |
| DepartRoutine | 단계별 진행·결과(✓ / 실패 이유) |
| Blocked | "정차 후 이용하세요" |
| Settings | 음성·점검 항목 on/off |
| 개발자 패널 | 홈 제목 5번 탭: 실시간 원시 신호, capability 결과, 퍼미션, 최근 오류 (심사자 검증용) |

UI는 가로형 인포테인먼트 화면(큰 터치 영역, 큰 글씨). 컴포넌트는 직접 구성하거나 커뮤니티 `Pleos Connect UI`(비공식) 참고. 색 대비와 글자 크기는 차량 UX 기준으로 크게.

## 테스트 전략
| 층 | 도구 | 대상 |
|---|---|---|
| core 단위 | JUnit + 코루틴 테스트 | 구역 대조, 점검 로직, 상태 머신 전이, 루틴 실패 처리 |
| 시나리오 | FakeVehicle 스크립트 | 데모 3분 시나리오 전체 |
| 에뮬레이터 | Pleos Connect Emulator + Virtual Console | 실제 SDK 콜백 확인, 퍼미션 |
| 린트·빌드 | Gradle, CI(GitHub Actions) | `assembleDebug`, `lintDebug` |

핵심 단위 테스트 목록(수용 기준과 1:1): AC1.1~1.4, AC2.1~2.4, AC3.1~3.2, AC4.1~4.4, 정차 전용 차단.

## 설정·보안
- 네트워크·계정 없음(v1). 위치는 메모리에서만 사용하고 저장·전송하지 않는다.
- 로그에 위치 좌표를 남기지 않는다(개발자 패널에는 구역 ID와 거리만).
- 서명 키스토어는 저장소에 올리지 않는다(.gitignore).
