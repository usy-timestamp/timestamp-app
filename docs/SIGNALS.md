# 신호 확인 결과 (M0)

확인일: 2026-11-12
환경: Pleos Connect Emulator v2.1.0 (Android 14, API 34), Virtual Console
방법: 신호 확인용 화면(`probe/ProbeActivity`, 디버그 빌드 전용)이 각 신호를 읽어 로그에 남기고, Virtual Console로 값을 바꿔 앱에 들어오는지 봤다.
"됨"은 에뮬레이터에서 직접 본 것만 적었다.

## 차량 신호

| 신호 | 됨/안 됨 | 읽는 방법 | Virtual Console로 바꾸기 | 비고 |
|---|---|---|---|---|
| 기어 | 됨 | AAOS `CarPropertyManager`, `GEAR_SELECTION`. 권한 `android.car.permission.CAR_POWERTRAIN` | 됨 (P, R, N, D) | D로 바꾸자 `GEAR_DRIVE` 수신 |
| 차속 | 됨 | AAOS `PERF_VEHICLE_SPEED` (m/s). 권한 `android.car.permission.CAR_SPEED`는 실행 중에 허용을 받아야 함 | 됨. 콘솔 주행 화면의 속도 입력 | 시속 40km를 넣자 11.11 m/s 수신. 따로 "시연 모드"를 만들 필요 없음 |
| 브레이크 | 됨 | Pleos Vehicle SDK `Brake.registerBrakePedalStatus`. 권한 `pleos.car.permission.CAR_DRIVE` | 됨 (0~100%) | **앱이 받는 값은 "밟음 또는 안 밟음"뿐이다.** 퍼센트는 받지 못한다 |
| 외기온 | 됨 | AAOS `ENV_OUTSIDE_TEMPERATURE`. 권한 `android.car.permission.CAR_EXTERIOR_ENVIRONMENT` | 콘솔 카드 없음 | 25.0도 고정값 수신 |
| 경고등 | 됨 (조건 있음) | Pleos Vehicle SDK `Safety.registerTelltales` | 됨 (종류, 상태, 색) | `pleos.car.permission.PRIVATE_CAR_SAFETY`를 선언해야 읽힌다. 공식 샘플의 공개 권한 목록에는 없는 권한이라 핵심 흐름에 쓰지 않고 확장으로만 둔다. ABS를 켜자 `ABS=ON` 수신 | 이 권한은 승인받지 않아 제출용 빌드에서는 뺐다.
| 위치 | 됨 | Pleos FusedLocation SDK. 권한 `pleos.car.permission.FUSED_LOCATION`은 실행 중에 허용을 받아야 함 | 에뮬레이터 위치 주입 | 위도, 경도, 방향 수신 |
| 운전 중 제한 | 됨 | AAOS `CarUxRestrictionsManager` | 기어와 속도에 따라 자동 | D단에서 제한 켜짐 수신 |

## 음성

| 기능 | 됨/안 됨 | 방법 | 비고 |
|---|---|---|---|
| 음성 출력 (TTS) | 됨 | Pleos TextToSpeech SDK 3.3.1, 기기 내 모드, 한국어. 권한 `pleos.car.permission.TTS_SERVICE` | "기록했어요" 재생 시작과 완료 이벤트, 오디오 출력 기록을 확인. P단과 D단 모두 됨. 에뮬레이터를 창 없이 켜서 귀로 듣지는 못했다 (사람 확인 필요) |
| 음성 인식 (STT), 파일 입력 | 됨 | Pleos SpeechToText SDK 3.3.1, 기기 내 모드, 한국어. 권한 `pleos.car.permission.STT_SERVICE` | 합성한 "타임 스탬프 찍어줘" 음성을 넣자 "타임스탬프 찍어줘"로 인식. P단과 D단 모두 됨 |
| 음성 인식 (STT), 마이크 입력 | 듣기 시작까지 됨 | 같은 SDK의 `request()` | 듣기 시작은 확인. 사람이 말한 소리를 알아듣는지는 에뮬레이터 앞에서 직접 말해 봐야 한다 (사람 확인 필요) |
| Gleo 음성 호출 | 안 됨 (이전 시험 기준) | 앱 스키마 등록 | 이전 시험에서 Gleo가 스키마를 읽고 문장도 인식했지만 앱을 부르지 않았다. 스키마 승인은 신청할 수 없다고 주최 측이 답했다. 새 앱으로 다시 시험하지는 않았다 |

## 화면

| 항목 | 값 |
|---|---|
| 전체 화면 | 2560 x 1440, 밀도 160 (1dp = 1px) |
| 앱이 열리는 창 | 1682 x 1210. 화면 왼쪽 853px은 주행 정보 영역, 위 90px은 상태 줄, 아래 140px은 공조와 앱 바 |
| 내비와의 관계 | 이 에뮬레이터에서는 앱과 내비가 같은 창 자리를 번갈아 쓴다. 반씩 나눈 창은 아직 찾지 못했다 |
| 주행 중 | 아무 선언이 없으면 시스템이 앱 화면을 "주행 중에는 사용할 수 없습니다"로 덮는다. 액티비티에 `distractionOptimized` 선언을 넣으면 주행 중에도 보인다 |

## 써 보며 알게 된 주의점

1. TTS와 STT는 `initialize`가 끝난 뒤에만 다른 함수를 부를 수 있다. 순서를 바꾸면 SDK 내부 오류가 난다.
2. `TextToSpeech.speak(문장, 끝남 여부)`의 두 번째 값은 "문장이 여기서 끝났다"는 표시다. `false`로 주면 서비스가 다음 말을 계속 기다려 소리가 끝나지 않고, 그 뒤의 말도 나오지 않는다. 항상 `true`로 준다.
3. STT `request()`는 말이 들릴 때까지 계속 듣는다. 앱이 꺼질 때 `stop()`을 부르지 않으면 서비스에 듣기 작업이 남는다.
4. 화면이 덮여도 앱은 계속 돌아가고 신호도 계속 받는다. 기록과 음성은 주행 중에도 동작한다.
5. 브레이크가 "밟음 또는 안 밟음"만 오므로 리포트 문구는 "브레이크 밟는 중"으로 쓰고, 그래프도 퍼센트가 아니라 밟은 구간으로 그린다.
