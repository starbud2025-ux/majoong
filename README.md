# 마중 — 등·하원 픽업 도우미 (팀 별틔움)

학교·학원 앞 짧은 정차에서 **아이가 타고 내리는 한 번의 흐름**(좌석별 안전벨트 확인 → 인도 쪽 문 안내 → 마무리 점검)을 돕는 **정차 전용** Pleos Connect 앱.
보조 기능: 주정차 금지 구역 경고, 출발 전 서리·시야 확보 루틴.

> 상태(2026-11-12): core 로직·화면·Fake 데모 완료(단위 테스트 47개 통과). Pleos Vehicle SDK 2.3.2 연결 코드 작성 완료(빌드·lint 통과), **Pleos Connect Emulator 동작 검증은 아직 못 했다.**

## 화면
`docs/screenshots/` (에뮬레이터·실기기 캡처, `device/`는 실기기).

## 실행 방법
요구: JDK 17 이상(Android Studio의 jbr 가능), Android SDK 35.
```
export JAVA_HOME=<JDK 17+>
./gradlew :core:test                    # 순수 로직 테스트 47개
./gradlew :app:assembleFakeDebug        # SDK 없이 도는 데모 빌드
./gradlew :app:assemblePleosDebug       # Pleos Vehicle SDK 연결 빌드
./gradlew :app:lintFakeDebug :app:lintPleosDebug
```
- **fake 빌드(`kr.pleosapp.getoff.fake`)**: 가상 차량으로 모든 시나리오를 재현한다. 앱 하단 "데모 패널"로 기어·좌석·벨트·도어·창문을 조작한다.
  - 시연 순서: `구역 밖 P 정차` → 홈에서 `픽업 모드` → `[픽업] 아이 2명 탑승` → `[픽업] 벨트 착용` → `[픽업] 뒷문 왼쪽 열림`(차도 쪽 경고) → `[픽업] 뒷문 닫음`/`오른쪽 열림` → `모두 내렸어요` → `④ 주행 시작(D)`(차단).
- **pleos 빌드**: 실제 차량·Pleos Connect Emulator(API 34, Cold boot, GPU 필요)에서만 값이 들어온다. 일반 안드로이드 기기에서는 기어를 읽지 못해 항상 "정차 후 이용하세요" 차단 화면이다(의도된 동작).

## 테스트 환경
- 단위 테스트(JUnit, 코루틴 테스트) 47개: 점검·구역 대조·루틴·픽업 수용 기준(AC5.1~5.6)·상태 머신.
- fake 빌드는 Android 에뮬레이터(API 36/37)와 실기기(Android 17)에서 시나리오를 확인했다.
- Pleos Connect Emulator 2.1.0: **미검증**.

## 사용 SDK와 퍼미션 (pleos 빌드)
`ai.pleos.playground:Vehicle:2.3.2` (`https://nexus-playground.pleos.ai/repository/maven-releases/`). 시그니처는 AAR `javap`와 lint로 확인(`docs/03` §10).

| 용도 | API | 퍼미션 |
|---|---|---|
| 좌석 탑승·벨트 | `Seat.getCurrentSeatOccupancy / BeltBuckledState`, `register…` | CAR_SEATS |
| 도어·창문 열림 | `Door/Window.getCurrent…Position`, `check…PositionCapabilities` | CAR_DOORS, CAR_WINDOWS |
| 기어(P 판별) | `Gear.getCurrentGearSelectionState`, `registerGearSelection` | CAR_DRIVE |
| 서리 제습·와이퍼 1회 | `HVAC.setHvacDefroster`, `Wiper.setWiperSwitch(MIST)` | CONTROL_CAR_CLIMATE, CONTROL_CAR_WINDOWS |
| 선언만 한 것 | CAR_CLIMATE, CAR_MIRRORS, CAR_SAFETY, CONTROL_CAR_MIRRORS, TTS_SERVICE, FUSED_LOCATION | 해당 기능은 아래 한계 참고 |

fake 빌드는 퍼미션·SDK 의존성이 없다.

## 개인정보
위치·좌석 정보는 기기 안 메모리에서만 쓰고 저장·전송하지 않는다. 계정·서버·네트워크 사용 없음. 아동의 이름·사진 등은 입력받지 않는다.

## 알려진 한계 (정직하게)
- **에뮬레이터 미검증**: 도어·창문 위치 Int의 의미가 문서에 없어 `check…PositionCapabilities`의 최솟값을 "닫힘"으로 가정했다.
- **차량 경고등 미사용**: 조회에 PRIVATE_CAR_SAFETY가 필요해 쓰지 않는다. 마무리 점검에서 "차량 경고 정보는 확인 불가"로 표시된다.
- **위치(구역 경고)**: FusedLocation SDK가 신청 불가(Coming Soon)라 pleos 빌드는 "위치 확인 불가"다. 구역 데이터는 가상 샘플 5곳이며 실제 단속 구역과 다르다.
- **서리 루틴 제어**: Vehicle Control SDK가 신청 불가라 개방 전에는 안내 모드다. 핸들·사이드미러 열선은 단계 값 의미를 확인하지 못해 항상 수동 안내다. 와이퍼 1회는 결과를 확인할 수 없어 "확인 불가"로 보고한다.
- **주행 제한**: CarUxRestrictions 연동은 `android.car` 접근 경로를 확인하지 못해 아직 없다. 기어가 P가 아니거나 읽지 못하면 차단한다.
- 음성은 플랫폼 TextToSpeech(TTS SDK 신청 불가).

## APK 위치
`apk/` 폴더(자체 서명 release 빌드, 파일명 `팀명_앱이름_release.apk` 규칙: 팀 별틔움, 앱 마중).
- `별틔움_마중_release.apk`(제출용): Pleos 차량·에뮬레이터용(일반 기기에서는 차단 화면)
- `별틔움_마중_fake_release.apk`: 아무 안드로이드 기기에서 데모 패널로 시나리오 재현(심사·시연용)

## 시연 영상
- `docs/demo/maejung-promo-v2.mp4`(약 75초, 1080p): 유즈케이스 6가지(좌석별 벨트, 인도 쪽 문, 내리기 전 점검, 주정차 금지 구역, 서리 루틴, 주행 중 차단)를 실제 상황 스톡 영상과 앱 화면으로 보여 준다. 메일 첨부용은 `maejung-promo-v2_mail.mp4`.
- 앱 화면은 fake 빌드(가상 차량 데이터) 캡처다. 실제 차량 연동 화면이 아니다.
- 영상 편집 프로젝트: `video/`(HyperFrames). 스톡 영상은 Pixabay(도시 항공)·Pexels(나머지), 효과음은 Pixabay, 모두 무료 라이선스. 서체는 Pretendard(OFL).
- 이전 버전: `maejung-promo.mp4`(11/10, ffmpeg 편집). `demo-device.mp4`는 앱 이름이 바뀌기 전("내리기 전에") 녹화라 쓰지 않는다.

## 문서
`CLAUDE.md` · `docs/01-planning.md` · `docs/02-use-cases.md` · `docs/03-api-mapping.md` · `docs/04-architecture.md` · `docs/05-roadmap.md` · `docs/06-submission-checklist.md` · `docs/07-proposal-draft.md`
