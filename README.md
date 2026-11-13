# 타임 스탬프

운전 중 이상한 소리가 나는 순간 "타임 스탬프 찍어줘" 한마디로 표시하면, 그 앞뒤 30초의 차 상태를 남겨 정비사가 읽을 수 있는 리포트로 만들어 주는 Pleos Connect 앱입니다.

## 하는 일
1. 한마디 기록: 말하기 25초 전부터 5초 후까지의 속도, 브레이크, 기어를 차 안에 저장합니다
2. 정차 후 차 그림 표시와 정비 리포트: 소리가 난 부위를 차 그림에서 누르면, 같은 부위 기록들의 공통 조건(재현 조건)과 블랙박스 확인 시각을 한 화면에 정리합니다
3. 수리 후 확인: 수리 완료로 표시해 둡니다 (같은 조건에서 다시 묻는 기능은 다음 단계)

## 실행 방법
1. Pleos Connect 에뮬레이터 v2.1.0을 콜드 부팅으로 켭니다
2. `apk/` 폴더의 APK를 설치합니다
   ```
   adb install -r apk/usy_타임스탬프_release.apk
   ```
3. 에뮬레이터에서 "타임 스탬프"를 열고 첫 화면에서 권한을 허용한 뒤 "시작하기"를 누릅니다
4. Virtual Console로 기어, 속도, 브레이크를 바꾸며 시연합니다. 순서는 `docs/DEMO.md`

## 테스트 환경과 필요한 설정
- Pleos Connect Emulator v2.1.0 (Android 14, API 34), 화면 2560 x 1440
- Virtual Console: `adb forward tcp:8088 tcp:8088` 후 http://127.0.0.1:8088
- 권한: 마이크, 차량 속도와 기어는 첫 화면에서 허용합니다. 개인정보 안내에 동의해야 시작됩니다
- 자세한 준비 과정은 `docs/SETUP.md`, 신호별 확인 결과는 `docs/SIGNALS.md`

- 차량 속도 권한을 허용할 때 시스템이 "위치 정보 액세스" 창을 띄웁니다. 시스템이 차량 속도 권한에 붙여 보여 주는 창이며, 이 앱은 위치 권한을 선언하지 않았고 위치를 읽지 않습니다

## APK 위치
`apk/usy_타임스탬프_release.apk`

## 소스에서 빌드
JDK 17, Android 플랫폼 34, 빌드 도구 34.0.0이 필요합니다.
```
gradlew.bat :app:assembleDebug
gradlew.bat :app:testDebugUnitTest
```

## 쓰는 SDK
- Pleos Connect SDK: Vehicle 2.3.2, TextToSpeech 3.3.1, SpeechToText 3.3.1
- FusedLocation 1.1.3은 디버그 빌드의 신호 확인 화면에서만 씁니다. 제출용 빌드는 위치를 읽지 않고 위치 권한도 없습니다
- Android Automotive 표준 API (CarPropertyManager)로 속도와 기어를 읽습니다
- 공개된 SDK 문서 범위에서만 만들었습니다. 차량 제어는 하지 않습니다

## 개인정보
기록은 차 안에만 저장합니다. 위치는 모으지 않습니다. 밖으로 보내는 기능은 없습니다.

## 문서
- `docs/ARCHITECTURE.md`: 구조 설명
- `docs/SIGNALS.md`: 신호 확인 결과
- `docs/SETUP.md`: 개발 환경과 실행 방법
- `docs/DEMO.md`: 시연 순서

## 오픈소스
- AndroidX (Compose, Room, Lifecycle), Kotlin, kotlinx.coroutines: Apache License 2.0
