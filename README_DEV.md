# Developer Quick Start Guide

## Purpose
This document provides software engineers with technical onboarding procedures, environment configurations, and debugging protocols required to compile, modify, and test the NEXUS MESH Android client.

## Scope
Includes workspace setup, dependency management, compiler profiles, code style enforcement, emulator constraints, and remote logging integration.

## Background
NEXUS MESH relies on Gradle Kotlin DSL, Jetpack Compose, Kotlin Coroutines, and the Room database compiler. It integrates hardware-level low-energy transceivers requiring specific hardware capability checks.

## Architecture
```
[Gradle Compiler Profile] -> [KSP Parser] -> [Generated Room Schemas]
                                      -> [Jetpack Compose UI Layout]
```

## Implementation

### Prerequisites
- JDK 17 (Azul Zulu or Temurin recommended)
- Android SDK 34 (Android 14)
- Android Studio Jellyfish or later

### Local Configuration
Create an environment file `.env` in the project root:
```properties
NEXUS_DEV_LOGGING=true
NEXUS_SIMULATOR_BYPASS=false
```

## Examples

### Building Debug Build via Terminal
```bash
gradle assembleDebug
```

### Running Unit Tests
```bash
gradle :app:testDebugUnitTest
```

## Limitations
- Android Emulators do not support physical BLE advertising or Wi-Fi Direct grouping.
- Real hardware (minimum 2 devices) is required to test active transit sync loops.

## Security Considerations
Never hardcode private credentials inside code or commit `local.properties` to Git tracking.

## Future Work
- Dynamic mock BLE injection inside emulator builds via mock BluetoothSocket wrappers.

## References
- [Android Developer Guides](https://developer.android.com)
- [Kotlin Style Guidelines](https://kotlinlang.org/docs/coding-conventions.html)

## Revision History
- **v1.0.0 (2026-06-26)**: Initial release for v7.0 client integration.