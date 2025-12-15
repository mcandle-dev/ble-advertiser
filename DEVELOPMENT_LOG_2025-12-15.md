# 개발 작업 기록 - 2025-12-15

## 📋 작업 개요

**목표**: Android 15 BLE 정책 준수를 위한 GATT Server Race Condition 수정

**작업 기간**: 2025-12-15
**담당**: Claude Code & User
**브랜치**: `claude/fix-ble-race-condition-LgBDL`

---

## 🔍 발견된 문제

### Android 15 Play Store 리뷰 지적사항: GATT Server 설정 순서 (Race Condition)

**증상**:
- Google Play Android 15 리뷰에서 BLE advertise 관련 Race Condition 지적
- `startAdvertising()`을 먼저 하고, 나중에 `addService()`를 하는 경우
- 스캐너가 너무 빨리 접속하면 서비스가 아직 등록되지 않은 상태일 수 있음

**리뷰 요구사항**:
```
반드시 server.addService()가 완료된 후(onServiceAdded 콜백 확인)에
startAdvertising()을 시작하도록 순서를 고정하세요.
```

**기존 코드의 문제**:
```kotlin
// CardFragment.kt:355 (수정 전)
private fun startAdvertiseAndGatt(...) {
    Handler(...).postDelayed({
        // ❌ 잘못된 순서: Advertise 먼저 시작
        advertiserManager.startAdvertise(currentData)  // T=100ms

        // ❌ GATT Server 나중에 시작
        val gattStarted = gattServerManager.startGattServer()  // T=100.5ms

        startWaitingEffects()
    }, 100)
}
```

```kotlin
// GattServerManager.kt:110 (수정 전)
fun startGattServer(): Boolean {
    // ...
    val result = bluetoothGattServer?.addService(service) ?: false

    // ❌ onServiceAdded 콜백을 기다리지 않고 바로 return
    return result
}
```

**문제점**:
1. ❌ Advertise를 먼저 시작하고 GATT Server를 나중에 시작 (역순)
2. ❌ `addService()` 호출 후 `onServiceAdded` 콜백을 기다리지 않음
3. ❌ `BluetoothGattServerCallback`에 `onServiceAdded` 구현 없음
4. ❌ 자동 Scanner가 광고 즉시 감지하여 연결 시도 시 GATT Service가 준비되지 않을 수 있음

**Race Condition 시나리오**:
```
T=0ms   : startAdvertise() 호출 → BLE 광고 시작
T=10ms  : 자동 Scanner가 광고 감지
T=20ms  : Scanner가 GATT 연결 시도
T=30ms  : startGattServer() 호출 시작
T=40ms  : addService() 호출 (아직 완료 안됨)
T=50ms  : ❌ Scanner가 연결했지만 Service가 없음 → 연결 실패
T=60ms  : onServiceAdded 콜백 (너무 늦음)
```

---

## ✅ 해결 방법

### 1. GattServerManager 수정

#### ✅ `onGattServerReady()` 콜백 인터페이스 추가

**위치**: `app/src/main/java/com/mcandle/bleapp/gatt/GattServerManager.kt:32-38`

```kotlin
interface GattServerCallback {
    /**
     * GATT Server와 Service가 준비 완료되었을 때 호출
     * 이 콜백 이후에 BLE Advertise를 시작해야 함
     *
     * @param success Service 등록 성공 여부
     */
    fun onGattServerReady(success: Boolean)

    // ... 기존 콜백들 ...
}
```

**설명**: GATT Service 등록이 완료되었을 때 알림을 받기 위한 콜백 추가

---

#### ✅ `onServiceAdded()` 콜백 구현

**위치**: `app/src/main/java/com/mcandle/bleapp/gatt/GattServerManager.kt:153-162`

```kotlin
private val gattServerCallback = object : BluetoothGattServerCallback() {

    override fun onServiceAdded(status: Int, service: BluetoothGattService) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "✅ Service added successfully: ${service.uuid}")
            Log.d(TAG, "GATT Server is ready. Safe to start advertising now.")
            callback.onGattServerReady(true)  // ← 준비 완료 통지
        } else {
            Log.e(TAG, "❌ Failed to add service: status=$status")
            callback.onGattServerReady(false)
        }
    }

    // ... 기존 콜백들 ...
}
```

