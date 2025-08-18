# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

### Development
```bash
# Debug APK build
./gradlew assembleDebug

# Release APK build
./gradlew assembleRelease

# Build all variants
./gradlew assemble

# Run unit tests
./gradlew test

# Run Android instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean
```

### Windows Commands
```cmd
# Use gradlew.bat on Windows
gradlew.bat assembleDebug
gradlew.bat assembleRelease
```

## Project Architecture

This is an Android BLE (Bluetooth Low Energy) advertiser and scanner application built in Kotlin. The app enables stores to advertise customer card numbers and phone numbers via BLE and scan for nearby iBeacon signals to match payment requests.

### Core Architecture Pattern
- **MVVM Architecture**: Uses ViewModel pattern with LiveData for UI state management
- **Repository Pattern**: Managers handle specific functionality (BLE operations, settings)
- **Fragment-based UI**: Modular UI components with ViewBinding

### Key Components

#### BLE Operations (`advertise/` and `scan/`)
- `AdvertiserManager.kt`: Manages BLE advertisement broadcasting
- `AdvertisePacketBuilder.kt`: Constructs BLE advertisement packets with card/phone data
- `BleScannerManager.kt`: Handles BLE scanning with filtering capabilities
- `IBeaconParser.kt`: Parses received iBeacon data from scan results

#### Data Models (`model/`)
- `AdvertiseDataModel.kt`: Holds card number and phone number data
- `AdvertiseMode.kt`: Enum for advertisement modes (MINIMAL/DATA)
- `EncodingType.kt`: Encoding options (ASCII/BCD)

#### Settings & Utilities (`util/`)
- `SettingsManager.kt`: SharedPreferences wrapper for app configuration
- `ByteUtils.kt`: Byte array manipulation utilities for BLE packets

#### UI Layer (`ui/` and Activities)
- `MainActivity.kt`: Main BLE control interface with dual advertise/scan functionality
- `SettingsActivity.kt`: Configuration screen for device name, encoding, scan filters
- `InputFormFragment.kt`: Input form for card numbers and phone numbers

#### Settings Architecture (`SettingsActivity`)
The settings screen provides centralized configuration management:

**Configuration Categories:**
- **디바이스 설정**: Device name for BLE advertisement
- **전송 방식**: ASCII (human-readable) vs BCD (space-efficient) encoding
- **광고 모드**: MINIMAL (basic) vs DATA (detailed) advertisement modes  
- **스캔 필터**: ALL/RFSTAR_ONLY/IBEACON_RFSTAR filtering options

**UI Design Pattern:**
- Consistent button styling (56dp height, rounded corners)
- Category-based sections with clear visual separation
- Real-time setting updates via `SettingsManager`
- Top bar settings icon access from MainActivity

**Integration with Main App:**
- Settings persist across app restarts via SharedPreferences
- MainActivity reads settings on startup to configure BLE operations
- Dynamic packet building based on selected encoding/mode
- Scan filter selection affects `BleScannerManager` behavior

### BLE Architecture Details

#### Advertisement Flow
1. User inputs 16-digit card number and 4-digit phone number
2. `AdvertisePacketBuilder` creates BLE packet using selected encoding (ASCII/BCD)
3. `AdvertiserManager` broadcasts packet with device name and service data
4. Simultaneous scanning starts to detect matching iBeacon signals

#### Scanning & Matching Flow
1. `BleScannerManager` scans with configurable filters (ALL/RFSTAR_ONLY/IBEACON_RFSTAR)
2. `IBeaconParser` extracts UUID data containing order number and phone digits
3. Phone number matching triggers 2-stage payment dialog system
4. Store information and product details are displayed for confirmation

#### Scan Filtering & iBeacon Matching Logic

**Scan Filter Modes (`BleScannerManager.ScanMode`):**
```kotlin
enum class ScanMode {
    ALL,            // All BLE devices (no filtering)
    RFSTAR_ONLY,    // Only RFStar manufacturer (0x5246) devices
    IBEACON_RFSTAR  // Only RFStar iBeacon frames (0x02, 0x15 prefix)
}
```

**iBeacon Frame Structure:**
- Company ID: RFStar manufacturer identifier (0x5246)
- UUID: Contains embedded order number and phone number data
- Major/Minor: Additional beacon identification
- TX Power: Signal strength calibration

