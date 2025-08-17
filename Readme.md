# BLE Advertiser & Scanner App

Android BLE (Bluetooth Low Energy) 광고 송신 및 수신 기능을 제공하는 테스트 앱입니다. 매장에서 고객의 카드번호와 전화번호를 BLE로 광고하고, 주변 iBeacon 신호를 스캔하여 결제 요청을 매칭하는 시스템입니다.

## 📱 주요 기능

### 🔵 BLE Advertise (광고 송신)
- **카드번호 (16자리)** 및 **전화번호 (4자리)** BLE 광고
- **ASCII/BCD** 인코딩 방식 선택
- **Minimal/Data** 광고 모드 지원
- **디바이스명** 커스터마이징

### 🔍 BLE Scanner (신호 수신)
- **iBeacon 형태** 신호 감지
- **전화번호 매칭** 기반 자동 결제 팝업
- **3가지 스캔 필터**: ALL, RFSTAR_ONLY, IBEACON_RFSTAR
- **실시간 패킷 로그** 디버깅 기능

### 💳 결제 시스템
- **2단계 팝업**: 결제 요청 도착 → 상세 결제 정보
- **매장 정보**: 엠캔들 잠실점, 직원명
- **상품 목록**: 여성/남성 상의, 수량, 금액
- **할인 정보**: 롯데카드 10% 할인

### ⚙️ 설정 관리
- **SharedPreferences** 기반 설정 저장
- **디바이스명, 인코딩, 광고모드, 스캔필터** 설정
- **상단바 설정 버튼**으로 접근

## 🏗️ 프로젝트 구조

```
app/src/main/java/com/mcandle/bleapp/
├── MainActivity.kt                     # 메인 화면 - BLE 광고/스캔 제어
├── SettingsActivity.kt                 # 설정 화면
│
├── advertise/                          # BLE 광고 관련
│   ├── AdvertiserManager.kt            # BLE 광고 송신 관리
│   └── AdvertisePacketBuilder.kt       # BLE 패킷 데이터 생성
│
├── scan/                               # BLE 스캔 관련
│   ├── BleScannerManager.kt            # BLE 스캔 및 필터링
│   ├── IBeaconParser.kt                # iBeacon 데이터 파싱
│   └── ScanListActivity.kt             # 스캔 결과 리스트 (디버깅용)
│
├── model/                              # 데이터 모델
│   ├── AdvertiseDataModel.kt           # 광고 데이터 모델
│   ├── AdvertiseMode.kt                # 광고 모드 (MINIMAL/DATA)
│   └── EncodingType.kt                 # 인코딩 타입 (ASCII/BCD)
│
├── ui/                                 # UI 컴포넌트
│   └── InputFormFragment.kt            # 카드번호/전화번호 입력 폼
│
├── util/                               # 유틸리티
│   ├── SettingsManager.kt              # 설정 저장/불러오기 관리
│   └── ByteUtils.kt                    # 바이트 배열 처리 유틸
│
└── viewmodel/                          # ViewModel
    └── BleAdvertiseViewModel.kt        # UI 상태 및 데이터 관리
```

## 📋 주요 파일 설명

### 🎯 MainActivity.kt
**역할**: 앱의 메인 진입점, BLE 광고/스캔 통합 제어
- **Advertise Start**: 패킷 적용 + 광고 시작 + 스캔 동시 실행
- **Advertise Stop**: 광고/스캔 모두 중지
- **매칭 감지**: 전화번호 일치 시 2단계 결제 팝업 표시
- **권한 관리**: BLUETOOTH_ADVERTISE, BLUETOOTH_SCAN 권한 처리

### ⚙️ SettingsActivity.kt
**역할**: 고급 설정 관리 화면
- **디바이스명**: BLE 광고에 사용될 기기 이름
- **전송방식**: ASCII(가독성) vs BCD(효율성) 선택
- **광고모드**: MINIMAL(기본) vs DATA(상세) 선택
- **스캔필터**: ALL/RFSTAR_ONLY/IBEACON_RFSTAR 필터링