**설명**: `addService()` 비동기 완료를 감지하여 상위 레이어에 통지

---

#### ✅ `startGattServer()` 비동기 변경

**위치**: `app/src/main/java/com/mcandle/bleapp/gatt/GattServerManager.kt:74-126`

**수정 전**:
```kotlin
fun startGattServer(): Boolean {
    // ...
    val result = bluetoothGattServer?.addService(service) ?: false
    return result  // ❌ 즉시 반환
}
```

**수정 후**:
```kotlin
fun startGattServer() {  // ← 반환값 제거 (비동기)
    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
        Log.e(TAG, "Bluetooth is not available or not enabled")
        callback.onGattServerReady(false)  // ← 즉시 실패 통지
        return
    }

    try {
        bluetoothGattServer = bluetoothManager.openGattServer(context, gattServerCallback)

        if (bluetoothGattServer == null) {
            Log.e(TAG, "Failed to open GATT server")
            callback.onGattServerReady(false)
            return
        }

        // Service 생성
        val service = BluetoothGattService(...)
        // ... Characteristic 추가 ...

        // Service를 GATT Server에 추가 (비동기)
        val result = bluetoothGattServer?.addService(service) ?: false

        if (!result) {
            Log.e(TAG, "Failed to initiate addService()")
            callback.onGattServerReady(false)
        } else {
            Log.d(TAG, "addService() initiated, waiting for onServiceAdded callback...")
            // ← onServiceAdded에서 성공 통지
        }
    } catch (e: SecurityException) {
        Log.e(TAG, "Security exception starting GATT server", e)
        callback.onGattServerReady(false)
    }
}
```

**변경 사항**:
- 반환 타입을 `Boolean` → `Unit`으로 변경 (비동기 패턴)
- 에러 발생 시 즉시 `callback.onGattServerReady(false)` 호출
- 성공 시 `onServiceAdded` 콜백에서 `callback.onGattServerReady(true)` 호출

---

### 2. CardFragment 수정

#### ✅ `onGattServerReady()` 콜백 구현

**위치**: `app/src/main/java/com/mcandle/bleapp/fragment/CardFragment.kt:141-169`

```kotlin
override fun onGattServerReady(success: Boolean) {
    requireActivity().runOnUiThread {
        if (success) {
            Log.d("CardFragment", "✅ GATT Server 준비 완료 - 이제 Advertise 시작")

            // GATT Server가 준비되었으므로 이제 안전하게 Advertise 시작
            val currentData = viewModel.currentData.value
            if (currentData != null) {
                advertiserManager.startAdvertise(currentData)  // ← 여기서 Advertise 시작!
                Log.d("CardFragment", "BLE Advertise 시작 완료")

                // 시각적 효과 시작
                startWaitingEffects()
            } else {
                Log.e("CardFragment", "ViewModel 데이터가 null입니다")
                showToast("데이터 오류가 발생했습니다")
                stopAdvertiseAndGatt()
                binding.btnToggle.visibility = View.VISIBLE
                binding.btnToggle.text = "결제 시작"
            }
        } else {
            Log.e("CardFragment", "❌ GATT Server 시작 실패")
            showToast("GATT Server 시작 실패")
            stopAdvertiseAndGatt()
            binding.btnToggle.visibility = View.VISIBLE
            binding.btnToggle.text = "결제 시작"
        }
    }
}
```

**설명**: GATT Server 준비 완료 후에만 Advertise 시작

---

#### ✅ `startAdvertiseAndGatt()` 순서 변경

**위치**: `app/src/main/java/com/mcandle/bleapp/fragment/CardFragment.kt:368-393`

**수정 전**:
```kotlin
private fun startAdvertiseAndGatt(...) {
    stopAdvertiseAndGatt()

    Handler(...).postDelayed({
        viewModel.updateData(...)
        viewModel.setAdvertising(true)

        // ❌ Advertise 먼저 시작
        advertiserManager.startAdvertise(currentData)

        // ❌ GATT 나중에 시작
        val gattStarted = gattServerManager.startGattServer()

        startWaitingEffects()
    }, 100)
}
```

