# 타임 스탬프 (Time Stamp)

말로만 설명하던 차 문제, '스탬프' 한마디로 남긴 차 상태를 운전자와 정비사가 함께 보는 Pleos Connect 앱입니다.

## 하는 일
1. 한마디 기록: "스탬프"가 들어간 말을 하거나 Gleo에 기록을 요청하면, 말하기 25초 전부터 5초 후까지의 속도, 브레이크 밟음 여부, 기어, 외기온을 차 안에 저장합니다
2. 증상 메모: "기록했어요. 어떤 소리였나요?"에 답하거나 한 번에 말한 증상을 글로 남깁니다
3. 주행 중: 기어가 P를 벗어나면 앱 화면이 스스로 내려가고, 기록은 뒤에서 계속합니다
4. 정차 후: 차 그림에서 소리가 난 부위를 고르고, 같은 부위 기록의 공통 조건과 블랙박스 확인 시각을 리포트로 봅니다. "정비소에 보여주기"는 큰 글씨 화면입니다
5. 수리 완료 날짜와 내용을 기록에 남깁니다

## 실행 방법
1. Pleos Connect 에뮬레이터 v2.1.0을 콜드 부팅으로 켭니다
2. `apk/` 폴더의 release APK를 설치합니다
   ```
   adb install -r apk/usy_타임스탬프_release.apk
   ```
3. 에뮬레이터에서 "타임 스탬프"를 열고 개인정보 안내에 동의한 뒤 마이크와 차량 권한을 허용합니다
4. Virtual Console로 기어, 속도, 브레이크를 바꾸며 "스탬프"라고 말합니다. 시연 순서는 `docs/DEMO.md`

## 테스트 환경과 필요한 설정
- 에뮬레이터: Pleos Connect Emulator v2.1.0 (빌드 RELEASE.release_connect_v2.1.0, Android 14, API 34)
- 화면 규격: 2560 x 1440, 160dpi (에뮬레이터 기본값). 가로와 세로 창, 주간과 야간 테마에서 확인
- Virtual Console: `adb forward tcp:8088 tcp:8088` 후 http://127.0.0.1:8088
- 마이크: 에뮬레이터를 `-allow-host-audio`로 켜고 `adb emu avd hostmicon`을 실행하면 PC 마이크로 "스탬프"를 말할 수 있습니다
- Gleo 요청 시험 (Gleo가 보내는 것과 같은 형식):
  ```
  adb shell am broadcast -a RECORD_VEHICLE_MOMENT -p io.timestamp.app --es ACTION_PARAM '{"symptom":"끼익 소리"}'
  ```
- 차량 속도 권한을 허용할 때 시스템이 "위치 정보 액세스" 창을 띄울 수 있습니다. 시스템이 차량 속도 권한에 붙여 보여 주는 창이며, 이 앱은 위치 권한을 선언하지 않았고 위치를 읽지 않습니다
- 자세한 준비 과정은 `docs/SETUP.md`, 신호별 확인 결과는 `docs/SIGNALS.md`

## APK 위치
`apk/usy_타임스탬프_release.apk`

## 소스에서 빌드
JDK 17, Android 플랫폼 34, 빌드 도구 34.0.0이 필요합니다.
```
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:assembleRelease
```

## 쓰는 SDK와 API
- Pleos Connect SDK: Vehicle 2.3.2 (브레이크), SpeechToText 3.3.1과 TextToSpeech 3.3.1 (기기 내 처리, 한국어)
- Gleo Interface: `app/src/main/assets/schema.json`에 `RECORD_VEHICLE_MOMENT`를 등록하고, 처리 결과를 `APP_ACTION_RESPONSE`로 돌려줍니다
- Android Automotive 표준 API: CarPropertyManager (속도, 기어, 외기온), CarUxRestrictionsManager (주행 중 화면 제한)
- FusedLocation 1.1.3은 디버그 빌드의 신호 확인 화면에서만 씁니다. 제출용 빌드는 위치를 읽지 않습니다
- 공개된 SDK 문서 범위에서만 만들었습니다. 차량 제어는 하지 않습니다

## Pleos 가이드 반영
- 주행 규제: 주행 중에는 버튼 없는 화면만 보이고, 화면 조작은 P단에서만 합니다
- 오디오 포커스: 안내 음성은 포커스를 받은 뒤에만 재생하고, 포커스를 잃으면 바로 멈춥니다
- 백그라운드 정리: 음성 기록을 끄거나 P단이 10분 넘게 이어지면 마이크와 포그라운드 서비스를 내려놓습니다
- 디자인 가이드: Asta Sans 글꼴, 글자 크기 체계, Pleos 색 토큰(무채색 버튼과 탭, 42dot 테마 초록 강조), 주간과 야간 테마, 팝업과 탭 형태, Adaptive Icon

## 개인정보
기록은 직접 지울 때까지 차 안에만 저장합니다. 위치는 모으지 않고, 밖으로 보내는 기능도 없습니다. 음성 기록은 설정에서 끌 수 있습니다.

## 문서
- `docs/ARCHITECTURE.md`: 구조 설명
- `docs/SIGNALS.md`: 신호 확인 결과
- `docs/SETUP.md`: 개발 환경과 실행 방법
- `docs/DEMO.md`: 시연 순서

## 오픈소스
- AndroidX (Compose, Room, Lifecycle), Kotlin, kotlinx.coroutines, Gson: Apache License 2.0
- Asta Sans 글꼴 (42dot, https://github.com/42dot/Asta-Sans): SIL Open Font License 1.1, 전문은 `docs/ASTA_SANS_OFL.txt`
