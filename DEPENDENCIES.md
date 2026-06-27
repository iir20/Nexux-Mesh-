# Dependency Management & Software Inventory

This document tracks all external libraries, SDKs, plug-ins, and build system dependencies utilized within the **Nexus Mesh** Android application, providing justifications and open-source licenses for each.

---

## 1. Core Platform & UI Dependencies

| Group & Artifact ID | Version | License | Justification |
| :--- | :---: | :---: | :--- |
| **Kotlin Standard Library** | `1.9.22` | Apache 2.0 | Core programming language runtime. |
| **Kotlin Coroutines Core / Android** | `1.7.3` | Apache 2.0 | Asynchronous non-blocking execution & state pipelines (Flow). |
| **AndroidX Core KTX** | `1.12.0` | Apache 2.0 | Standard extensions for Android framework components. |
| **AndroidX Activity Compose** | `1.8.2` | Apache 2.0 | Jetpack Compose integration with basic Activity frameworks. |
| **AndroidX Lifecycle Runtime Compose** | `2.7.0` | Apache 2.0 | Lifecycle-aware Flow collectors (`collectAsStateWithLifecycle`). |

---

## 2. Jetpack Compose & Material 3

| Group & Artifact ID | Version | License | Justification |
| :--- | :---: | :---: | :--- |
| **AndroidX Compose Compiler** | `1.5.8` | Apache 2.0 | Compiler plugin translating Compose functions to runtime structures. |
| **AndroidX Compose UI / Foundation** | `1.6.1` | Apache 2.0 | Drawing layout engine, input events, and scrolling behaviors. |
| **AndroidX Compose Material 3** | `1.2.0` | Apache 2.0 | Material Design 3 theme system, components, and widgets. |
| **AndroidX Navigation Compose** | `2.7.7` | Apache 2.0 | Type-safe navigation controller for multi-screen routing. |

---

## 3. Storage & Local Persistence Layer

| Group & Artifact ID | Version | License | Justification |
| :--- | :---: | :---: | :--- |
| **AndroidX Room Runtime** | `2.6.1` | Apache 2.0 | SQL abstraction mapping database rows directly to Kotlin entities. |
| **AndroidX Room KTX / Compiler** | `2.6.1` | Apache 2.0 | Annotation processing and coroutine flow query supports. |
| **SQLite Standard Library** | `3.45.0` | Public Domain | Embedded database engine backing local storage files. |

---

## 4. Test Frameworks & Visual Verification

| Group & Artifact ID | Version | License | Justification |
| :--- | :---: | :---: | :--- |
| **JUnit** | `4.13.2` | EPL 1.0 | Core test runner engine for unit assertions. |
| **Robolectric** | `4.11.1` | MIT | Mocking of Android framework components on local JVMs (no emulator). |
| **Roborazzi** | `1.10.1` | Apache 2.0 | Captured screenshot verification and visual regression testing. |
| **AndroidX Test Core / Runner** | `1.5.0` | Apache 2.0 | Core testing infrastructure for Robolectric contexts. |

---

## 5. Dependency Update Policy

To ensure security, stability, and compatibility, we implement a strict dependency upgrade protocol:

1.  **Weekly Dependabot Sweep**: Dependabot is configured in `.github/dependabot.yml` to automatically analyze the Version Catalog and generate pull requests for outdated libraries.
2.  **Verification Pipelines**: Any automated dependency upgrade PR **MUST** run all tests (`gradle :app:testDebugUnitTest`) and compile successfully before manual approval.
3.  **Kotlin & KSP Version Lock**: The Kotlin compiler and KSP compiler are tightly coupled. Developers are **forbidden** from upgrading Kotlin without verifying the respective compatible version of KSP and the Jetpack Compose Compiler plugin.