**수정 후**:
```kotlin
@SuppressLint("MissingPermission")
private fun startAdvertiseAndGatt(cardNumber: String, phone4: String) {
    // 🔥 1. 기존 advertise/GATT가 있으면 무조건 먼저 중지
    stopAdvertiseAndGatt()
    Log.d("CardFragment", "기존 advertise/GATT 중지 후 100ms 대기")

    // 🔥 2. 잠깐 대기 (이전 advertise/GATT 완전 종료 대기)
    Handler(Looper.getMainLooper()).postDelayed({
        // ViewModel 업데이트 - 전체 파라미터 전달
        val deviceName = settingsManager.getDeviceName()
        val encoding = settingsManager.getEncodingType()
        val advMode = settingsManager.getAdvertiseMode()
        viewModel.updateData(cardNumber, phone4, deviceName, encoding, advMode)
        viewModel.setAdvertising(true)

        // 🔥 3. GATT Server를 먼저 시작 (비동기)
        // onServiceAdded 콜백에서 Service 등록 완료를 확인한 후
        // onGattServerReady()에서 Advertise 시작
        Log.d("CardFragment", "🚀 GATT Server 시작 (Service 등록 대기 중...)")
        gattServerManager.startGattServer()

        // ⚠️ Advertise는 onGattServerReady() 콜백에서 시작됨
        // 이렇게 하면 Race Condition 방지 (Android 15 요구사항)

        Log.d("CardFragment", "GATT Server 시작 요청 완료 - 카드: $cardNumber, 폰: $phone4")
    }, 100) // 100ms delay
}
```

**변경 사항**:
- ✅ GATT Server만 먼저 시작 (비동기)
- ✅ Advertise는 `onGattServerReady()` 콜백에서 시작
- ✅ 시각적 효과(`startWaitingEffects()`)도 콜백에서 시작

---

## 🔄 새로운 실행 순서 (Race Condition 방지)

### 수정 전 (잘못된 순서)
```
T=0ms   : startAdvertiseAndGatt() 호출
T=100ms : advertiserManager.startAdvertise() ← ❌ Advertise 먼저
T=101ms : gattServerManager.startGattServer() ← ❌ GATT 나중
T=110ms : addService() 호출 (비동기)
T=200ms : onServiceAdded 콜백 (늦음)
T=500ms : Scanner 연결 시도 → ✅ 성공 (운이 좋은 경우)
T=50ms  : Scanner 빠른 연결 → ❌ 실패 (Race Condition 발생)
```

### 수정 후 (올바른 순서)
```
T=0ms   : startAdvertiseAndGatt() 호출
T=100ms : gattServerManager.startGattServer() ← ✅ GATT 먼저
T=110ms : openGattServer() 완료
T=120ms : addService() 호출 (비동기)
T=130ms : onServiceAdded() 콜백 호출
T=131ms : onGattServerReady(true) 콜백 호출
T=132ms : advertiserManager.startAdvertise() ← ✅ Advertise 나중
T=140ms : BLE 광고 시작
T=1000ms: Scanner가 광고 감지 및 연결 시도
T=1010ms: ✅ GATT Service 이미 준비됨 → 연결 성공!
```

**핵심 차이점**:
- ✅ GATT Service 등록 완료 **후** Advertise 시작
- ✅ `onServiceAdded` 콜백으로 준비 완료 확인
- ✅ 어떤 타이밍에 Scanner가 연결해도 안전

---

## ✅ Android 15 리뷰 요구사항 충족

| 요구사항 | 수정 전 | 수정 후 | 상태 |
|---------|--------|--------|-----|
| `addService()` 완료 후 `startAdvertising()` 호출 | ❌ 역순 | ✅ 올바른 순서 | ✅ 완료 |
| `onServiceAdded` 콜백 확인 | ❌ 미구현 | ✅ 구현됨 | ✅ 완료 |
| GATT Server 준비 완료 대기 | ❌ 대기 안함 | ✅ 콜백으로 대기 | ✅ 완료 |
| Race Condition 방지 | ❌ 위험 존재 | ✅ 완전 제거 | ✅ 완료 |

---

## 📦 수정된 파일

