# 개발 환경과 실행 방법

이 문서는 저장소에 올라간다. 개인 PC의 실제 경로는 적지 않고 `<도구 폴더>`처럼 표시했다. 이 PC의 실제 경로는 `local-tools/PATHS.md`에 있다 (저장소에 올리지 않음).

## 새 프로젝트로 시작한 이유

이전 프로토타입은 화면 하나짜리 Java 앱이고 영상 재생 시험이 목적이었다. 이 앱은 Kotlin과 Compose로 화면 10개와 백그라운드 기록을 만들어야 해서 구조가 다르다. 그래서 새 프로젝트로 시작하고 아래만 가져왔다.

## 이전 작업에서 재사용한 것

| 재사용한 것 | 용도 |
|---|---|
| Pleos Connect 에뮬레이터 v2.1.0, 시스템 이미지, AVD 설정 | 그대로 실행 |
| JDK 17, Android 플랫폼 34, 빌드 도구 34.0.0의 원본 압축 파일 | 다시 풀어서 사용 |
| Gradle 8.7 래퍼와 캐시 | 그대로 사용 |
| AAOS로 기어를 읽는 방식 | 같은 API를 Kotlin으로 다시 작성 |
| Gleo 스키마와 수신 방식 | 참고만. 스키마 승인을 신청할 수 없어 음성은 SpeechToText SDK가 1순위 |
| P단에서만 화면을 여는 규칙 | 그대로 유지 |

재사용하지 않은 것: 영상 재생 화면, PC 데모 서버, 테스트 영상.

## 필요한 것

- Windows, RAM 16GB 이상. 내장 그래픽(Intel Iris Xe)에서도 에뮬레이터가 부팅되고 동작했다
- JDK 17
- Android 플랫폼 34, 빌드 도구 34.0.0
- Pleos Connect 에뮬레이터 v2.1.0과 AVD

## 빌드

```
set JAVA_HOME=<도구 폴더>\jdk-17
gradlew.bat :app:assembleDebug
```

- `local.properties`에 `sdk.dir=<도구 폴더>/build-sdk`를 적는다
- Gradle 설정 파일은 Groovy 방식(`build.gradle`)이다. Kotlin 스크립트 방식은 이 환경에서 설정 단계 오류가 나서 쓰지 않았다
- 결과물: `app/build/outputs/apk/debug/app-debug.apk`

## 에뮬레이터 실행

```
set ANDROID_SDK_ROOT=<도구 폴더>\emu-sdk
set ANDROID_AVD_HOME=<도구 폴더>\avd
<도구 폴더>\emu-sdk\emulator\emulator.exe -avd Pleos_Iris_Xe -port 5564 -gpu host -no-snapshot -no-boot-anim -memory 4096
```

- 창 없이 켜려면 `-no-window`를 붙인다. 화면을 보거나 녹화하려면 뺀다
- 반드시 콜드 부팅(`-no-snapshot`). 빠른 부팅은 차량 데이터가 안 나온다
- 부팅 완료까지 1~2분. `adb -s emulator-5564 shell getprop sys.boot_completed`가 1이면 완료
- 화면은 2560 x 1440, 밀도 160

## 설치와 실행

에뮬레이터는 사용자 10번으로 동작한다. 권한과 실행 명령에 `--user 10`을 붙인다.

```
adb -s emulator-5564 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s emulator-5564 shell pm grant --user 10 io.timestamp.app android.car.permission.CAR_SPEED
adb -s emulator-5564 shell pm grant --user 10 io.timestamp.app android.permission.RECORD_AUDIO
# (위치 권한은 2026-11-12에 앱에서 뺐다. 디버그 빌드의 신호 확인 화면에서만 쓴다)
adb -s emulator-5564 shell am start --user 10 -n io.timestamp.app/.probe.ProbeActivity
adb -s emulator-5564 logcat -s TimeStampProbe
```

## Virtual Console

```
adb -s emulator-5564 forward tcp:8088 tcp:8088
```

브라우저에서 http://127.0.0.1:8088 을 연다. 콘솔은 브라우저가 열려 있는 동안만 명령을 받는다.

| 바꿀 것 | 콘솔에서 |
|---|---|
| 기어 | P, R, N, D 버튼 |
| 속도 | 주행 화면의 속도 입력 (0~240km/h) |
| 브레이크 | 브레이크 페달 0~100% |
| 경고등 | Safety의 Telltales 카드. 종류, 상태(Off, On, Blink), 색 |

브라우저 없이 명령줄로 조작하는 도우미는 `local-tools/vc.sh`에 있다 (저장소에 올리지 않음).

## 시험용 디버그 명령

신호 확인 화면이 떠 있을 때 동작한다.

```
adb -s emulator-5564 shell am broadcast --user 10 -a io.timestamp.app.PROBE_TTS --es text 기록했어요
adb -s emulator-5564 shell am broadcast --user 10 -a io.timestamp.app.PROBE_STT
adb -s emulator-5564 shell am broadcast --user 10 -a io.timestamp.app.PROBE_STT_STOP
adb -s emulator-5564 shell am broadcast --user 10 -a io.timestamp.app.PROBE_STT_FILE --es name utt1.wav
```

- `PROBE_STT_FILE`은 디버그 빌드에 넣어 둔 시험 음성(`app/src/debug/assets/stt-test`)을 마이크 대신 인식기에 보낸다
- adb로 보내는 글자에 띄어쓰기가 있으면 잘린다. 띄어쓰기 없는 문장으로 시험한다

## 기록 서비스 시험 (M1)

신호 확인 화면의 "기록 서비스 시작" 버튼을 누른 뒤에 동작한다. 디버그 빌드에서만 된다.

```
adb -s emulator-5564 shell am broadcast --user 10 -a io.timestamp.app.DEBUG_CAPTURE --es memo test
adb -s emulator-5564 shell am broadcast --user 10 -a io.timestamp.app.DEBUG_VOICE_FILE --es name utt1.wav
adb -s emulator-5564 shell am broadcast --user 10 -a io.timestamp.app.DEBUG_DUMP
adb -s emulator-5564 logcat -s TimeStampCapture
```

- `DEBUG_CAPTURE`: 지금 순간을 기록한다 (5초 뒤 저장)
- `DEBUG_VOICE_FILE`: 시험 음성 "타임 스탬프 찍어줘"를 인식기에 넣어 음성 기록 흐름을 확인한다
- `DEBUG_DUMP`: 저장된 기록 수와 최근 3건 요약을 로그로 본다
- 단위 테스트: `gradlew.bat :app:testDebugUnitTest`
