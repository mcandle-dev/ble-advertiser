# Android APK 빌드 가이드

BLE Advertiser & Scanner App의 APK를 생성하는 다양한 방법을 설명합니다.

## 📋 목차

1. [방법 1: GitHub Actions 자동 빌드 (추천)](#방법-1-github-actions-자동-빌드-추천)
2. [방법 2: GitHub Releases 수동 배포](#방법-2-github-releases-수동-배포)
3. [방법 3: 로컬 빌드 (개발용)](#방법-3-로컬-빌드-개발용)
4. [방법 4: 프로젝트 소스에 APK 포함](#방법-4-프로젝트-소스에-apk-포함)

---

## 방법 1: GitHub Actions 자동 빌드 (추천)

### 🎯 특징
- **완전 자동화**: 코드 푸시 시 자동 APK 생성
- **무료**: GitHub에서 제공하는 무료 서비스
- **멀티 빌드**: Debug + Release APK 동시 생성
- **캐싱**: Gradle 캐싱으로 빌드 시간 단축

### 📂 필요 파일들

#### **.github/workflows/build.yml**
```yaml
name: Build Android APK

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - name: Checkout code
      uses: actions/checkout@v4
      
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        
    - name: Cache Gradle packages
      uses: actions/cache@v3
      with:
        path: |
          ~/.gradle/caches
          ~/.gradle/wrapper
        key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
        restore-keys: |
          ${{ runner.os }}-gradle-
          
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
      
    - name: Build Debug APK
      run: ./gradlew assembleDebug
      
    - name: Build Release APK
      run: ./gradlew assembleRelease
      
    - name: Upload Debug APK
      uses: actions/upload-artifact@v4
      with:
        name: app-debug-apk
        path: app/build/outputs/apk/debug/app-debug.apk
        retention-days: 30
        
    - name: Upload Release APK
      uses: actions/upload-artifact@v4
      with:
        name: app-release-apk
        path: app/build/outputs/apk/release/app-release-unsigned.apk
        retention-days: 30
```

### 🚀 사용 방법

#### **1단계: 워크플로우 파일 생성**
```bash
# 디렉토리 생성
mkdir -p .github/workflows

# 위 내용을 build.yml 파일로 저장
# (이미 생성됨)
```

#### **2단계: Git에 추가 및 푸시**
```bash
git add .github/workflows/build.yml
git commit -m "Add GitHub Actions auto build workflow"
git push origin main
```

#### **3단계: GitHub에서 확인**
```
1. https://github.com/mcandle-dev/ble-advertiser 접속
2. "Actions" 탭 클릭
3. "Build Android APK" 워크플로우 실행 확인
4. 3-5분 후 완료 시 APK 다운로드 가능
```

### 📦 APK 다운로드 방법
```
GitHub → Actions 탭 → 완료된 빌드 클릭 → "Artifacts" 섹션
├── app-debug-apk.zip (디버그 버전)
└── app-release-apk.zip (릴리즈 버전)
```

### ⏱️ 실행 시점
- **main 브랜치**에 push
- **develop 브랜치**에 push
- **Pull Request** 생성 시

---

## 방법 2: GitHub Releases 수동 배포

### 🎯 특징
- **공식 릴리즈**: 사용자가 쉽게 다운로드
- **태그 기반**: 버전 관리와 연동
- **자동 릴리즈 노트**: 변경사항 자동 생성
- **다운로드 통계**: 얼마나 다운로드되었는지 확인

### 📂 필요 파일

#### **.github/workflows/release.yml**
```yaml
name: Release Signed APK

on:
  push:
    tags:
      - 'v*'  # v1.0.0, v2.1.0 등의 태그에서 실행

jobs:
  release:
    runs-on: ubuntu-latest
    
    steps:
    - name: Checkout code
      uses: actions/checkout@v4
      
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        
    - name: Cache Gradle packages
      uses: actions/cache@v3
      with:
        path: |
          ~/.gradle/caches
          ~/.gradle/wrapper
        key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
        
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
      
    - name: Build Release APK
      run: ./gradlew assembleRelease
      
    - name: Get version from tag
      id: version
      run: echo "VERSION=${GITHUB_REF#refs/tags/}" >> $GITHUB_OUTPUT
      
    - name: Create GitHub Release
      uses: softprops/action-gh-release@v1
      with:
        tag_name: ${{ steps.version.outputs.VERSION }}
        name: BLE Advertiser ${{ steps.version.outputs.VERSION }}
        body: |
          ## 🚀 BLE Advertiser & Scanner App ${{ steps.version.outputs.VERSION }}
          
          ### 📱 주요 기능
          - 🔵 BLE Advertise (카드번호, 전화번호 광고)
          - 🔍 BLE Scanner (iBeacon 매칭)
          - 💳 2단계 결제 시스템
          - ⚙️ 설정 관리 (스캔 필터 포함)
          
          ### 📦 다운로드
          - **app-release-unsigned.apk**: 릴리즈 버전 (권장)
          - **app-debug.apk**: 디버그 버전 (개발자용)
          
          ### 🔧 설치 방법
          1. APK 파일 다운로드
          2. Android 기기에서 "알 수 없는 소스" 허용
          3. APK 설치 실행
          
        files: |
          app/build/outputs/apk/release/app-release-unsigned.apk
          app/build/outputs/apk/debug/app-debug.apk
        draft: false
        prerelease: false
      env:
        GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### 🏷️ 사용 방법

#### **1단계: 릴리즈 워크플로우 설정**
```bash
git add .github/workflows/release.yml
git commit -m "Add automatic release workflow"
git push origin main
```

#### **2단계: 버전 태그 생성**
```bash
# v1.0.0 릴리즈 생성
git tag -a v1.0.0 -m "첫 번째 공식 릴리즈

✨ 주요 기능:
- BLE Advertise & Scanner
- 2단계 결제 시스템  
- 설정 관리 (스캔 필터)
- UI/UX 개선"

# GitHub에 태그 푸시
git push origin v1.0.0
```

#### **3단계: 자동 릴리즈 확인**
```
1. GitHub → "Releases" 탭 확인
2. "v1.0.0" 릴리즈 자동 생성 확인
3. APK 파일 자동 첨부 확인
4. 릴리즈 노트 자동 생성 확인
```

### 📱 사용자 다운로드 방법
```
GitHub → Releases 탭 → 최신 릴리즈 → APK 파일 다운로드
```

---

## 방법 3: 로컬 빌드 (개발용)

### 🎯 특징
- **즉시 빌드**: 로컬에서 바로 APK 생성
- **개발 중 테스트**: 빠른 반복 개발
- **디버깅**: 상세한 빌드 로그 확인

### 💻 요구사항
- **Android Studio** 설치
- **JDK 17** 이상
- **Android SDK** 설치

### 🔧 빌드 명령어

#### **Android Studio GUI 방법**
```
1. Android Studio에서 프로젝트 열기
2. Build → Build Bundle(s) / APK(s) → Build APK(s)
3. 빌드 완료 후 "locate" 클릭하여 APK 위치 확인
```

#### **커맨드라인 방법**
```bash
# 프로젝트 루트 디렉토리에서 실행

# Debug APK 빌드
./gradlew assembleDebug

# Release APK 빌드  
./gradlew assembleRelease

# 모든 variant 빌드
./gradlew assemble

# 빌드 + 테스트
./gradlew build
```

#### **Windows에서 실행**
```cmd
# gradlew.bat 사용
gradlew.bat assembleDebug
gradlew.bat assembleRelease
```

### 📂 APK 출력 위치
```
app/build/outputs/apk/
├── debug/
│   └── app-debug.apk
└── release/
    └── app-release-unsigned.apk
```

### 🧹 빌드 캐시 정리
```bash
# Gradle 캐시 정리
./gradlew clean

# 완전 정리 (build 폴더 삭제)
./gradlew clean
rm -rf app/build/
```

---

## 방법 4: 프로젝트 소스에 APK 포함

### ⚠️ 주의사항
**일반적으로 권장하지 않는 방법**입니다. 하지만 특별한 목적이 있을 때 사용할 수 있습니다.

### 🎯 사용 시나리오
- **데모/프레젠테이션**: 바로 실행 가능한 APK 필요
- **오프라인 배포**: 인터넷 없이 APK 배포
- **백업 목적**: 특정 버전 APK 보관

### 📝 .gitignore 수정

#### **기본 설정 (APK 제외)**
```gitignore
# Android
*.iml
.gradle/
/local.properties
.idea/
.DS_Store
/build/           # 빌드 결과 제외
/captures
.externalNativeBuild
.cxx
/app/build/       # APK 제외
```

#### **APK 포함 설정**
```gitignore
# Android  
*.iml
.gradle/
/local.properties
.idea/
.DS_Store
/build/
/captures
.externalNativeBuild
.cxx

# APK 파일만 포함 (나머지 빌드 결과는 제외)
/app/build/*
!/app/build/outputs/
/app/build/outputs/*
!/app/build/outputs/apk/
/app/build/outputs/apk/*
!/app/build/outputs/apk/**/*.apk
```

### 🚀 APK 포함 방법
```bash
# APK 빌드
./gradlew assembleRelease

# Git에 APK 추가
git add app/build/outputs/apk/release/app-release-unsigned.apk
git commit -m "Add release APK v1.0.0"
git push origin main
```

### 📦 사용자 다운로드 방법
```
GitHub → Code → Browse files → app/build/outputs/apk/release/ → APK 다운로드
```

---

## 🔍 빌드 문제 해결

### 🚨 일반적인 오류

#### **Gradle 권한 오류**
```bash
# 해결: gradlew 실행 권한 추가
chmod +x gradlew
git update-index --chmod=+x gradlew
```

#### **JDK 버전 오류**
```bash
# 현재 Java 버전 확인
java -version

# JDK 17 설치 필요
# Android Studio → File → Project Structure → SDK Location → JDK Location
```

#### **메모리 부족 오류**
```bash
# gradle.properties에 추가
echo "org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8" >> gradle.properties
```

#### **의존성 오류**
```bash
# Gradle 캐시 정리
./gradlew clean
./gradlew --refresh-dependencies
```

### 📊 빌드 시간 최적화

#### **Gradle 설정 최적화**
```properties
# gradle.properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.daemon=true
android.useAndroidX=true
android.enableJetifier=true
```

#### **빌드 시간 확인**
```bash
# 빌드 시간 상세 분석
./gradlew assembleDebug --profile
```

---

## 📋 방법별 비교표

| 방법 | 장점 | 단점 | 권장 용도 |
|------|------|------|----------|
| **GitHub Actions** | 자동화, 무료, 멀티환경 | 초기 설정 필요 | 일반 개발 |
| **GitHub Releases** | 공식 배포, 다운로드 통계 | 수동 태그 생성 | 릴리즈 배포 |
| **로컬 빌드** | 빠름, 디버깅 용이 | 환경 의존적 | 개발/테스트 |
| **소스 포함** | 간단, 즉시 접근 | 저장소 크기 증가 | 데모/백업 |

---

## 🎯 권장 워크플로우

### 📱 일반 개발
```
1. 로컬에서 개발 및 테스트
2. GitHub에 코드 푸시
3. GitHub Actions 자동 빌드 확인
4. Artifacts에서 APK 다운로드하여 테스트
```

### 🚀 릴리즈 배포
```
1. 기능 완성 및 테스트 완료
2. 버전 태그 생성 (git tag v1.x.x)
3. GitHub Releases 자동 생성 확인
4. 사용자에게 릴리즈 공지
```

---

**📅 최종 업데이트**: 2025년 8월  
**🔧 권장 방법**: GitHub Actions (방법 1) + GitHub Releases (방법 2)

> 💡 **팁**: 대부분의 경우 방법 1과 방법 2를 조합하여 사용하는 것이 가장 효율적입니다.