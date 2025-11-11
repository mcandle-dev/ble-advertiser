# 개발 작업 기록 - 2025-11-11

## 📋 작업 개요

**목표**: nRF Connect 앱에서 mCandle BLE 앱에 연결하여 주문 데이터를 전송할 수 있도록 구현

**작업 기간**: 2025-11-11
**담당**: Claude Code & User

---

## 🔍 발견된 문제

### 문제 1: nRF Connect에서 연결(Connect) 불가

**증상**:
- nRF Connect에서 mCandle 디바이스가 Scan 리스트에는 표시됨
- CONNECT 버튼 클릭 시 연결 실패 또는 GATT Service가 보이지 않음

**원인**:
```kotlin
// AdvertiserManager.kt:45
.setConnectable(false)  // ← 연결 차단
```

**해결**:
```kotlin
// AdvertiserManager.kt:45
.setConnectable(true)  // ← 연결 허용
```

---

### 문제 2: GATT Service UUID가 nRF Connect에 표시되지 않음

**증상**:
- nRF Connect에서 연결은 되지만
- mCandle의 커스텀 GATT Service (0xFFF0)가 보이지 않음
- Generic Services만 표시됨

**원인**:
- BLE Legacy Advertisement는 31 bytes 크기 제한이 있음
- DATA 모드에서 Advertisement 패킷에 다음 데이터 포함:
  - Service Data (0xFE10): 카드번호 + 전화번호 (~20 bytes)
  - TX Power Level (~3 bytes)
  - Device Name (~8 bytes)
- **총 31+ bytes → GATT Service UUID를 추가할 공간 없음**

**해결**:
```kotlin
// AdvertisePacketBuilder.kt:44-49
fun buildScanResponse(data: AdvertiseDataModel): AdvertiseData {
    val gattServiceUuid = ParcelUuid.fromString("0000FFF0-0000-1000-8000-00805F9B34FB")
    return AdvertiseData.Builder()
        .setIncludeDeviceName(true)
        .addServiceUuid(gattServiceUuid)  // ← Scan Response에 추가
        .build()
}
```

**핵심 아이디어**:
- Advertisement 패킷: Service Data + TX Power (데이터 전송용)
- Scan Response 패킷: Device Name + GATT UUID (서비스 발견용)
- 31 bytes 제한을 두 패킷으로 분산

---

### 문제 3: 잘못된 디바이스에 연결

**증상**:
- nRF Connect에서 CONNECT 버튼 클릭
- 하지만 Logcat에 "Client connected" 메시지 없음
- MAC 주소 불일치

**원인**:
- 주변에 여러 BLE 디바이스 존재
- nRF Connect가 다른 디바이스에 연결함

**해결 방법**:
1. nRF Connect Scan 리스트에서 각 디바이스 확장 (▼ 버튼)
2. **Service Data 0xFE10**이 있는 디바이스 찾기
3. **Complete Local Name: "mcandle1"** 확인
4. **Complete list of 16-bit Service UUIDs: 0xFFF0** 확인
5. 올바른 디바이스 CONNECT
6. Logcat에서 "Client connected: XX:XX:XX" MAC 주소 일치 확인

---

### 문제 4: Order Data 전송 형식 오류

**증상**:
```
Failed to parse data: abcd1234
Invalid parameter format: abcd1234
```

**원인**:
- OrderDataParser는 **URL 파라미터 형식**을 기대함
- 단순 문자열 전송 시 파싱 실패

**올바른 전송 형식**:
```
order_id=abcd1234
```

또는 추가 파라미터 포함:
```
order_id=ORDER-001&phone=1234&amount=15000
```

---

## ✅ 구현된 기능

### 1. BLE 연결 지원

**파일**: `AdvertiserManager.kt`

**변경 사항**:
```kotlin
val settings = AdvertiseSettings.Builder()
    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
    .setConnectable(true)  // ← 변경
    .build()
```

**효과**:
- nRF Connect에서 CONNECT 버튼 작동
- GATT Server 연결 가능

---

### 2. GATT Service 발견 지원

**파일**: `AdvertisePacketBuilder.kt`