### 📡 BleScannerManager.kt
**역할**: BLE 스캔 및 필터링 핵심 로직
```kotlin
class BleScannerManager(
    context: Context,
    listener: Listener,
    expectedMinor: Int? = null,
    maxTimeoutMs: Long = 60_000L,
    mode: ScanMode = ScanMode.ALL  // 스캔 필터 모드
)
```

**스캔 필터 구현**:
```kotlin
enum class ScanMode {
    ALL,            // 모든 BLE 디바이스
    RFSTAR_ONLY,    // RFStar 제조사(0x5246)만
    IBEACON_RFSTAR  // RFStar iBeacon(0x02, 0x15)만
}
```

### 📦 AdvertisePacketBuilder.kt
**역할**: BLE 광고 패킷 생성
- **Service Data (0x16)**: 카드번호 + 전화번호 데이터
- **Complete Local Name (0x09)**: 디바이스명
- **ASCII/BCD 인코딩**: 데이터 크기 최적화

### 🔍 IBeaconParser.kt
**역할**: 수신된 BLE 패킷에서 iBeacon 데이터 추출
```kotlin
data class IBeaconFrame(
    val companyId: Int,
    val uuid: String,
    val orderNumber: String,    // UUID에서 추출
    val phoneLast4: String,     // UUID에서 추출
    val major: Int,
    val minor: Int,
    val txPower: Int
)
```

### 💾 SettingsManager.kt
**역할**: SharedPreferences 기반 설정 관리
```kotlin
// 설정 항목들
- getDeviceName() / setDeviceName()
- getEncodingType() / setEncodingType()  
- getAdvertiseMode() / setAdvertiseMode()
- getScanFilter() / setScanFilter()      // 새로 추가
```

## 🔄 앱 동작 흐름

### 📱 메인 사용 시나리오
```
1. 설정 화면에서 디바이스명, 인코딩, 모드 설정
2. 메인 화면에서 카드번호(16자리), 전화번호(4자리) 입력
3. "Advertise Start" 클릭
   ├── 입력 데이터 + 설정값 → BLE 패킷 생성
   ├── BLE 광고 시작 (주변에 정보 브로드캐스트)
   └── BLE 스캔 시작 (매장 iBeacon 감지)
4. 매장 iBeacon 감지 시
   ├── 전화번호 매칭 확인
   ├── "결제 요청 도착" 1차 팝업
   ├── "확인하기" 클릭
   └── "결제 정보" 상세 팝업 (매장정보, 상품목록, 결제금액)
```

### 🔧 디버깅 기능
- **Raw 버튼**: 생성된 BLE 패킷 HEX 데이터 확인
- **스캔 시작**: ScanListActivity로 모든 BLE 패킷 로그 확인
- **로그 출력**: 매칭 과정 및 패킷 정보 상세 로깅

## 🛠️ 기술 스펙

### 📋 개발 환경
- **언어**: Kotlin
- **플랫폼**: Android (minSdk 26, targetSdk 34)
- **IDE**: Android Studio Meerkat Feature Drop
- **패키지명**: com.mcandle.bleapp

### 📚 주요 라이브러리
```gradle
implementation("androidx.core:core-ktx:1.13.1")
implementation("androidx.appcompat:appcompat:1.6.1")
implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
implementation("androidx.fragment:fragment-ktx:1.7.1")
implementation("androidx.activity:activity-ktx:1.9.0")
implementation("com.google.android.material:material:1.12.0")
```

### 🔐 필요 권한
```xml
<!-- Android 12+ (API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Android 11 이하 호환 -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

### 📡 BLE 사양
- **광고 간격**: 100ms ~ 1000ms
- **전송 파워**: -21dBm ~ 9dBm  
- **광고 모드**: LOW_LATENCY
- **패킷 크기**: 최대 31바이트

### 📊 패킷 구조
```
Service Data (0x16):
├── 카드번호: 16바이트 (ASCII) 또는 8바이트 (BCD)
└── 전화번호: 4바이트 (ASCII) 또는 2바이트 (BCD)

