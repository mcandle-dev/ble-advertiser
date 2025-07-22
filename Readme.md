BLE Advertiser App (Kotlin, Android)
📲 프로젝트 소개
이 앱은 Android 디바이스를 BLE Advertiser(광고 송신기)로 동작시키는 테스트 앱입니다.

카드번호(16자리), 카카오페이 설치 여부, 디바이스 이름을 직접 입력

해당 데이터를 BLE Advertise 패킷으로 브로드캐스트

광고 시작/중지 상태를 UI에서 직관적으로 제어

EFR32BG22 등 BLE 수신 장비와 호환 가능하도록, 패킷 구조와 데이터가 명확히 규격화되어 있습니다.

🚀 주요 기능
카드번호, 카카오페이, 디바이스 이름 입력 (UI 제공, 기본값 지정 가능)

BLE Advertise 시작/중지 (한 번에 하나의 패킷만 송신)

광고 시작/중지 상태에 따라 Start/Stop 버튼 UI가 동적으로 변함

Android 8.0 (API 26)+, Android Studio Meerkat Feature Drop 이상 지원

🛠️ 개발 환경
Android Studio Meerkat Feature Drop (최신 권장)

Kotlin

minSdk: 26

targetSdk: 34

패키지명: com.mcandle.bleapp

📁 디렉토리 구조
swift
복사
편집
com.mcandle.bleapp/
├── MainActivity.kt               // 메인 UI, Start/Stop 버튼, BLE 권한 처리
├── advertise/
│   ├── AdvertiserManager.kt      // BLE 광고 송신/중지 관리 (ViewModel 상태 연동)
│   └── AdvertisePacketBuilder.kt // 패킷 데이터 구성 (서비스 데이터 등)
├── model/
│   └── AdvertiseDataModel.kt     // 광고 데이터(카드번호, 카카오페이, 디바이스 이름) 모델
├── ui/
│   └── InputFormFragment.kt      // 카드번호/카카오페이/디바이스명 입력 UI
├── util/
│   └── ByteUtils.kt              // ByteArray to HEX 변환 등
├── viewmodel/
│   └── BleAdvertiseViewModel.kt  // LiveData로 데이터 및 광고상태 관리
res/layout/
├── activity_main.xml             // 전체 UI 컨테이너
├── fragment_input_form.xml       // 입력 폼 UI
res/values/
├── themes.xml, colors.xml        // 앱 테마/색상
⚙️ 실행/개발 방법
1. 프로젝트 생성
   Android Studio → New Project → Empty Views Activity

패키지명: com.mcandle.bleapp

Kotlin 선택, minSdk 26 이상

2. 퍼미션 설정
   AndroidManifest.xml에 다음 퍼미션 추가:

xml
복사
편집
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
3. 테마 설정
   res/values/themes.xml에서
   Theme.BLEAdvertiser의 parent를 반드시 Theme.AppCompat.Light.DarkActionBar 등 AppCompat 계열로 설정해야 함.

4. build.gradle.kts 세팅
   kotlin
   복사
   편집
   implementation("androidx.core:core-ktx:1.13.1")
   implementation("androidx.appcompat:appcompat:1.6.1")
   implementation("com.google.android.material:material:1.12.0")
   implementation("androidx.constraintlayout:constraintlayout:2.1.4")
   implementation("androidx.fragment:fragment-ktx:1.7.1")
   implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
   implementation("androidx.activity:activity-ktx:1.9.0")
5. 권한 요청
   MainActivity에서 BLE 및 위치 권한을 런타임에 반드시 요청하도록 구현

6. 입력/광고 절차
   앱 시작 후, 카드번호, 카카오페이(설치됨/설치안됨), 디바이스 이름(기본값 "mcandle") 입력

[패킷 적용] 버튼으로 입력값 적용

[Advertise Start] 클릭 → BLE 브로드캐스트 시작, 버튼이 [적용중...]으로 비활성화

[Advertise Stop] 클릭 → 광고 중단, Start 버튼 다시 활성화

7. 수신 확인
   EFR32BG22, nRF Connect 등 BLE 스캐너에서 광고 패킷 수신 확인 가능

패킷 구조:

Service Data (0x16): 16자리 카드번호 + 1자리(Y/N) 카카오페이 여부

Complete Local Name (0x09): 디바이스 이름

💡 커스터마이징/확장 포인트
광고 데이터 추가: AdvertisePacketBuilder.kt에서 서비스 데이터 구조 확장 가능

상태 관리: BleAdvertiseViewModel의 LiveData를 사용해 UI, 로그 등과 연동

BLE 수신/스캐너 기능: 필요시 Fragment/Activity 추가로 확장 가능

🐞 트러블슈팅
테마 오류: 반드시 AppCompat 기반 테마 사용

Start/Stop 상태가 UI에 반영되지 않음: ViewModel과 observe 코드 연결 확인

BLE 광고 미동작: 기기 BLE Advertise 지원 여부 확인, 권한 허용 필수

📝 참고
본 앱은 BLE 테스트/개발 목적의 샘플이며, 상용 환경에선 BLE 패킷 데이터 보호 등 추가 보안 조치가 필요할 수 있습니다.

Android 12(API 31)+ 이상에서 BLE 권한 세분화 및 런타임 퍼미션 적용

✨ 문의/기여
코드/문서 개선이나 기능 추가를 원하시면 Pull Request 혹은 이슈 남겨주세요!

최신 코드는 항상 README와 함께 업데이트 해주세요.
(2025-07 기준, Android Studio Meerkat Feature Drop/최신 SDK 호환 테스트됨)