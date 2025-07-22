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

Kotlin 1.9.0 이상

minSdk: 26 (Android 8.0)

targetSdk: 34 (Android 14)

패키지명: com.mcandle.bleapp

Gradle: 8.2.0 이상

📁 디렉토리 구조 및 주요 파일 설명
app/src/main/java/com/mcandle/bleapp/
├── MainActivity.kt               // 앱의 메인 화면. UI와 BLE 광고 제어의 시작점
│                                // - **역할**: 앱의 전체 생명주기를 관리하며, `InputFormFragment`를 호스팅합니다.
│                                // - **주요 기능**:
│                                //   - BLE 관련 런타임 권한(BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT 등)을 사용자에게 요청하고 결과를 처리합니다.
│                                //   - `BleAdvertiseViewModel`과 연동하여 광고 상태(시작/중지)를 감지하고, 이에 따라 Start/Stop 버튼의 활성화 및 텍스트를 동적으로 변경합니다.
│                                //   - 사용자가 Start/Stop 버튼을 누르면 `ViewModel`에 명령을 전달하여 광고 프로세스를 시작하거나 중지시킵니다.
├── advertise/
│   ├── AdvertiserManager.kt      // 실제 BLE Advertise 기능을 담당하는 핵심 클래스
│   │                             // - **역할**: `BluetoothLeAdvertiser`를 사용하여 BLE 광고 패킷을 송신하고 중지하는 로직을 캡슐화합니다.
│   │                             // - **주요 기능**:
│   │                             //   - `AdvertiseSettings`: 광고 모드(LOW_LATENCY), 전송 파워(TX_POWER_HIGH), 타임아웃 등을 설정합니다.
│   │                             //   - `AdvertiseData`: `AdvertisePacketBuilder`가 생성한 패킷 데이터를 받아와 광고에 포함시킵니다.
│   │                             //   - `startAdvertising()` / `stopAdvertising()`: ViewModel의 요청에 따라 실제 광고를 시작하고 중지합니다.
│   │                             //   - `AdvertiseCallback`: 광고 시작 성공/실패, 상태 변경 등 비동기 결과를 처리하고 ViewModel에 알립니다.
│   └── AdvertisePacketBuilder.kt // BLE 광고 패킷의 데이터 구조를 정의하고 생성
│                                 // - **역할**: `AdvertiseDataModel`에 담긴 사용자 데이터를 BLE 규격에 맞는 `ByteArray`로 변환합니다.
│                                 // - **주요 기능**:
│                                 //   - **Service Data (0x16)**: 카드번호(16바이트), 카카오페이 설치 여부(1바이트 'Y'/'N')를 포함하는 서비스 데이터를 구성합니다.
│                                 //   - **Complete Local Name (0x09)**: 디바이스 이름을 별도의 데이터 필드로 추가합니다.
│                                 //   - `build()`: 모든 데이터를 조합하여 최종 `AdvertiseData` 객체를 생성합니다. 데이터 유효성 검증 로직도 포함될 수 있습니다.
├── model/
│   └── AdvertiseDataModel.kt     // 광고에 사용될 데이터를 표현하는 데이터 클래스(DTO)
│                                 // - **역할**: UI(`InputFormFragment`)와 비즈니스 로직(`ViewModel`) 간에 데이터를 안전하고 명확하게 전달합니다.
│                                 // - **구조**: `cardNumber: String`, `isKakaoPayInstalled: Boolean`, `deviceName: String` 등 광고에 필요한 모든 필드를 포함합니다.
├── ui/
│   └── InputFormFragment.kt      // 사용자 입력을 받는 UI 컴포넌트
│                                 // - **역할**: 사용자가 카드번호, 카카오페이 설치 여부, 디바이스 이름을 입력하고 수정하는 화면을 제공합니다.
│                                 // - **주요 기능**:
│                                 //   - `EditText`와 `TextWatcher`를 사용해 카드번호 16자리 입력을 제한하고, 유효성을 검사합니다.
│   │                             //   - `Switch` 또는 `ToggleButton`으로 카카오페이 설치 여부를 직관적으로 선택하게 합니다.
│                                 //   - [패킷 적용] 버튼 클릭 시, 입력된 데이터를 `BleAdvertiseViewModel`의 `LiveData`에 업데이트하여 다른 컴포넌트가 사용할 수 있도록 합니다.
├── util/
│   └── ByteUtils.kt              // 바이트 배열 처리를 위한 유틸리티 함수 모음
│                                 // - **역할**: 데이터 변환, 디버깅 등 앱 전반에서 필요한 공통 기능을 제공합니다.
│                                 // - **주요 기능**: `ByteArray`를 사람이 읽기 쉬운 HEX 문자열로 변환하여 로그를 확인할 때 사용하거나, 문자열을 특정 인코딩의 `ByteArray`로 변환하는 등의 작업을 수행합니다.
├── viewmodel/
│   └── BleAdvertiseViewModel.kt  // UI 상태와 비즈니스 로직을 연결하는 중간 관리자
│                                 // - **역할**: UI(`Activity`/`Fragment`)와 데이터 처리 로직(`AdvertiserManager`)을 분리하여, 생명주기를 고려한 안전한 데이터 관리를 수행합니다.
│                                 // - **주요 기능**:
│                                 //   - `LiveData`: 광고 상태(`isAdvertising`), 사용자 입력 데이터(`advertiseData`) 등을 `LiveData`로 관리하여, 데이터가 변경될 때마다 UI가 자동으로 업데이트되도록 합니다.
│                                 //   - `startAdvertising()` / `stopAdvertising()`: UI로부터 받은 명령을 `AdvertiserManager`에 전달하고, 그 결과를 `LiveData`에 반영하여 UI 상태를 갱신합니다.

