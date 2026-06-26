# Logging Standard & Privacy Protection

## Purpose
Specifies logging filters, levels (DEBUG, INFO, ERROR), and privacy filters.

## Guidelines
- **NEVER** write plain-text private keys, recovery seed phrases, or active GPS coordinates to standard system console outputs (`Log.d`).
- Always use tag wrappers indicating the subsystem name (e.g., `[NEXUS-BLE]`).

## Revision History
- **v1.0.0 (2026-06-26)**: Initial release.