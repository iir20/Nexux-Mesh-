# Android Runtime Permissions Enforcement Model

## Purpose
Documents the declarative permissions, runtime prompt models, and graceful performance degradation rules under strict Android OS boundaries.

## Scope
Covers Android 10, 11, 12, 13, and 14 runtime permission flows.

## Permission Manifest Declaration
- `ACCESS_FINE_LOCATION`: Required by Bluetooth LE scan APIs to estimate physical proximity.
- `BLUETOOTH_SCAN` / `BLUETOOTH_ADVERTISE` / `BLUETOOTH_CONNECT`: Required for core peer discovery.

## Revision History
- **v1.0.0 (2026-06-26)**: Android runtime permission profiles.