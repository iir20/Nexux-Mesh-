# Developer Onboarding Guide

Welcome to the **Nexus Mesh** open-source development community! This guide provides everything an engineer needs to set up, build, understand, test, and contribute to the Nexus Mesh Android codebase.

---

## 1. Prerequisites & Toolchain Setup

To compile and execute Nexus Mesh, you need the following standard tools:

*   **Operating System**: Linux (Ubuntu 22.04+ recommended), macOS (13+), or Windows 11 (with WSL2).
*   **Java Development Kit (JDK)**: JDK 17 (Eclipse Temurin or Azul Zulu is recommended). Set your `$JAVA_HOME` environment variable accordingly.
*   **Android SDK**: Command-line tools or Android Studio (Iguana 2023.3.1 or newer).
    *   Build Tools Version: `35.0.0`
    *   SDK Platforms: Android 15 (API 35, 36)
*   **Build System**: Gradle 8.4+ (Kotlin DSL).

---

## 2. Fast-Start Build Workflow

Follow these command-line instructions to clone, build, and verify your workspace setup:

```bash
# 1. Clone the repository
git clone https://github.com/aistudio/nexus-mesh.git
cd nexus-mesh

# 2. Build the project and run all JVM Unit & Robolectric tests
gradle :app:testDebugUnitTest

# 3. Compile a debug APK
gradle :app:assembleDebug
```

The compiled APK will be located at:
`/app/build/outputs/apk/debug/app-debug.apk`

---

## 3. Architecture Walkthrough

Nexus Mesh utilizes a modern, clean, local-first architecture pattern:

```
+---------------------------------------------------------+
|                    UI Components                        |
|   (Jetpack Compose: LocalComms, NetworkLab, SOS...)     |
+---------------------------+-----------------------------+
                            |
                            v
+---------------------------------------------------------+
|                     State Management                    |
|   (ViewModel, Kotlin Flow StateIn, MutableStateFlow)    |
+---------------------------+-----------------------------+
                            |
                            v
+---------------------------------------------------------+
|                  Data Repository Layer                  |
|    (MeshRepository - Cryptographic AES decryption)      |
+---------------------------+-----------------------------+
                            |
                            v
+---------------------------------------------------------+
|                   Local Database (Room)                 |
|   (MeshDatabase, MeshDao - Local SQLite Storage)       |
+---------------------------------------------------------+
```

### Key Source Files to Inspect first:
1.  **`/app/src/main/java/com/example/MainActivity.kt`**: Contains the primary Jetpack Compose app container (`NexusMeshApp`), sub-screen routing, state management flow definitions, and BLE/Simulation engines.
2.  **`/app/src/main/java/com/example/database/Entities.kt`**: Contains all Room table definitions.
3.  **`/app/src/main/java/com/example/database/SecurityHelper.kt`**: Manages cryptographic enclaves, AES-GCM decryption, EC-ECDSA signatures, and seed recoveries.

---

## 4. Test Suites and Visual Regression Testing

We maintain high-density test suites that execute locally on JVM without requiring physical Android hardware or emulators:

### Running Local JVM & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```
These tests verify database schemas, security encryptions, message routing logic, and state changes.

### Running Screenshot Regression Tests (Roborazzi)
To verify that the user interface maintains pixel-perfect visual quality and does not suffer from regressions:
```bash
# 1. Verify screenshot layout matches base image references
gradle :app:verifyRoborazziDebug

# 2. Record new base image references (run after making intentional UI changes)
gradle :app:recordRoborazziDebug
```

---

## 5. Contributing Guidelines & Issue Workflows

1.  **Find a Task**: Look at the `.github/ISSUE_TEMPLATE` or `/IMPLEMENTATION_STATUS.md` to see planned features.
2.  **Coding Conventions**: Read and conform to `/CODING_STYLE.md`.
3.  **Branching Strategy**:
    *   `main`: Stable releases only.
    *   `develop`: Integration branch for new features.
    *   Create branches with naming schemes: `feature/your-feature` or `bugfix/issue-description`.
4.  **Pull Requests**: Open a PR targeting `develop` using the template provided in `.github/PULL_REQUEST_TEMPLATE.md`. Ensure that all tests pass.