**Phone Number Matching Algorithm:**
```kotlin
// IBeaconParser matching logic
fun parseAndMatch(scanResult: ScanResult, expectedPhone4: String): IBeaconFrame? {
    val manufacturerData = scanResult.scanRecord?.getManufacturerSpecificData(0x5246)
    val frame = parseIBeaconFrame(manufacturerData)
    
    // Extract phone digits from UUID
    val phoneDigits = extractPhoneFromUUID(frame.uuid)
    
    // Match last 4 digits
    return if (phoneDigits.takeLast(4) == expectedPhone4) frame else null
}
```

**Filtering Implementation:**
- **RFSTAR_ONLY**: Filters by manufacturer data presence (0x5246)
- **IBEACON_RFSTAR**: Additional validation of iBeacon frame format (0x02, 0x15)
- **Performance**: Early filtering reduces processing overhead for irrelevant devices

#### 2-Stage Payment Dialog System
The app implements a sophisticated payment confirmation flow:

**Stage 1: Payment Notification Dialog (`payment_notification_dialog.xml`)**
- Simple arrival notification: "결제 요청이 도착했습니다"
- Single "확인하기" button to proceed
- iOS-style rounded design with clean typography

**Stage 2: Payment Detail Dialog (`payment_detail_dialog.xml`)**
- Comprehensive order information display
- Store details: "엠캔들 잠실점", staff name
- Product listing: items, quantities, prices
- Payment summary with discounts (e.g., "롯데카드 10% 할인")
- Final confirmation buttons

**Implementation Pattern:**
```kotlin
// MainActivity matching logic
private fun showPaymentNotificationDialog(frame: IBeaconFrame) {
    // Stage 1: Notification dialog
    val notificationDialog = AlertDialog.Builder(this)
        .setView(R.layout.payment_notification_dialog)
        .create()
    
    // On "확인하기" click -> Stage 2
    confirmButton.setOnClickListener {
        notificationDialog.dismiss()
        showPaymentDetailDialog(frame)
    }
}
```

### Data Encoding Strategy
- **ASCII Mode**: Human-readable but larger packet size (16+4 bytes)
- **BCD Mode**: Binary-coded decimal for space efficiency (8+2 bytes)
- **Service Data (0x16)**: Card + phone number payload
- **Complete Local Name (0x09)**: Device identifier