app/src/main/res/
├── layout/
│   ├── activity_main.xml         // `MainActivity`의 화면 레이아웃
│   │                             // - **구조**:
│   │                             //   - `FragmentContainerView`: `InputFormFragment`가 표시될 영역을 정의합니다.
│   │                             //   - `Button`: [Advertise Start], [Advertise Stop] 버튼을 포함하며, `ViewModel`의 상태에 따라 활성화/비활성화됩니다.
│   └── fragment_input_form.xml   // `InputFormFragment`의 화면 레이아웃
│                                 // - **구조**:
│                                 //   - `EditText`: 카드번호, 디바이스 이름 입력을 위한 텍스트 필드. `inputType`과 `maxLength` 속성으로 입력을 제한합니다.
│                                 //   - `Switch`: 카카오페이 설치 여부를 ON/OFF로 선택하는 토글 버튼.
│                                 //   - `Button`: [패킷 적용] 버튼을 포함하여, 입력된 데이터를 ViewModel에 저장하도록 트리거합니다.
├── values/
│   ├── themes.xml                // 앱 테마 정의
│   ├── colors.xml                // 색상 리소스
│   └── strings.xml               // 문자열 리소스

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

📝 기술 스펙
BLE 광고 사양:
- 광고 간격: 100ms ~ 1000ms (조절 가능)
- 전송 파워: -21dBm ~ 9dBm (디바이스 지원 범위 내)
- 광고 모드: LOW_LATENCY
- 서비스 UUID: 사용자 정의 가능

패킷 구조:
1. Service Data (0x16)
   - 카드번호 (16바이트)
   - 카카오페이 설치 여부 (1바이트: 'Y'/'N')
2. Complete Local Name (0x09)
   - 디바이스 이름 (최대 20바이트)

성능 고려사항:
- 배터리 소비: LOW_LATENCY 모드 사용시 배터리 소비가 증가할 수 있음
- 메모리 사용: 최소 RAM 요구사항 2GB
- 저장공간: 앱 설치시 약 10MB 필요

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