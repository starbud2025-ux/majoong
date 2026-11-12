# CLAUDE.md — 마중 (등·하원 픽업 도우미, 팀 별틔움, 구 가칭 "내리기 전에")

현대자동차그룹 차량용 앱 개발 공모전(Pleos Connect / Pleos App Market) 출품용 Android Automotive 앱.
**마감: 2026-11-15(일)**. 이 파일은 Claude Code가 매 세션 처음 읽는 프로젝트 지침입니다.

## 한 줄 요약
차에서 **내리기 전**(불법 주정차 구역 경고, 창문·도어·뒷좌석 점검)과 **출발하기 전**(서리·시야 확보 루틴)을 한 흐름으로 챙겨 주는 **정차 전용** 차량 앱.

## 문서 안내 (먼저 읽을 것)
| 문서 | 내용 |
|---|---|
| docs/01-planning.md | 문제 정의, 목표, 범위, 차별점, 리스크 |
| docs/02-use-cases.md | 유즈케이스와 시나리오, 수용 기준 |
| docs/03-api-mapping.md | 기능별 SDK API 매핑과 **확인 필요 항목** |
| docs/04-architecture.md | 구조, 상태 머신, 인터페이스, 테스트 전략 |
| docs/05-roadmap.md | 11/6~11/15 일정과 작업 순서 |
| docs/06-submission-checklist.md | 제출 체크리스트, 기획서 뼈대 |

## 절대 규칙
1. **정차 전용.** Driver Distraction Optimized를 선언하지 않는다. 기어가 P가 아니거나 `CarUxRestrictions`가 걸리면 화면 조작과 차량 제어를 즉시 중단한다.
2. **SDK API 이름을 추측하지 않는다.** docs/03의 "확인 필요"는 실제 SDK(Maven 의존성의 클래스와 KDoc)와 Pleos 문서로 검증한 뒤에 코드에 쓴다. 문서에 없으면 해당 기능을 degrade(안내만)하고 README에 한계로 적는다.
3. **승인되지 않은 퍼미션을 선언하지 않는다.** 퍼미션은 docs/03의 표에 있는 것만. 추가가 필요하면 먼저 사용자에게 묻는다.
4. **차량 제어는 사용자 확인 후에만.** 서리 제거 루틴 등 제어는 버튼 한 번(명시적 동의) 뒤 실행하고, 결과는 콜백으로 확인해서 보고한다. 실패는 숨기지 않는다.
5. **core 모듈은 순수 Kotlin.** Android·Pleos 의존 금지. Pleos 의존 코드는 `pleos` 어댑터에만 둔다. 모든 기능은 `fake` 차량으로 에뮬레이터 없이 데모와 테스트가 가능해야 한다.
6. **개인정보 최소화.** 위치는 기기 안에서만 처리한다. 서버 전송, 계정, 로그인 없음(v1).
7. 다른 팀 공개 저장소의 코드를 복사하지 않는다. 구조 참고만.
8. UI 문구는 한국어, 짧고 명확하게. 음성은 TTS(온디바이스) 사용.

## 기술 스택 (저장소 사례 기준, 공식 문서로 재확인할 것)
- Kotlin 2.0.x, Jetpack Compose, JDK 17, AGP 8.8.x, Gradle wrapper
- minSdk 28 / compileSdk·targetSdk 34~35
- Pleos Maven 저장소: `https://nexus-playground.pleos.ai/repository/maven-releases/`
- `ai.pleos.playground:Vehicle:2.3.2`, `TextToSpeech:3.3.1`, `FusedLocation:1.1.3` (NaviHelper는 v1 범위 밖)
- 테스트: Pleos Connect Emulator 2.1.0 (API 34, **Cold boot 필수**, GPU 필요), 단위 테스트는 JUnit + 코루틴 테스트
- 패키지명(가칭): `kr.pleosapp.getoff`

## 작업 방식
- 먼저 `docs/05-roadmap.md`의 현재 단계를 확인하고, 한 번에 한 단계씩 진행한다.
- 코드를 쓰기 전에 해당 기능의 수용 기준(docs/02)을 테스트로 먼저 옮긴다(core 단위 테스트).
- 각 단계가 끝나면 로드맵의 체크박스를 갱신하고, 새로 알게 된 사실(API 가능 여부 등)은 docs/03에 기록한다.
- 막히는 항목(퍼미션 미승인, API 없음)은 숨기지 말고 사용자에게 보고한다.

## 명령어 (프로젝트 생성 후)
```
./gradlew :core:test            # 순수 로직 테스트
./gradlew :app:assembleDebug    # 디버그 APK
./gradlew :app:lintDebug
```
