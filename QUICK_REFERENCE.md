# 빠른 참조 가이드 - mCandle BLE 앱

**작성일**: 2025-11-11
**최종 업데이트**: 2025-11-11

---

## 📌 5분 요약

### 오늘 구현한 기능
1. ✅ **nRF Connect 연결 지원** - 외부 BLE 앱에서 연결 가능
2. ✅ **GATT Service 발견** - 커스텀 Service (0xFFF0) 노출
3. ✅ **주문 데이터 수신** - Write Characteristic으로 주문번호 받기
4. ✅ **주문번호 표시** - 결제 다이얼로그에 자동 표시

### 핵심 코드 변경 (3줄)
```kotlin
// 1. AdvertiserManager.kt:45
.setConnectable(true)  // false → true

// 2. AdvertisePacketBuilder.kt:48
.addServiceUuid(gattServiceUuid)  // Scan Response에 추가

// 3. CardFragment.kt:190
tvOrderNumber.text = "주문번호: $orderId"  // UI 업데이트
```

---

## 🚀 5초 테스트 방법

### nRF Connect에서 테스트
```
1. mCandle 앱 실행 → 카드 탭
2. nRF Connect → SCAN
3. "mcandle1" 찾기 → CONNECT
4. Service 0xFFF0 → Char 0xFFF1 (Write)
5. Text 입력: order_id=TEST123
6. SEND → 앱에서 다이얼로그 확인
```

---

## 📂 변경된 파일 위치

```
app/src/main/java/com/mcandle/bleapp/
├── advertise/
│   ├── AdvertiserManager.kt          ← setConnectable(true)
│   └── AdvertisePacketBuilder.kt     ← GATT UUID 추가
├── fragment/
│   └── CardFragment.kt               ← 주문번호 표시
└── res/layout/
    └── payment_detail_dialog.xml     ← 주문번호 TextView
```

---

## 🔑 핵심 개념

### BLE Advertisement 구조
```
Advertisement (31B)     Scan Response (31B)
┌─────────────────┐    ┌──────────────────┐
│ Service Data    │    │ Device Name      │
│ 0xFE10: 카드정보│    │ GATT Service UUID│
│ TX Power        │    │ 0xFFF0           │
└─────────────────┘    └──────────────────┘
```

### GATT Service (0xFFF0)
```
Characteristic 0xFFF1 (WRITE)
  ↓ Scanner → Store
  order_id=XXX&param=value

Characteristic 0xFFF2 (READ)
  ↓ Store → Scanner
  {"status": "success", "message": "..."}
```

### Order Data 형식
```
✅ order_id=ABC123
✅ order_id=ORDER-001&phone=1234&amount=15000
❌ ABC123 (order_id= 없음)
❌ order_id:ABC123 (= 대신 : 사용)
```

---

## 🛠️ 자주 사용하는 명령어

### Git 기본
```bash
git status                    # 상태 확인
git add .                     # 모든 변경사항 추가
git commit -m "메시지"        # 커밋
git push                      # 푸시
git pull                      # 최신 코드 가져오기
```

### 빌드
```bash
# Windows
gradlew.bat clean assembleDebug
gradlew.bat assembleRelease

# Linux/Mac
./gradlew clean assembleDebug
./gradlew assembleRelease
```