### 1. `app/src/main/java/com/mcandle/bleapp/gatt/GattServerManager.kt`
- `GattServerCallback` 인터페이스에 `onGattServerReady(success: Boolean)` 추가
- `BluetoothGattServerCallback`에 `onServiceAdded()` 구현
- `startGattServer()` 반환 타입 `Boolean` → `Unit` (비동기 패턴)
- 에러 처리 시 `callback.onGattServerReady(false)` 호출
- Service 등록 완료 시 `callback.onGattServerReady(true)` 호출

### 2. `app/src/main/java/com/mcandle/bleapp/fragment/CardFragment.kt`
- `onGattServerReady(success: Boolean)` 콜백 구현
- GATT 준비 완료 후 Advertise 시작 로직 추가
- `startAdvertiseAndGatt()` 메서드 수정: GATT 먼저, Advertise는 콜백에서

---

## 🎯 테스트 시나리오

### 테스트 1: 정상 시나리오
1. 카드 탭 진입
2. GATT Server 시작 로그 확인: `🚀 GATT Server 시작 (Service 등록 대기 중...)`
3. Service 등록 완료 로그: `✅ Service added successfully`
4. Advertise 시작 로그: `✅ GATT Server 준비 완료 - 이제 Advertise 시작`
5. nRF Connect에서 연결 시도
6. ✅ GATT Service 정상 표시 및 연결 성공

### 테스트 2: 빠른 연결 시나리오
1. Advertise 시작 즉시 Scanner가 연결 시도
2. ✅ GATT Service가 이미 준비된 상태이므로 연결 성공

### 테스트 3: 실패 시나리오
1. Bluetooth 꺼진 상태에서 시작
2. `onGattServerReady(false)` 호출 확인
3. ✅ 에러 토스트 표시 및 UI 원상복구

---

## 📊 커밋 정보

**커밋 해시**: `1a9d643`
**브랜치**: `claude/fix-ble-race-condition-LgBDL`
**커밋 메시지**:
```
Fix BLE GATT Server race condition for Android 15 compliance

Android 15 Play Store review requires GATT service to be fully ready
before starting BLE advertising to prevent race conditions when scanners
connect immediately.

Changes:
- Add onGattServerReady() callback to GattServerCallback interface
- Implement onServiceAdded() in BluetoothGattServerCallback
- Convert startGattServer() to async callback pattern
- Modify CardFragment to start advertising only after GATT is ready

Sequence (before):
1. startAdvertise() → 2. startGattServer() ❌ Race condition

Sequence (after):
1. startGattServer() → 2. onServiceAdded → 3. onGattServerReady → 4. startAdvertise() ✅

This ensures the GATT service is fully registered before any scanner
can connect, eliminating the race condition scenario.
```

**변경 통계**:
- 2 files changed
- 73 insertions(+)
- 30 deletions(-)

---

## 💡 핵심 개선 사항

### 1. 안정성 향상
- ✅ Race Condition 완전 제거
- ✅ 자동 Scanner 환경 대응
- ✅ Edge case 방지 (시스템 부하, 저사양 기기)

### 2. 표준 준수
- ✅ Android 15 BLE 정책 준수
- ✅ 업계 표준 BLE 시퀀스 준수
- ✅ Google Play 리뷰 통과 가능

### 3. 코드 품질
- ✅ 비동기 패턴 적용 (콜백 기반)
- ✅ 명확한 에러 처리
- ✅ 상세한 로그 추가

---

## 📌 향후 작업

- [ ] 실제 기기에서 테스트 (Android 15)
- [ ] nRF Connect 앱으로 빠른 연결 테스트
- [ ] Google Play 리뷰 재제출
- [ ] 자동 결제 단말기와 연동 테스트

---

## 📝 참고 자료

- Android Developers - [BluetoothGattServerCallback](https://developer.android.com/reference/android/bluetooth/BluetoothGattServerCallback)
- Android Developers - [onServiceAdded](https://developer.android.com/reference/android/bluetooth/BluetoothGattServerCallback#onServiceAdded(int,%20android.bluetooth.BluetoothGattService))
- Google Play Console - Android 15 BLE 정책

---

**작업 완료**: 2025-12-15
**상태**: ✅ 완료 및 푸시 완료
