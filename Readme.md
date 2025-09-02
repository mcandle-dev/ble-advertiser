# BLE Advertiser Android App

본 앱은 Android 단말을 BLE Advertiser(비콘 송신기)로 동작시키는 예제 앱입니다.  
카드번호(16자리), 카카오페이 설치여부, 디바이스 이름을 입력받아 BLE 패킷에 포함하여 브로드캐스트하며,  
EFR32BG22 등 BLE 수신기로 패킷을 실시간 확인할 수 있습니다.

---

## 주요 기능

- 카드번호, 카카오페이 설치여부, 디바이스 이름(기본값: mcandle) 입력 가능
- BLE Advertise 패킷 송신 및 중지 (상태에 따라 버튼 활성/비활성 자동 전환)
- ViewModel로 광고 상태/데이터 LiveData 관리
- Android 8.0(API 26)+, Android Studio Meerkat Feature Drop 이상 지원

---

## 개발 환경

- Android Studio Meerkat Feature Drop
- Kotlin
- minSdk: 26 / targetSdk: 34
- 패키지명: `com.mcandle.bleapp`

---

## 프로젝트 구조

```
com.mcandle.bleapp/
├── MainActivity.kt
├── advertise/
│   ├── AdvertiserManager.kt
│   └── AdvertisePacketBuilder.kt
├── model/
│   └── AdvertiseDataModel.kt
├── ui/
│   └── InputFormFragment.kt
├── util/
│   └── ByteUtils.kt
├── viewmodel/
│   └── BleAdvertiseViewModel.kt
res/layout/
├── activity_main.xml
├── fragment_input_form.xml
res/values/
├── themes.xml, colors.xml
```

---

## 실행/빌드 방법

1. Android Studio에서 프로젝트 open  
   패키지명을 `com.mcandle.bleapp`로 유지
2. Gradle 의존성은 build.gradle.kts에 이미 반영됨
3. AndroidManifest.xml에 BLE/위치 권한 포함
4. AppCompat 기반 테마(`Theme.BLEAdvertiser`) 적용 확인
5. 앱 실행 후 카드번호(16자리), 카카오페이 설치여부, 디바이스명 입력
6. [패킷 적용] 후 [Advertise Start] 클릭 → 광고 시작  
   [Advertise Stop] 클릭 → 광고 중지

---

## BLE 패킷 포맷

- **Service Data(0x16)**: 카드번호 16자리 + 카카오페이(Y/N)
- **Device Name(0x09)**: 입력한 디바이스명(기본 mcandle)
- **TX Power Level(0x0A)**: 자동 포함

---

## 커스터마이즈

- 패킷 데이터 포맷, UUID 등은 `AdvertisePacketBuilder.kt`에서 수정 가능
- ViewModel, LiveData, Fragment 구조로 앱 상태 연동 및 확장 용이

---

## 참고/트러블슈팅

- Start/Stop 버튼이 동작하지 않으면 ViewModel/observe 연동 확인
- BLE Advertise가 안 되면 권한, 기기 호환성, 테마 설정 확인
- .gitignore에 .idea, /build 등 개발환경 관련 파일 추가 권장

---

## 라이선스/문의

이 프로젝트는 교육 및 테스트 목적 오픈소스 예제입니다.  
질문/기여는 GitHub Issues 혹은 mcandle.dev로 문의 바랍니다.

---

**© 2025 mcandle.dev / BLE Advertiser Example**
