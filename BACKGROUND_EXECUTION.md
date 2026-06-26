# Background Execution & Service Architecture

## Purpose
Specifies the foreground service model, background limitations, broadcast-receivers, and scheduling policies of Android OS.

## Scope
Details wake locks, Foreground Service allocations, and Worker specifications.

## Implementation Details
NEXUS MESH starts a `ForegroundService` presenting an active status bar notification to prevent the system CPU from entering deep sleep during packet routing.

## Revision History
- **v1.0.0 (2026-06-26)**: Background execution spec sheet.