### Permission Requirements
The app requires multiple BLE permissions with Android 12+ compatibility:
- `BLUETOOTH_ADVERTISE`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`
- Legacy permissions: `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION`

### Settings Architecture
`SettingsManager` centralizes configuration storage:
- Device name for BLE advertisement
- Encoding type (ASCII/BCD) preference
- Advertisement mode selection
- Scan filter mode (ALL/RFSTAR_ONLY/IBEACON_RFSTAR)

### Payment/Order Confirmation Workflow

**Complete User Journey:**
1. **Setup Phase**: User configures device settings in SettingsActivity
2. **Input Phase**: User enters 16-digit card number + 4-digit phone number
3. **Broadcast Phase**: App simultaneously:
   - Advertises user data via BLE packets
   - Scans for matching iBeacon signals from stores
4. **Detection Phase**: When matching iBeacon detected (phone number match):
   - Stage 1: Payment notification dialog appears
   - User clicks "확인하기" to proceed
5. **Confirmation Phase**: Detailed payment dialog shows:
   - Store information (엠캔들 잠실점, 직원명)
   - Product list with quantities and prices
   - Payment summary with discounts
   - Final confirmation buttons

**Technical Implementation:**
```kotlin
// MainActivity workflow
private fun onBeaconMatched(frame: IBeaconFrame) {
    // Stop scanning to prevent multiple matches
    scannerManager.stopScan()
    
    // Show notification dialog first
    showPaymentNotificationDialog(frame) { confirmed ->
        if (confirmed) {
            // Proceed to detailed payment info
            showPaymentDetailDialog(frame)
        }
    }
}
```

**Use Case Scenarios:**
- **Cafe Pickup**: Customer orders online, enters phone number, app detects when near pickup counter
- **Store Payment**: In-store purchase matching via phone number for contactless payment
- **Drive-through**: Vehicle occupant detection and order matching via BLE proximity

## Development Notes

### Testing Strategy
- Unit tests: `app/src/test/` for business logic
- Instrumented tests: `app/src/androidTest/` for BLE hardware interactions
- Physical device required for BLE functionality (emulator limitations)

### Build Configuration
- Target SDK 34, Min SDK 26
- Kotlin with ViewBinding enabled
- Uses version catalog (`gradle/libs.versions.toml`) for dependency management
- JDK 17 required for compilation

### Debugging Tools
- Raw packet HEX viewer in MainActivity
- Scan result logging in ScanListActivity
- Comprehensive logging throughout BLE operations

## Common Development Tasks

### Adding New BLE Features
1. Extend data models in `model/` package
2. Update `AdvertisePacketBuilder` for packet structure changes
3. Modify `IBeaconParser` for new data extraction logic
4. Update UI in MainActivity and relevant fragments

### Modifying Scan Filters
1. Update `BleScannerManager.ScanMode` enum
2. Implement filtering logic in `BleScannerManager.startScan()`
3. Add UI controls in `SettingsActivity`
4. Update `SettingsManager` for persistence

### Extending Dialog System
1. Create new dialog layout XML in `res/layout/`
2. Add dialog logic in MainActivity with proper lifecycle management
3. Update `IBeaconFrame` data class if new fields needed
4. Test dialog flow with proper back button handling

### Adding New Store/Payment Data
1. Modify dialog layouts (`payment_detail_dialog.xml`) for new fields
2. Update hardcoded store data or implement dynamic loading
3. Consider Supabase integration for real-time store/product data
4. Test with various data scenarios (long store names, multiple products)

### Testing BLE Operations
- Use nRF Connect app for packet verification
- Test with actual BLE hardware (EFR32BG22 recommended)
- Verify permission handling across Android versions
- Test battery optimization impact on LOW_LATENCY mode
- Test dialog flow with matching/non-matching scenarios
- Verify scan filter effectiveness with various BLE devices

## Recent Development Status (2025-08-18)

### Current Implementation Status
The app is **fully functional** with all major features implemented and tested:

#### ✅ Completed Features
1. **BLE Advertisement & Scanning**: Dual functionality working simultaneously
2. **iBeacon Parsing**: Full parsing with manufacturer detection (RFStar, Apple, Nordic)
3. **MAC Address Deduplication**: Scan list shows unique devices with real-time RSSI updates
4. **Two-Stage Payment Dialog**: Notification dialog → Payment detail dialog flow
5. **Button State Management**: Proper transitions between "Advertise Start" → "광고 중 (Scan 모드)" → "광고중..." → back to "Advertise Start"
6. **Payment Completion Flow**: BroadcastReceiver communication between activities working correctly
7. **Phone Number Integration**: Removed phone input from ScanListActivity, uses MainActivity input
8. **Comprehensive Logging**: Detailed debugging capabilities throughout the app

#### ✅ Recently Fixed Issues
1. **Button State Stuck on "광고중..."**: Fixed by calling `stopAdvertiseAndScan()` instead of just `setScanning(false)` when matching succeeds
2. **BroadcastReceiver Android 13+ Compatibility**: Added `RECEIVER_NOT_EXPORTED` flag
3. **Activity Communication**: Proper Intent passing and result handling between MainActivity and ScanListActivity
4. **Scan Duplication**: Implemented deviceMap to prevent duplicate MAC addresses in scan results

#### 🔧 Key Technical Details
1. **Button State Logic** (`MainActivity.kt:265-291`):
   ```kotlin
   when {
       advertising && scanning -> "광고 중 (Scan 모드)"
       advertising -> "광고 중..."  
       else -> "Advertise Start"
   }
   ```

2. **Matching Success Flow** (`MainActivity.kt:115-121`):
   ```kotlin
   override fun onMatch(frame: IBeaconParser.IBeaconFrame) {
       Log.d("MainActivity", "매칭 성공 - stopAdvertiseAndScan() 호출")
       stopAdvertiseAndScan()  // Stops both advertising AND scanning
       showOrderDialog(frame)
   }
   ```

3. **Payment Completion** (`ScanListActivity.kt:262-263`):
   ```kotlin
   val broadcastIntent = Intent("com.mcandle.bleapp.PAYMENT_COMPLETED")
   sendBroadcast(broadcastIntent)
   ```

#### 🎯 App Behavior Summary
- **Start Process**: User clicks "Advertise Start" → Both advertising and scanning begin → Button shows sequential states
- **Matching Process**: When iBeacon with matching phone number detected → Both advertising and scanning stop → Payment dialogs appear
- **Payment Process**: Two-stage dialog system → Payment completion → BroadcastReceiver notifies MainActivity → Button returns to "Advertise Start"

#### 📱 Current Scan Filter Modes
- **ALL**: All BLE devices (development/debugging)
- **RFSTAR_ONLY**: Only RFStar manufacturer devices (0x5246)
- **IBEACON_RFSTAR**: Only RFStar iBeacon frames with proper format

#### 🐛 No Known Issues
All major functionality is working correctly. The app successfully:
- Handles button state transitions properly
- Manages simultaneous BLE operations
- Processes iBeacon matching accurately  
- Completes payment flow end-to-end
- Maintains proper activity lifecycle