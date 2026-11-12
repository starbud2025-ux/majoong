# 05. 로드맵 (2026-11-06 ~ 11-15)

우선순위: **F2 하차 점검 → F1 구역 경고 → F4 시야 루틴 → F3 뒷좌석**. 시간이 부족하면 뒤에서부터 "안내 모드"로 축소한다.
각 단계 끝에 체크박스를 갱신하고, 알게 된 API 사실은 `docs/03-api-mapping.md`에 기록한다.

## 0단계. 사전 확인 (11/6, 사용자 직접)
- [ ] Pleos Playground 회원가입, 프로젝트 생성, SDK 사용 신청
- [ ] 공모전 가이드·FAQ·참가 규정에서 제출물(APK 경로, README, 기획서 양식) 확인
- [ ] 제어 퍼미션(HVAC·Wiper·SideMirror·핸들 열선) 사용 가능 범위 확인 → `docs/03` §4 #1
- [ ] Pleos Connect Emulator 설치(API 34, Cold boot, GPU 요건)

## 1단계. 뼈대와 core (11/6~11/7)
- [x] Gradle 프로젝트 생성: `core`(JVM) + `app`(Android), 버전 카탈로그, flavor(`fake`/`pleos`)
- [x] 포트 인터페이스와 모델(`docs/04`) 작성
- [x] 상태 머신과 `Blocked` 규칙 구현 + 단위 테스트
- [x] FakeVehicle(core/fake) + 앱 데모 패널(fake flavor)