**변경 사항**:
```kotlin
fun buildScanResponse(data: AdvertiseDataModel): AdvertiseData {
    val gattServiceUuid = ParcelUuid.fromString("0000FFF0-0000-1000-8000-00805F9B34FB")
    return AdvertiseData.Builder()
        .setIncludeDeviceName(true)
        .addServiceUuid(gattServiceUuid)  // ← 추가
        .build()
}
```

**효과**:
- nRF Connect에서 "Unknown Service (0xFFF0)" 표시
- Characteristics 확인 가능:
  - 0xFFF1 (WRITE): Scanner → Store 데이터 전송용
  - 0xFFF2 (READ): Store → Scanner 응답 전송용

---

### 3. 주문번호 표시 기능

**파일**: `payment_detail_dialog.xml`

**추가된 UI**:
```xml
<!-- 주문번호 -->
<TextView
    android:id="@+id/tvOrderNumber"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="주문번호: -"
    android:textSize="14sp"
    android:textColor="#666666"
    android:layout_marginBottom="16dp" />
```

**위치**: 결제 정보 타이틀 바로 아래

---

**파일**: `CardFragment.kt`

**변경 사항**:
```kotlin
private fun showOrderDetailDialog(orderId: String, additionalData: Map<String, String>?) {
    val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.payment_detail_dialog, null)

    // 주문번호 표시
    dialogView.findViewById<TextView>(R.id.tvOrderNumber).text = "주문번호: $orderId"

    // ... 나머지 코드
}
```

**효과**:
- 결제 정보 다이얼로그에 주문번호 표시
- 예: "주문번호: ORDER-12345"

---

## 🧪 테스트 결과

### 1. nRF Connect 연결 테스트

**절차**:
1. mCandle 앱 실행 → CardFragment (카드 탭) 선택
2. 파형 애니메이션 시작 확인
3. nRF Connect 앱 실행 → SCAN
4. "mcandle1" 디바이스 확장 (▼)
5. Service Data 0xFE10 확인
6. Service UUIDs: 0xFFF0 확인
7. CONNECT 버튼 클릭

**결과**: ✅ 성공
- Connected 표시
- Logcat: "Client connected: XX:XX:XX:XX:XX:XX"
- GATT Services 표시:
  - Generic Attribute (0x1801)
  - Generic Access (0x1800)
  - Generic Media Control Service (0x1849)
  - Generic Telephone Bearer Service (0x184C)
  - Telephony and Media Audio Service (0x1855)
  - **Unknown Service (0xFFF0)** ← mCandle GATT Service

---

### 2. GATT Write 테스트

**절차**:
1. nRF Connect에서 0xFFF0 Service 확장
2. Characteristic 0xFFF1 (WRITE) 선택
3. ↑ (업로드) 버튼 클릭
4. Data format: Text
5. Value: `order_id=TEST-12345`
6. SEND 클릭

**결과**: ✅ 성공
```
D/OrderDataParser: Parsing data: order_id=TEST-12345
D/OrderDataParser: Parsed - orderId: TEST-12345, additionalData: {}
D/GattServerManager: Order parsed - ID: TEST-12345
D/CardFragment: Order received: TEST-12345
```

**앱 동작**:
1. 광고 및 GATT Server 중지
2. 1단계 다이얼로그: "결제 요청이 도착했습니다" → "확인하기" 클릭
3. 2단계 다이얼로그: 결제 정보 표시
   - **주문번호: TEST-12345** ← 표시됨
   - 매장 정보, 구매 목록, 결제 금액 등

---

### 3. 추가 파라미터 테스트

**전송 데이터**:
```
order_id=ORDER-001&phone=1234&amount=25000&store=잠실점
```

**결과**: ✅ 성공
```
D/OrderDataParser: Parsed - orderId: ORDER-001, additionalData: {phone=1234, amount=25000, store=잠실점}
```

---

## 📦 변경된 파일 목록

### 주요 파일 (Commit 포함)

1. **`AdvertiserManager.kt`**
   - Line 45: `setConnectable(true)` 변경

