# Android Client Architecture & Subsystems Specification

## Purpose
Establishes the specific Android software patterns, component mappings, lifecycle management, and dynamic state bindings implemented inside the client codebase.

## Scope
Details MVVM implementations, Jetpack Compose lifecycle bindings, state flow collection, and asynchronous coroutine models.

## Background
Standard Android system components (Activities, Services, Broadcast Receivers) are isolated and transient. NEXUS MESH utilizes state preservation engines coupled with Local Storage to present cohesive reactive interfaces.

## Subsystem Architecture
```
[Jetpack Compose View] <--- collectsState --- [MeshViewModel (StateFlow)]
                                                    |
                                             invokesSuspends
                                                    |
                                                    v
                                         [MeshRepository (Room DAO / Bluetooth)]
```

## Implementation Details
The UI layer is written entirely in Jetpack Compose utilizing `collectAsStateWithLifecycle()` to prevent background memory leaks.

## Revision History
- **v1.0.0 (2026-06-26)**: Core Android Architecture spec sheet.