Complete Local Name (0x09):
└── 디바이스명: 최대 20바이트
```

## 🎨 UI/UX 특징

### 🖥️ 메인 화면 (단순화)
- **카드번호/전화번호** 입력만 표시
- **큰 입력 필드** (56dp 높이, 18sp 텍스트)
- **색상별 버튼**: 파란색(Start), 빨간색(Stop)

### ⚙️ 설정 화면
- **상단바 설정 아이콘**으로 접근
- **카테고리별 섹션**: 디바이스, 전송방식, 광고모드, 스캔필터
- **일관된 버튼 스타일**: 56dp 높이, 둥근 모서리

### 💳 결제 팝업
- **iOS 스타일** 디자인
- **2단계 확인**: 도착알림 → 상세정보
- **실제 매장 데이터**: 엠캔들 잠실점, 상품목록, 할인정보

## 🔧 빌드 및 실행

### 📱 실행 방법
```bash
1. Android Studio에서 프로젝트 열기
2. Gradle Sync
3. 실제 Android 기기 연결 (BLE 기능 필요)
4. Run 'app'
```

### ⚠️ 주의사항
- **실제 기기 필요**: BLE 기능은 에뮬레이터에서 제한적
- **위치 권한**: Android 11 이하에서 BLE 스캔 시 필요
- **배터리 최적화**: LOW_LATENCY 모드로 인한 배터리 소모 증가

## 🧪 테스트 방법

### 📡 BLE 광고 테스트
1. **nRF Connect** 앱으로 광고 패킷 확인
2. **Raw 버튼**으로 생성된 패킷 데이터 검증
3. **EFR32BG22** 같은 BLE 수신 장비로 호환성 확인

### 🔍 BLE 스캔 테스트  
1. **다른 기기**에서 iBeacon 앱 실행
2. **스캔 시작 버튼**으로 ScanListActivity에서 패킷 로그 확인
3. **전화번호 매칭** 시 결제 팝업 동작 확인

### ⚙️ 설정 테스트
1. **설정 변경** 후 앱 재시작하여 유지 확인
2. **다양한 조합**으로 BLE 패킷 생성 테스트
3. **스캔 필터** 별로 수신되는 디바이스 종류 확인

## 🚀 향후 확장 계획

### 📊 Supabase 연동
- 현재 **하드코딩된 매장/상품 데이터**를 Supabase DB에서 실시간 로드
- **주문 이력 관리** 및 **결제 상태 동기화**

### 🔒 보안 강화
- **BLE 패킷 암호화** (AES-256)
- **디지털 서명** 기반 패킷 무결성 검증
- **재전송 공격 방지** (Nonce 기반)

### 📱 UI 개선
- **다크 모드** 지원
- **다국어** 지원 (한국어/영어)
- **접근성** 개선 (TalkBack 지원)

## 📞 문의 및 기여

### 🐛 버그 리포트
이슈 발견 시 GitHub Issues에 다음 정보와 함께 제보해주세요:
- **기기 모델** 및 **Android 버전**
- **재현 단계** 상세 설명
- **로그 출력** (가능한 경우)

### 🔧 기여 방법
1. **Fork** 프로젝트
2. **Feature Branch** 생성 (`git checkout -b feature/amazing-feature`)
3. **Commit** 변경사항 (`git commit -m 'Add amazing feature'`)
4. **Push** to Branch (`git push origin feature/amazing-feature`)
5. **Pull Request** 생성

---

**📅 최종 업데이트**: 2025년 8월
**🏷️ 버전**: v1.0.0
**👨‍💻 개발자**: BLE Advertiser Team

> 💡 **참고**: 본 앱은 BLE 테스트/개발 목적이며, 상용 환경에서는 추가 보안 조치가 필요할 수 있습니다.