2. **`AdvertisePacketBuilder.kt`**
   - Line 44-49: Scan Response에 GATT UUID 추가

3. **`CardFragment.kt`**
   - Line 189-190: 주문번호 표시 로직 추가

4. **`payment_detail_dialog.xml`**
   - Line 43-51: 주문번호 TextView 추가

---

## 🏗️ BLE 아키텍처

### Advertisement 패킷 구조

```
┌─────────────────────────────────────┐
│     Advertisement Packet (31B)      │
├─────────────────────────────────────┤
│ Service Data (0xFE10)               │
│  - 카드번호 (16자리)                │
│  - 전화번호 (4자리)                 │
│ TX Power Level                      │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│     Scan Response Packet (31B)      │
├─────────────────────────────────────┤
│ Complete Local Name: "mcandle1"     │
│ Service UUID: 0xFFF0                │
└─────────────────────────────────────┘
```

---

### GATT Service 구조

```
Service: 0000FFF0-0000-1000-8000-00805F9B34FB
├─ Characteristic: 0xFFF1 (WRITE, WRITE_NO_RESPONSE)
│  └─ Scanner → Store: Order 데이터 전송
│     형식: order_id=XXX&param1=value1&param2=value2
│
└─ Characteristic: 0xFFF2 (READ, NOTIFY)
   └─ Store → Scanner: 응답 데이터
      형식: {"status": "success", "message": "..."}
```

---

### 통신 플로우

```
┌─────────────┐                    ┌─────────────┐
│  nRF Connect │                    │ mCandle App │
│  (Scanner)   │                    │  (Store)    │
└──────┬───────┘                    └──────┬──────┘
       │                                   │
       │ 1. Scan                           │
       │◄──────────────────────────────────┤
       │   Advertisement (Service Data)    │
       │   Scan Response (Service UUID)    │
       │                                   │
       │ 2. Connect                        │
       ├──────────────────────────────────►│
       │                                   │
       │ 3. Service Discovery              │
       ├──────────────────────────────────►│
       │◄──────────────────────────────────┤
       │   GATT Services (0xFFF0, etc)     │
       │                                   │
       │ 4. Write 0xFFF1                   │
       │    "order_id=ORDER-001"           │
       ├──────────────────────────────────►│
       │                                   ├─> Parse order data
       │                                   ├─> Show dialog
       │◄──────────────────────────────────┤
       │   Write Response: SUCCESS         │
       │                                   │
       │ 5. (Optional) Read 0xFFF2         │
       ├──────────────────────────────────►│
       │◄──────────────────────────────────┤
       │   {"status": "success", ...}      │
       │                                   │
```

---

## 🎯 주요 학습 내용

### 1. BLE Advertisement 크기 제한

**Legacy BLE Advertisement**: 31 bytes 최대
- Advertisement Packet: 31 bytes
- Scan Response Packet: 31 bytes (추가)
- **총 62 bytes 활용 가능**

**전략**:
- 자주 변하는 데이터 (카드번호) → Advertisement
- 고정 데이터 (Service UUID, Device Name) → Scan Response

---

### 2. setConnectable() 동작

**setConnectable(false)**:
- Broadcast-only 모드
- nRF Connect에서 CONNECT 불가
- 배터리 효율적
- GATT Server 불필요

**setConnectable(true)**:
- Connection 가능
- GATT Server 필요
- 양방향 통신 가능
- 배터리 소모 증가

---

### 3. nRF Connect 디바이스 식별

**문제**: 주변에 여러 BLE 디바이스 존재

**해결**:
1. **Service Data 확인**: 0xFE10 UUID 포함 여부
2. **Device Name 확인**: "mcandle1" 등
3. **Service UUIDs 확인**: 0xFFF0 포함 여부
4. **MAC 주소 대조**: Logcat과 nRF Connect MAC 주소 일치

---

### 4. GATT Write 데이터 형식

**OrderDataParser 요구사항**:
```
order_id=VALUE                           // 기본
order_id=VALUE&key1=val1&key2=val2      // 추가 파라미터
```

