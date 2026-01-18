# Changelog

Recent updates to the BLE Advertiser Android application.

## [Unreleased] - 2026-01-18

### Added
- **Multi-step Payment Flow**: Introduced a segmented UI for the payment process including:
  - **Payment Detail View**: Shows order summaries and product details (e.g., "Nike Alphafly 3").
  - **Payment Method Selection**: Optional view to choose between Simple Pay, Normal Pay, and Samsung Pay.
  - **Payment Completion Screen**: A dedicated success screen with visual confirmation (`ic_check_circle`).
- **New Icons/Assets**:
  - Added `ic_check_circle.xml` for success states.
  - Integrated `ic_settings_gear` for easier access to settings.

### Changed
- **BLE Stability (Android 15 Compliance)**: Fixed a race condition where advertising started before the GATT service was fully registered. The app now waits for the `onServiceAdded` callback before initiating BLE advertisement.
- **UI Redesign**: Updated `fragment_card.xml` to support dynamic state switching between Initial, Detail, and Selection layouts.
- **Permission Management**: Updated `MainActivity` and `AndroidManifest.xml` (via fragments) to handle `BLUETOOTH_SCAN` and other BLE permissions more robustly on Android 12+.
- **UX Improvements**: 
  - Renamed primary payment button to "카카오페이 결제하기".
  - Improved layout spacing and font sizes for better readability.

### Fixed
- Fixed BLE GATT Server initialization order to prevent connection failures on fast scanners.

### Removed
- **Unused Layouts**: Deleted `payment_notification_dialog.xml` in favor of the new in-fragment payment detail flow.
