# mCandle BLE 개발 치트시트

**빠른 복사/붙여넣기용 명령어 모음**

---

## 🔨 빌드 & 실행

### Debug APK 빌드
```bash
# Windows
gradlew.bat assembleDebug

# Linux/Mac
./gradlew assembleDebug
```

### Release APK 빌드
```bash
# Windows
gradlew.bat assembleRelease

# Linux/Mac
./gradlew assembleRelease
```

### Clean Build
```bash
# Windows
gradlew.bat clean assembleDebug

# Linux/Mac
./gradlew clean assembleDebug
```

### APK 설치
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### APK 위치
```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

---

## 📱 ADB 명령어

### 디바이스 확인
```bash
adb devices
```

### 앱 실행
```bash
adb shell am start -n com.mcandle.bleapp.v2/.MainActivity
```

### 앱 종료
```bash
adb shell am force-stop com.mcandle.bleapp.v2
```

### 앱 삭제
```bash
adb uninstall com.mcandle.bleapp.v2
```

### Logcat 실시간
```bash
adb logcat | grep -E "CardFragment|GattServerManager|OrderDataParser"
```

### Logcat 필터 (Windows)
```cmd
adb logcat | findstr "CardFragment GattServerManager OrderDataParser"
```

### Logcat 저장
```bash
adb logcat -d > logcat.txt
```

---

## 📂 Git 명령어

### 기본 워크플로우
```bash
git status                          # 상태 확인
git add .                           # 모든 변경사항 추가
git commit -m "커밋 메시지"         # 커밋
git push                            # 원격 저장소에 푸시
```

### 브랜치 관리
```bash
git branch                          # 로컬 브랜치 목록
git branch -a                       # 모든 브랜치 목록
git checkout main                   # main 브랜치로 이동
git checkout -b feature/new-branch  # 새 브랜치 생성 및 이동
```

### 원격 동기화
```bash
git pull                            # 원격 변경사항 가져오기
git fetch origin                    # 원격 정보만 가져오기
git merge origin/main               # 원격 main 브랜치 병합
```

### 변경사항 취소
```bash
git restore <file>                  # 파일 변경 취소
git restore --staged <file>         # Staged 상태 취소
git reset --hard HEAD               # 모든 변경사항 취소
```

### 로그 확인
```bash
git log --oneline -10               # 최근 10개 커밋
git log --stat                      # 변경된 파일 포함
git show <commit-id>                # 특정 커밋 상세
```

### Stash (임시 저장)
```bash
git stash                           # 변경사항 임시 저장
git stash list                      # Stash 목록
git stash pop                       # 가장 최근 stash 적용
git stash drop                      # 가장 최근 stash 삭제
```

---

## 🧪 테스트 명령어

### Unit Test
```bash
gradlew.bat test
```

### Android Instrumented Test
```bash
gradlew.bat connectedAndroidTest
```

### 특정 테스트 실행
```bash
gradlew.bat test --tests "com.mcandle.bleapp.OrderDataParserTest"
```

---

## 🔍 디버깅 명령어

### Bluetooth 상태 확인
```bash
adb shell dumpsys bluetooth_manager
```

### 앱 프로세스 정보
```bash
adb shell ps | grep mcandle
```

### 앱 메모리 사용량
```bash
adb shell dumpsys meminfo com.mcandle.bleapp.v2
```

### 스크린샷 저장
```bash
adb exec-out screencap -p > screenshot.png
```

### 화면 녹화
```bash
adb shell screenrecord /sdcard/recording.mp4
# 종료: Ctrl+C
# 다운로드
adb pull /sdcard/recording.mp4
```

---

## 📦 nRF Connect Write 데이터 예시

### 기본 주문
```
order_id=ORDER-12345
```

### 주문 + 전화번호
```
order_id=ORDER-12345&phone=1234
```

### 주문 + 전화번호 + 금액
```
order_id=ORDER-12345&phone=1234&amount=25000
```

### 주문 + 여러 파라미터
```
order_id=ORDER-12345&phone=1234&amount=25000&store=잠실점&table=A3
```

### JSON 형식 (미지원)
```
❌ {"order_id": "ORDER-12345"}
```

---

## 🔑 핵심 UUID 참고

### GATT Service
```
0000FFF0-0000-1000-8000-00805F9B34FB
```

### Write Characteristic
```
0000FFF1-0000-1000-8000-00805F9B34FB
```

### Read Characteristic
```
0000FFF2-0000-1000-8000-00805F9B34FB
```

### Service Data UUID
```
0000FE10-0000-1000-8000-00805F9B34FB
```

---

## 📝 자주 사용하는 파일 경로

### 코드 파일
```
app/src/main/java/com/mcandle/bleapp/
├── advertise/AdvertiserManager.kt
├── advertise/AdvertisePacketBuilder.kt
├── fragment/CardFragment.kt
├── gatt/GattServerManager.kt
├── gatt/GattServiceConfig.kt
└── gatt/OrderDataParser.kt
```

### 레이아웃 파일
```
app/src/main/res/layout/
├── fragment_card.xml
├── payment_detail_dialog.xml
└── payment_notification_dialog.xml
```

### 설정 파일
```
app/build.gradle.kts
gradle/libs.versions.toml
```

---

## 🎨 Android Studio 단축키

### Windows/Linux
```
Ctrl + Alt + L          # 코드 포맷팅
Ctrl + D                # 라인 복제
Ctrl + Y                # 라인 삭제
Ctrl + /                # 주석 토글
Ctrl + Shift + F        # 전체 검색
Ctrl + N                # 클래스 검색
Ctrl + Shift + N        # 파일 검색
Alt + Enter             # Quick Fix
Ctrl + B                # 정의로 이동
```

### Mac
```
Cmd + Option + L        # 코드 포맷팅
Cmd + D                 # 라인 복제
Cmd + Delete            # 라인 삭제
Cmd + /                 # 주석 토글
Cmd + Shift + F         # 전체 검색
Cmd + O                 # 클래스 검색
Cmd + Shift + O         # 파일 검색
Option + Enter          # Quick Fix
Cmd + B                 # 정의로 이동
```

---

## 🐛 빠른 문제 해결

### 빌드 오류 시
```bash
gradlew.bat clean
# Android Studio: File → Invalidate Caches → Invalidate and Restart
```

### Gradle Sync 문제
```bash
# .gradle 폴더 삭제
rm -rf ~/.gradle/caches/
# Windows
rmdir /s /q %USERPROFILE%\.gradle\caches
```

### ADB 연결 끊김
```bash
adb kill-server
adb start-server
```

### Bluetooth 권한 문제
```bash
adb shell pm grant com.mcandle.bleapp.v2 android.permission.BLUETOOTH_ADVERTISE
adb shell pm grant com.mcandle.bleapp.v2 android.permission.BLUETOOTH_SCAN
adb shell pm grant com.mcandle.bleapp.v2 android.permission.BLUETOOTH_CONNECT
```

---

## 📊 Logcat 필터 태그

### Android Studio Logcat 필터
```
Tag: CardFragment|GattServerManager|OrderDataParser|AdvertiserManager
Level: Debug
Package: com.mcandle.bleapp.v2
```

### 명령줄 Logcat 필터
```bash
adb logcat -s CardFragment:D GattServerManager:D OrderDataParser:D
```

---

## 🔧 Settings 관련

### SharedPreferences 확인
```bash
adb shell run-as com.mcandle.bleapp.v2 cat /data/data/com.mcandle.bleapp.v2/shared_prefs/ble_settings.xml
```

### SharedPreferences 초기화
```bash
adb shell pm clear com.mcandle.bleapp.v2
```

---

## 📱 디바이스 정보

### Bluetooth 정보
```bash
adb shell dumpsys bluetooth_manager | grep "Bluetooth Status"
```

### Android 버전
```bash
adb shell getprop ro.build.version.release
```

### 디바이스 모델
```bash
adb shell getprop ro.product.model
```

### 배터리 상태
```bash
adb shell dumpsys battery
```

---

## 🎯 자주 사용하는 Gradle 태스크

### 의존성 확인
```bash
gradlew.bat dependencies
```

### 프로젝트 정보
```bash
gradlew.bat projects
```

### 캐시 정리
```bash
gradlew.bat --stop
```

### 빌드 시간 측정
```bash
gradlew.bat assembleDebug --profile
```

---

## 💾 백업 & 복원

### APK 백업
```bash
adb pull /data/app/com.mcandle.bleapp.v2-1/base.apk mcandle-backup.apk
```

### 앱 데이터 백업
```bash
adb backup -f backup.ab com.mcandle.bleapp.v2
```

### 앱 데이터 복원
```bash
adb restore backup.ab
```

---

## 🔐 키스토어 관리

### Debug 키스토어 위치
```
~/.android/debug.keystore
```

### 키스토어 정보 확인
```bash
keytool -list -v -keystore ~/.android/debug.keystore -storepass android
```

---

## 📦 Release 빌드

### Release APK 서명
```bash
gradlew.bat assembleRelease
# APK 위치: app/build/outputs/apk/release/app-release-unsigned.apk
```

### APK 서명 확인
```bash
keytool -printcert -jarfile app-release.apk
```

---

## 🌐 유용한 URL

### 프로젝트
```
GitHub: https://github.com/mcandle-dev/ble-advertiser
```

### 문서
```
Android BLE: https://developer.android.com/guide/topics/connectivity/bluetooth
Bluetooth Spec: https://www.bluetooth.com/specifications/specs/
```

### 도구
```
nRF Connect: https://www.nordicsemi.com/Products/Development-tools/nRF-Connect-for-mobile
```

---

## 📝 템플릿

### Commit 메시지
```
Add feature: [기능 설명]

- 변경사항 1
- 변경사항 2

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```

### Bug Report
```
## 문제 설명
[간단한 설명]

## 재현 단계
1.
2.
3.

## 예상 동작


## 실제 동작


## 환경
- Android 버전:
- 디바이스:
- 앱 버전:

## Logcat
```
[로그 붙여넣기]
```
```

---

**이 치트시트를 즐겨찾기에 추가하세요!**

**마지막 업데이트**: 2025-11-11 21:30 KST