**잘못된 형식**:
```
abcd1234          // ❌ order_id= 접두사 없음
order_id:12345    // ❌ = 대신 : 사용
```

---

## 📊 성능 측정

### Advertisement 간격
- Mode: ADVERTISE_MODE_LOW_LATENCY
- 예상 간격: ~100ms

### GATT 연결 시간
- Scan → Connect: ~1-2초
- Service Discovery: ~0.5-1초
- Write 응답: ~100-200ms

### 배터리 소모
- Advertising Only: 낮음
- Advertising + GATT Connection: 중간
- Recommendation: 60초 타임아웃 사용 중 (CardFragment.kt:312)

---

## 🐛 트러블슈팅 가이드

### 문제: nRF Connect에서 디바이스가 안 보임

**체크리스트**:
- [ ] CardFragment 활성화 (카드 탭 선택)
- [ ] Logcat: "Advertising started successfully"
- [ ] 파형 애니메이션 표시 중
- [ ] Bluetooth 권한 허용
- [ ] Settings에서 카드번호/전화번호 입력됨

---

### 문제: CONNECT 버튼을 눌러도 연결 안 됨

**체크리스트**:
- [ ] `setConnectable(true)` 설정 확인
- [ ] Logcat: "GATT Server started successfully"
- [ ] 올바른 디바이스 선택 (Service Data 0xFE10 확인)
- [ ] MAC 주소 일치 확인

---

### 문제: GATT Service (0xFFF0)가 안 보임

**체크리스트**:
- [ ] Scan Response에 UUID 추가됨 (AdvertisePacketBuilder.kt:48)
- [ ] 앱 재빌드 및 재설치
- [ ] nRF Connect 앱 재시작
- [ ] "DISCOVERING SERVICES..." 완료 대기

---

### 문제: Write 시 파싱 오류

**체크리스트**:
- [ ] `order_id=` 접두사 포함
- [ ] `&` 로 파라미터 구분
- [ ] `=` 로 key-value 구분
- [ ] UTF-8 인코딩 사용

---

## 📝 Git Commit 이력

### Commit: `566a7b7`

**메시지**:
```
Add nRF Connect connectivity and order number display

- Enable BLE connection: set setConnectable(true) in AdvertiserManager
- Add GATT Service UUID (0xFFF0) to Scan Response for service discovery
- Add order number display in payment detail dialog
- Support order data reception via GATT Write characteristic

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```

**변경 파일**:
- `AdvertisePacketBuilder.kt` (+2 lines)
- `AdvertiserManager.kt` (+1 / -1 lines)
- `CardFragment.kt` (+5 / -1 lines)
- `payment_detail_dialog.xml` (+10 lines)

**총 변경**: 4 files, 17 insertions(+), 2 deletions(-)

---

## 🚀 향후 개선 사항

### 1. Dynamic Product List
현재: Hardcoded 상품 목록
개선: additionalData에서 상품 정보 파싱

### 2. Response Characteristic 활용
현재: Write만 사용
개선: Read/Notify로 Store → Scanner 응답 전송

### 3. BLE 5.0 Extended Advertising
현재: Legacy Advertisement (31 bytes)
개선: Extended Advertising (최대 255 bytes)

### 4. Connection Timeout 최적화
현재: 60초 고정
개선: 사용자 설정 가능

### 5. Multi-device Support
현재: 단일 연결
개선: 여러 Scanner 동시 연결 지원

---

## 📚 참고 자료

### BLE Specification
- Advertisement 패킷 구조
- GATT Service/Characteristic
- Connection Parameters

### Android BLE API
- `BluetoothLeAdvertiser`
- `BluetoothGattServer`
- `AdvertiseSettings`

### Tools
- **nRF Connect**: BLE 테스트 앱
- **Logcat**: Android 디버깅
- **GitHub**: 버전 관리

---

## 👥 기여자

- **Developer**: mcandle.dev
- **AI Assistant**: Claude Code
- **Date**: 2025-11-11

---

## 📄 라이선스

이 프로젝트는 mCandle의 소유입니다.

---

**마지막 업데이트**: 2025-11-11 21:00 KST