### APK 위치
```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

---

## ⚠️ 빠른 트러블슈팅

### Q: nRF Connect에서 연결 안 됨
**A**: 3가지 체크
1. CardFragment 활성화 확인 (카드 탭)
2. Logcat: "Advertising started successfully" 확인
3. 파형 애니메이션 표시 확인

### Q: GATT Service (0xFFF0) 안 보임
**A**: 2가지 확인
1. 앱 재빌드 및 재설치
2. nRF Connect 앱 재시작

### Q: Write 시 파싱 오류
**A**: 형식 확인
```
올바름: order_id=ABC123
잘못됨: ABC123
```

### Q: 잘못된 디바이스에 연결됨
**A**: 디바이스 확인
- Service Data 0xFE10 있는지
- Complete Local Name: "mcandle1"
- Service UUIDs: 0xFFF0 있는지

---

## 📱 nRF Connect 사용법 (이미지로 기억)

### 1단계: Scan
```
[SCAN] 버튼 클릭
↓
"mcandle1" 디바이스 찾기
↓
▼ 버튼 눌러 확장
```

### 2단계: 확인
```
Service Data: 0xFE10 ✓
Complete Local Name: mcandle1 ✓
Service UUIDs: 0xFFF0 ✓
```

### 3단계: Connect
```
[CONNECT] 버튼 클릭
↓
"DISCOVERING SERVICES..." 대기
↓
Unknown Service (0xFFF0) 표시 확인
```

### 4단계: Write
```
0xFFF0 Service 확장
↓
0xFFF1 Characteristic 선택
↓
↑ (업로드) 버튼 클릭
↓
Text 입력: order_id=TEST123
↓
[SEND] 클릭
```

### 5단계: 결과
```
앱에서 다이얼로그 표시
"주문번호: TEST123"
```

---

## 🔍 Logcat 필터

### Android Studio Logcat 필터 설정
```
Tag: CardFragment|GattServerManager|OrderDataParser
Level: Debug
```

### 주요 로그 메시지
```
✅ "GATT Server started successfully"
✅ "Advertising started successfully"
✅ "Client connected: XX:XX:XX"
✅ "Order parsed - ID: XXX"
❌ "Failed to parse data"
❌ "GATT Server 시작 실패"
```

---

## 📊 성능 참고

### Advertisement 설정
- Mode: LOW_LATENCY (~100ms 간격)
- TX Power: HIGH (최대 전송 거리)
- Connectable: true

### 타임아웃
- 광고 타임아웃: 60초 (CardFragment.kt:312)
- GATT 연결 타임아웃: 시스템 기본값

### 배터리 영향
- Advertising Only: 낮음
- Advertising + GATT: 중간

---

## 🎯 다음 단계 아이디어

### 단기 (1-2주)
- [ ] 주문 데이터에 상품 정보 추가
- [ ] Read Characteristic으로 응답 전송
- [ ] 연결 상태 UI 표시

### 중기 (1개월)
- [ ] BLE 5.0 Extended Advertising (255 bytes)
- [ ] 여러 Scanner 동시 연결 지원
- [ ] Supabase 연동

### 장기 (3개월)
- [ ] 백그라운드 광고 지원
- [ ] 암호화 통신
- [ ] 로그 분석 대시보드

---

## 📚 참고 문서

### 프로젝트 문서
- `DEVELOPMENT_LOG_2025-11-11.md` - 상세 개발 로그
- `QUICK_REFERENCE.md` - 이 문서
- `TROUBLESHOOTING.md` - 문제 해결 가이드 (작성 예정)
- `NRF_CONNECT_GUIDE.md` - nRF Connect 가이드 (작성 예정)

### 외부 문서
- [Android BLE API](https://developer.android.com/guide/topics/connectivity/bluetooth/ble-overview)
- [nRF Connect 앱](https://www.nordicsemi.com/Products/Development-tools/nRF-Connect-for-mobile)
- [Bluetooth Core Spec](https://www.bluetooth.com/specifications/specs/)

---

## 💡 Pro Tips

### Tip 1: 빠른 디버깅
```kotlin
// OrderDataParser.kt에 로그 추가
Log.d("OrderDataParser", "Parsing data: $dataString")
```

### Tip 2: nRF Connect 캐시 제거
```
1. Disconnect
2. 디바이스 길게 누르기 → "Remove bond"
3. nRF Connect 앱 완전 종료
4. 재시작
```

### Tip 3: MAC 주소 빠른 확인
```
Logcat: "Client connected: XX:XX:XX"
nRF Connect: 디바이스 MAC 주소 비교
```

### Tip 4: 빌드 속도 개선
```bash
# Incremental build (빠름)
gradlew.bat assembleDebug

# Clean build (느리지만 확실함)
gradlew.bat clean assembleDebug
```

---

## 🔐 보안 고려사항

### 현재 구현
- ⚠️ 평문 통신 (암호화 없음)
- ⚠️ 인증 없음

### 향후 개선
- [ ] AES 암호화
- [ ] HMAC 서명
- [ ] Nonce 기반 재전송 공격 방지

---

## 📞 문제 발생 시

### 1차: 자체 해결
1. 이 문서의 트러블슈팅 섹션 확인
2. `DEVELOPMENT_LOG_2025-11-11.md` 참고
3. Logcat 로그 확인

### 2차: 팀 문의
- GitHub Issues 등록
- 상세 로그 첨부
- 재현 단계 기록

---

## 📌 즐겨찾기 (북마크용)

### 자주 여는 파일
```
AdvertiserManager.kt:45         # setConnectable
AdvertisePacketBuilder.kt:44    # Scan Response
CardFragment.kt:190             # 주문번호 표시
OrderDataParser.kt:29           # 파싱 로직
GattServiceConfig.kt:17         # Service UUID
```

### 자주 가는 경로
```
Settings 화면: 우측 상단 톱니바퀴
CardFragment: 하단 네비게이션 "카드" 탭
Logcat: Android Studio 하단 탭
nRF Connect: Scanner 탭 기본 화면
```

---

**이 문서는 빠른 참조용입니다. 자세한 내용은 `DEVELOPMENT_LOG_2025-11-11.md`를 참고하세요.**

**마지막 업데이트**: 2025-11-11 21:30 KST