# 문제 해결 가이드 (Troubleshooting)

**빠른 검색용 - 문제별 해결 방법**

---

## 🔍 빠른 찾기

문제 증상으로 검색하세요:
- [디바이스가 안 보임](#-nrf-connect에서-디바이스가-안-보임)
- [연결 안 됨](#-nrf-connect에서-연결-안-됨)
- [Service 안 보임](#-gatt-service-0xfff0-안-보임)
- [Write 오류](#-write-시-파싱-오류)
- [앱 크래시](#-앱이-크래시됨)
- [빌드 오류](#-빌드-오류)

---

## 📱 nRF Connect 관련 문제

### ❌ nRF Connect에서 디바이스가 안 보임

**증상**: Scan 리스트에 mCandle 디바이스가 전혀 안 보임

**체크리스트**:
```
[ ] CardFragment 활성화 (카드 탭 선택)
[ ] 파형 애니메이션 표시 중
[ ] Settings에서 카드번호(16자리) + 전화번호(4자리) 입력됨
[ ] Bluetooth 권한 허용됨
```

**Logcat 확인**:
```bash
# 다음 로그가 있어야 함:
D/CardFragment: 광고 및 GATT Server 시작
D/BLE: Advertising started successfully
D/GattServerManager: GATT Server started successfully
```

**해결 방법**:

1. **앱 재시작**
   ```
   1. 앱 완전 종료
   2. 재실행
   3. 카드 탭 선택
   4. 파형 애니메이션 확인
   ```

2. **Settings 확인**
   ```
   1. 우측 상단 톱니바퀴 클릭
   2. 카드번호: 1234567812345678 (16자리)
   3. 전화번호: 1234 (4자리)
   4. 저장
   ```

3. **권한 확인**
   ```bash
   adb shell dumpsys package com.mcandle.bleapp.v2 | grep permission
   # BLUETOOTH_ADVERTISE, BLUETOOTH_SCAN, BLUETOOTH_CONNECT 확인
   ```

4. **Bluetooth 재시작**
   ```
   Android 설정 → Bluetooth → 끄기 → 켜기
   ```

---

### ❌ nRF Connect에서 연결 안 됨

**증상**: CONNECT 버튼을 눌러도 연결 실패

**원인 1**: `setConnectable(false)` 설정
```kotlin
// AdvertiserManager.kt:45 확인
.setConnectable(true)  // ← true여야 함
```

**원인 2**: 잘못된 디바이스 선택
```
Service Data 0xFE10 없는 다른 디바이스에 연결 시도
```

**해결 방법**:

1. **올바른 디바이스 확인**
   ```
   nRF Connect에서:
   1. "mcandle1" 디바이스 찾기
   2. ▼ 버튼 눌러 확장
   3. Service Data: 0xFE10 확인
   4. Complete Local Name: mcandle1 확인
   5. CONNECT 클릭
   ```

2. **Logcat에서 MAC 주소 대조**
   ```
   nRF Connect MAC: XX:XX:XX:XX:XX:XX
   Logcat: "Client connected: XX:XX:XX:XX:XX:XX"
   → 일치해야 함
   ```

3. **앱 재빌드**
   ```bash
   gradlew.bat clean assembleDebug
   # 재설치 후 테스트
   ```

---

### ❌ GATT Service (0xFFF0) 안 보임

**증상**: 연결은 되지만 커스텀 Service가 보이지 않음

**원인**: Scan Response에 UUID 추가 안 됨

**확인**:
```kotlin
// AdvertisePacketBuilder.kt:44-49
fun buildScanResponse(data: AdvertiseDataModel): AdvertiseData {
    val gattServiceUuid = ParcelUuid.fromString("0000FFF0-0000-1000-8000-00805F9B34FB")
    return AdvertiseData.Builder()
        .setIncludeDeviceName(true)
        .addServiceUuid(gattServiceUuid)  // ← 이 줄 있어야 함
        .build()
}
```

**해결 방법**:

1. **코드 확인 후 재빌드**
   ```bash
   # 위 코드 확인
   gradlew.bat clean assembleDebug
   ```

2. **nRF Connect 캐시 제거**
   ```
   1. DISCONNECT
   2. 디바이스 길게 누르기 → "Remove bond" (있다면)
   3. nRF Connect 앱 종료 (최근 앱에서 스와이프)
   4. nRF Connect 재시작
   5. Scan → Connect
   ```

3. **"DISCOVERING SERVICES..." 완료 대기**
   ```
   최대 5초 정도 소요될 수 있음
   ```

---

### ❌ Write 시 파싱 오류

**증상**:
```
Failed to parse data: abcd1234
Invalid parameter format: abcd1234
```

**원인**: `order_id=` 접두사 없음

**올바른 형식**:
```
✅ order_id=abcd1234
✅ order_id=ORDER-001&phone=1234
❌ abcd1234
❌ order_id:abcd1234
❌ {"order_id": "abcd1234"}
```

**해결 방법**:

1. **nRF Connect에서 다시 전송**
   ```
   1. 0xFFF1 Characteristic 선택
   2. ↑ (Write) 버튼
   3. Data format: Text
   4. Value: order_id=TEST123
   5. SEND
   ```

2. **파라미터 형식 확인**
   ```
   key1=value1&key2=value2&key3=value3
   - & 로 구분
   - = 로 key-value 연결
   - order_id는 필수
   ```

---

## 🛠️ 빌드 & 실행 문제

### ❌ 빌드 오류

**증상 1**: Gradle Sync 실패
```
해결:
1. File → Invalidate Caches → Invalidate and Restart
2. gradlew.bat clean
3. Android Studio 재시작
```

**증상 2**: Kotlin 컴파일 오류
```
해결:
1. build.gradle.kts에서 Kotlin 버전 확인
2. gradle/libs.versions.toml 확인
3. Sync Project with Gradle Files
```

**증상 3**: 의존성 오류
```
해결:
1. gradle/libs.versions.toml 확인
2. 인터넷 연결 확인
3. Gradle 캐시 정리:
   rm -rf ~/.gradle/caches/
```

---

### ❌ 앱이 크래시됨

**증상 1**: SecurityException
```
Logcat:
java.lang.SecurityException: Need BLUETOOTH_ADVERTISE permission
```

**해결**:
```
1. AndroidManifest.xml 권한 확인
2. 앱 재설치
3. 수동 권한 부여:
   Android 설정 → 앱 → mCandle → 권한
```

**증상 2**: NullPointerException in CardFragment
```
Logcat:
java.lang.NullPointerException at CardFragment.kt:xxx
```

**해결**:
```
1. Settings에서 카드번호/전화번호 입력
2. ViewBinding이 null인지 확인
3. Fragment lifecycle 확인
```

**증상 3**: GATT Server 시작 실패
```
Logcat:
E/CardFragment: GATT Server 시작 실패
```

**해결**:
```
1. Bluetooth 켜져 있는지 확인
2. 다른 BLE 앱이 GATT Server 사용 중인지 확인
3. 디바이스 재부팅
```

---

## 📲 nRF Connect 앱 문제

### ❌ nRF Connect에서 서비스가 이상하게 보임

**증상**: Generic Services만 보이고 0xFFF0이 없음

**원인**: 잘못된 디바이스에 연결

**해결**:
```
1. DISCONNECT
2. Scan 리스트에서 Service Data 0xFE10 확인
3. MAC 주소를 Logcat과 대조
4. 올바른 디바이스에 재연결
```

---

### ❌ nRF Connect에서 Write Response "GATT_FAILURE"

**증상**: Write 시 실패 응답

**원인**: Characteristic UUID 잘못됨 또는 데이터 형식 오류

**해결**:
```
1. UUID 확인: 0xFFF1 (Write용)
2. 데이터 형식: order_id=XXX
3. UTF-8 인코딩 확인
4. Logcat에서 에러 메시지 확인
```

---

## 🔧 Settings 관련 문제

### ❌ 카드번호가 저장 안 됨

**증상**: Settings에서 입력했는데 CardFragment에 **** 표시

**해결**:
```
1. SettingsActivity에서 "저장" 버튼 클릭 확인
2. SharedPreferences 확인:
   adb shell run-as com.mcandle.bleapp.v2 cat /data/data/com.mcandle.bleapp.v2/shared_prefs/ble_settings.xml
3. 앱 데이터 초기화:
   adb shell pm clear com.mcandle.bleapp.v2
4. 다시 입력
```

---

### ❌ 디바이스 이름이 변경 안 됨

**증상**: Settings에서 변경했는데 nRF Connect에서 이전 이름

**원인**: nRF Connect 캐시

**해결**:
```
1. nRF Connect 완전 종료
2. 재시작 후 Scan
3. 또는 앱 재시작
```

---

## 🐛 일반적인 Android 문제

### ❌ ADB 연결 안 됨

**해결**:
```bash
adb kill-server
adb start-server
adb devices
# 디바이스가 안 보이면 USB 디버깅 다시 활성화
```

---

### ❌ Android Studio가 느림

**해결**:
```
1. File → Settings → Appearance → Use custom font 끄기
2. Power Save Mode 끄기 (File → Power Save Mode)
3. Gradle 데몬 재시작:
   gradlew.bat --stop
4. 메모리 증가 (studio.vmoptions):
   -Xmx4096m
```

---

### ❌ 앱이 백그라운드에서 죽음

**원인**: 배터리 최적화

**해결**:
```
Android 설정 → 배터리 → 배터리 최적화 → mCandle → 최적화 안 함
```

---

## 📊 Logcat 분석

### 정상 로그 패턴
```
D/CardFragment: 광고 및 GATT Server 시작
D/GattServerManager: GATT Server started successfully
D/GattServerManager: Service UUID: 0000fff0-...
D/BLE: Advertising started successfully
D/GattServerManager: Client connected: XX:XX:XX
D/OrderDataParser: Parsing data: order_id=XXX
D/OrderDataParser: Parsed - orderId: XXX
D/CardFragment: Order received: XXX
```

### 오류 로그 패턴
```
❌ E/CardFragment: GATT Server 시작 실패
❌ E/BLE: Advertising failed: [error code]
❌ E/OrderDataParser: Failed to parse data
❌ E/GattServerManager: Security exception
```

---

## 🔍 심화 디버깅

### Bluetooth 상태 확인
```bash
adb shell dumpsys bluetooth_manager | grep -A 20 "Bluetooth Status"
```

### BLE Advertisement 확인
```bash
adb shell dumpsys bluetooth_manager | grep -A 10 "LE Advertiser"
```

### 앱 메모리 확인
```bash
adb shell dumpsys meminfo com.mcandle.bleapp.v2
```

### 쓰레드 덤프
```bash
adb shell am dumpheap com.mcandle.bleapp.v2 /data/local/tmp/heap.hprof
adb pull /data/local/tmp/heap.hprof
```

---

## 🆘 마지막 수단

### 완전 초기화
```bash
# 1. 앱 삭제
adb uninstall com.mcandle.bleapp.v2

# 2. Gradle 캐시 삭제
gradlew.bat clean
rm -rf ~/.gradle/caches/

# 3. Android Studio 캐시 삭제
File → Invalidate Caches → Invalidate and Restart

# 4. 재빌드
gradlew.bat assembleDebug

# 5. 재설치
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📞 도움 요청 시 포함할 정보

### 체크리스트
```
[ ] 문제 발생 시점 (정확한 시간)
[ ] 재현 단계 (1, 2, 3...)
[ ] 예상 동작 vs 실제 동작
[ ] Logcat 로그 (adb logcat -d > log.txt)
[ ] 스크린샷/화면 녹화
[ ] 디바이스 정보 (모델, Android 버전)
[ ] 앱 버전 (build.gradle.kts의 versionName)
```

### Logcat 수집
```bash
adb logcat -d > logcat_full.txt
adb logcat -d | grep -E "CardFragment|GattServerManager|OrderDataParser" > logcat_filtered.txt
```

---

## 💡 예방 팁

### 개발 시 주의사항
1. **항상 Logcat 확인** - 오류를 조기에 발견
2. **Clean Build 습관** - 캐시 문제 예방
3. **Git Commit 자주** - 롤백 가능하게
4. **테스트 디바이스 여러 대** - 호환성 확인
5. **nRF Connect 버전 최신 유지** - 버그 수정

### 코드 작성 시
```kotlin
// Bad
val data = viewModel.currentData.value!!  // NPE 위험

// Good
val data = viewModel.currentData.value ?: run {
    Log.e(TAG, "Data is null")
    return
}
```

---

**문제가 해결되지 않으면 `DEVELOPMENT_LOG_2025-11-11.md`의 상세 내역을 참고하세요.**

**마지막 업데이트**: 2025-11-11 21:30 KST