## 2단계. F2 하차 점검 (11/7~11/8)
- [x] 점검 로직(이상 항목 추림, Unknown 처리) + AC2.* 테스트
- [x] ExitChecklist 화면 (TTS 문장은 Phrases) — 실기 확인은 6단계
- [ ] pleos 어댑터: Window, Door, Safety (확인 필요 #3 해소)

## 3단계. F1 구역 경고 (11/8~11/9)
- [x] 구역 대조(원·폴리곤) + AC1.* 테스트, 샘플 GeoJSON(가상 5곳)
- [ ] 기어 P 전환 트리거(확인 필요 #4), FusedLocation 어댑터
- [x] ZoneWarning 화면

## 4단계. F4 시야 루틴 (11/9~11/11)
- [x] 루틴 실행기(순서, 동의, 결과 확인, 실패 보고) + AC4.* 테스트
- [ ] pleos 어댑터 제어 호출과 콜백 확인(확인 필요 #1, #5)
- [x] 퍼미션 미승인 시 안내 모드 degrade (core 로직·화면)

## 5단계. F3 뒷좌석 (11/11~11/12)
- [ ] Seat 탑승 감지(확인 필요 #2), 미지원이면 일반 문구
- [x] 동승자용 안내 화면 + AC3.* 테스트 (Seat 탑승 감지 어댑터는 미완)

## 6단계. 통합·검증 (11/12~11/13)
- [ ] 데모 시나리오 3분(`docs/02`) 에뮬레이터 리허설
- [ ] 정차 전용 차단(주행 상태) 확인, 개발자 패널
- [ ] 성능(폴링 없음, 메모리) 점검, lint

## 7단계. 제출 준비 (11/13~11/14)
- [x] README(실행 방법, 테스트 환경, 사용 SDK, 퍼미션, 한계) 작성
- [x] 서명 APK, GitHub 경로 정리
- [x] 기획서 작성(`docs/06` 뼈대) — 접수양식 DOCX
- [x] 데모 영상(선택): maejung-promo-v2.mp4

## 8단계. 제출·예비 (11/14~11/15)
- [ ] 제출 메일 발송 후 수신 확인
- [ ] 마감 당일은 수정 금지, 버퍼로만 사용

## 일정이 밀릴 때의 축소 순서
1. F3 뒷좌석 → 일반 문구로 축소
2. F4 → 안내 모드(제어 없이 단계 안내)
3. F1 → 샘플 구역 3곳으로 축소
F2와 정차 전용 차단은 끝까지 유지.

## 진행 현황 (2026-11-08)
- 완료: 1단계 전부, 2~5단계의 **core 로직·화면·테스트** (`./gradlew :core:test` 27개 통과, `assembleFakeDebug`/`assemblePleosDebug`/`lintFakeDebug` 통과)
- 남음(사용자 확인·SDK 필요): 각 단계의 **pleos 어댑터**(Window/Door/Safety/Seat/Gear/FusedLocation/TTS/제어 계열). 0단계 확인 필요 항목 #1~#6 결과가 있어야 진행 가능.
  현재 `pleos` flavor는 스텁(항상 Blocked, 전 기능 Unsupported)이며 API 이름을 추측해 쓰지 않았다.
- 다음: 6단계(통합·리허설), 7단계(README·기획서·서명 APK)

## 공식 규정 반영 (2026-11-08)
- 필수 제출물은 **접수양식(기획서)+동의서**뿐. APK·GitHub·시연 영상은 가산점이므로 **기획서를 최우선**, 그다음 시연 영상(fake 빌드 화면 녹화로 충분), 그다음 GitHub.
- 심사 4항목(차량특화성·창의성·기술 활용성·사업성)에 맞춰 기획서 작성. 사업성(타깃·MAU·Retention·확장 계획) 항목이 현재 docs/01에 약하므로 보강 필요.
- 마감까지 남은 일: 기획서(양식 수령 후) → 서명 동의서 → 시연 영상 → README·GitHub → 제출 메일(11/14 목표, 11/15 버퍼).

## 진행 현황 (2026-11-08 밤) — 등·하원 픽업 모드 구현
- [x] core: `SeatPos`/`BeltState`/`CurbSide`, `seatOccupancy`·`seatBelts` 포트, `PickupEvaluator`, `AppState.PickupActive`, 사용자 액션(PickupStart/Done/Cancel), 설정(pickup, curbSide)
- [x] 테스트 46개 통과 (픽업 AC5.1~5.6 포함 `PickupTest.kt`)
- [x] 화면: 홈 "픽업 모드" 버튼, 좌석별 상태·하차 안내 화면, 설정(픽업 on/off, 정차 방향)
- [x] fake 데모 패널: 픽업 시나리오 7개 버튼
- [ ] pleos 어댑터(docs/03 §9 API): 실제 SDK 의존성·에뮬레이터 검증 후
- [ ] 에뮬레이터/실기기에서 화면 확인 (아직 화면을 눈으로 확인하지 못함)
- [ ] 기획서(docs/07)를 픽업 모드 중심으로 개정

## 진행 현황 (2026-11-09) — UI 2차 개편
- [x] 홈: 픽업 모드 히어로 카드(그라데이션) 중심, 하차 점검·출발 준비는 보조 타일
- [x] 픽업 화면: 위에서 본 차량 지도(`CarMap`, 좌석 색·뒷문 열림·인도 쪽 표시) + 좌석 카드 상태 전환 모션
- [x] 움직임: 화면 전환(페이드+슬라이드), 카드 순차 등장, 터치 눌림, 상태 색 전환, 점검 진행률 막대. 반복 모션(실시간 점, 경고 고리, 히어로 장식 이동)은 정차 중에만 동작하고 Blocked에서는 멈춘다(UI 가이드는 "운전 중" 시선 유도 금지 — docs/03 §8.1 정정)
- [x] 가로 2창: 픽업 중 오른쪽에 차량 지도 확대 표시
- [x] 검증: `:core:test`, `assembleFakeDebug`, `assemblePleosDebug`, `lintFakeDebug` 통과. **화면은 아직 눈으로 확인하지 못함** (에뮬레이터/기기 필요)
- 아이디어 검토: 드라이브 일기는 FusedLocation 미개방·장소명 해석 불확실로 v1 제외, "주차 기록" 정도로 축소 제안

## 진행 현황 (2026-11-09) — pleos 어댑터 1차
- [x] Pleos Maven에서 Vehicle 2.3.2 AAR 확보·검증(docs/03 §10), `pleosImplementation` 의존성 추가
- [x] pleos 어댑터: 기어(P 판별), 도어·창문 위치, 뒷좌석 탑승·벨트, 서리 제습·와이퍼 1회 제어(상태 재확인), 기능별 지원 여부 조회
- [x] CAR_DRIVE 선언(사용자 승인). 경고등(PRIVATE_CAR_SAFETY)은 미사용 결정 → "경고 정보 확인 불가"
- [x] `:core:test` 47개, `assembleFakeDebug/PleosDebug`, `lintFakeDebug/PleosDebug` 통과
- [ ] **Pleos Connect Emulator 검증 필요**: 도어·창문 위치값 의미, 콜백 타이밍(Vehicle.initialize 직후 호출), 지원 여부 결과, 제어 동작
- [ ] CarUxRestrictions 연결(android.car 접근 경로 확인), 위치(FusedLocation 개방 시), 사이드미러·핸들 열선(단계 값 확인 시)

## 진행 현황 (2026-11-09 저녁) — 실기기 리허설·README
- [x] 실기기(SM-F971N, Android 17)에 fake 빌드 설치, 시연 시나리오 전체 리허설(픽업 벨트 → 차도 쪽 문 경고 → 마무리 점검 → 주행 차단, 구역 경고, 서리 루틴) 정상. 캡처 11장 `docs/screenshots/device/`
- [x] 데모 패널 높이 축소(열어도 본 화면이 덜 잘림), README 작성(실행·환경·SDK·퍼미션·한계)
- [ ] 시연 영상 녹화(화면 녹화), 서명 APK, 접수양식 DOCX 이식·동의서(사용자)
- [x] 서명 release APK 2종(`apk/`), 실기기 화면 녹화(`docs/demo/demo-device.mp4`, 뒤쪽 약 1분은 대기 화면이라 편집 필요). 키스토어는 `C:\Users\mojji\.android\pleos-contest.jks`(저장소 밖, 비밀번호는 local.properties) — **분실하면 같은 서명으로 수정 제출 불가, 백업 필요**

## 진행 현황 (2026-11-10) — 접수양식 갱신
- [x] `contest-application-filled.docx`를 v2.1 기획서(마중·픽업 중심, 테스트 47개, SDK 표)로 갱신. 앱명 "내리기 전에" → "마중". 이전본은 `contest-application-filled.bak-1108.docx`. 지표(MAU)는 근거가 없어 수치 없이 가설·측정 계획으로 기재.
- [x] release APK 2종은 소스 변경 없이 11/9 빌드 그대로 최신(재빌드 불필요)
- [x] 대표자(김혜진)·연락처·팀명(별틔움)·개인 참가·동의서 서명란(성명·일자 2026-11-10) 기입. 이메일·소속·참가 부문·역할은 비워 둠
- [ ] 사용자: 이메일·참가 부문 기입, 시연 영상 편집(ffmpeg 없음), GitHub 저장소(git·gh 미설치), 제출 메일 발송

## 진행 현황 (2026-11-10) — 마케팅 시연 영상
- [x] ffmpeg 설치(winget). 에뮬레이터(Pixel 9)에 최신 fake 빌드("마중") 설치, 상태별 고해상도 캡처 11장 `docs/demo/shots/` (녹화 대신 캡처 — screenrecord는 정지 구간을 건너뛰어 시간이 어긋남)
- [x] Pexels 무료 스톡 영상 5개 `docs/demo/stock/` (벨트·하교·뒷좌석·서리 등) → ffmpeg로 자막·전환·폰 목업·배경음(합성 패드) 편집, 61.7초
- [x] 결과: `docs/demo/maejung-promo.mp4`(31MB, 원본), `maejung-promo_mail.mp4`(8.3MB, 메일 첨부용). DOCX 7번에 첨부 안내 반영
- 영상 말미 소스 표기 "Pexels", 화면은 "가상 차량(Fake) 데모"로 명시. 구 녹화 `demo-device.mp4`(앱명 "내리기 전에")는 사용하지 않음

## 진행 현황 (2026-11-12) — 제출 전 보완
- [x] 시연 영상 v2(`docs/demo/maejung-promo-v2.mp4`, 약 75초): 유즈케이스 6가지마다 실제 상황 스톡 영상(Pixabay 1·Pexels 10)과 앱 화면을 나란히 배치. HyperFrames(`video/`)로 편집, 메일용 압축본 `_mail.mp4`. 구역 경고는 "샘플 데이터", 서리 루틴은 "제어 권한 승인 시"로 화면에 명시
- [x] 팀명 통일: 접수양식 기준 **별틔움**. APK 파일명 `별틔움_마중_release.apk`, `별틔움_마중_fake_release.apk`로 변경, README·영상 엔딩 반영
- [x] `:core:test` 47개 재확인 통과
- [x] GitHub 저장소(가산점): https://github.com/starbud2025-ux/majoong (공개), 접수양식 7번에 링크 기재
- [ ] 제출 메일 발송(사용자): 접수양식 DOCX + 서명 동의서 + 영상(+ GitHub 링크)
- 남은 한계(제출 전 해결 불가): Pleos Connect Emulator 미검증, FusedLocation·Vehicle Control SDK 미개방
