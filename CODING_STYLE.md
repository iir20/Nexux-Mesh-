# Kotlin & Jetpack Compose Coding Standards

This document establishes the official coding standards, architectural boundaries, naming conventions, and performance guidelines for **Nexus Mesh** Android development.

---

## 1. Architectural Integrity

We strictly enforce the **MVVM (Model-View-ViewModel)** architectural pattern with clean database separations. No business logic or SQL querying is permitted inside UI/Compose layouts.

```
[UI Views] <---> [ViewModel / UI State] <---> [MeshRepository] <---> [Room Database]
```

*   **Views / Composables**: Responsible purely for rendering state and passing user gestures or actions upward to the ViewModel.
*   **ViewModel**: Houses application presentation state, processes intents, and handles asynchronous coroutines. All databases outputs are exposed as Kotlin Flows.
*   **Repository Layer**: Serves as the single source of truth for database operations. Automatically manages payload cryptographic encryptions.

---

## 2. Kotlin Language Guidelines

*   **Immutable State**: Prefer read-only variables (`val`) over mutable variables (`var`).
*   **Null-Safety**: Use standard safe call operators (`?.`) and Elvis operators (`?:`) rather than unsafe assertions (`!!`).
*   **Concurrency**: Always execute background tasks inside explicit coroutine scopes. Prefer `Dispatchers.IO` for disk reads/writes and `Dispatchers.Default` for CPU-heavy cryptography.
*   **Expressiveness**: Prefer high-order functions (`map`, `filter`, `flatmap`) and expression structures over verbose imperative loops:

```kotlin
// ✅ RECOMMENDED:
val decryptedMessages = rawMessages.map { it.copy(content = decrypt(it.content)) }

// ❌ AVOID:
val decryptedMessages = ArrayList<MeshMessage>()
for (msg in rawMessages) {
    decryptedMessages.add(msg.copy(content = decrypt(msg.content)))
}
```

---

## 3. Jetpack Compose Guidelines

### A. Naming Conventions
*   **Composables**: Capitalized `PascalCase` nouns (e.g., `LocalCommsScreen`, `PhysicalPerformancePanel`).
*   **Preview Composables**: Suffix with `Preview` (e.g., `LocalCommsScreenPreview`).
*   **State Containers**: Prefix with `remember` (e.g., `var usernameInput by remember { mutableStateOf("") }`).

### B. State Management
*   **Unidirectional Data Flow**: Pass states downwards into sub-composables and bubble events/gestures upwards via lambdas (`onAction: () -> Unit`).
*   **State Hoisting**: Hoist state properties out of visual components to make them testable and easily mockable in preview/screenshot suites.
*   **State Collection**: Collect Flow resources in Composables using `collectAsStateWithLifecycle()` to prevent background memory leaks:

```kotlin
// ✅ RECOMMENDED (Safely halts collection when the app goes background):
val nodes by viewModel.nodes.collectAsStateWithLifecycle()
```

### C. Performance Optimizations
*   **Avoid Large Composables**: Break down large screens into small, specialized modular composables of fewer than 100 lines.
*   **Keying Lazy Lists**: Always provide explicit unique keys in `LazyColumn` and `LazyRow` loops to avoid unnecessary recompositions when lists shift:

```kotlin
// ✅ RECOMMENDED:
LazyColumn(state = chatListState) {
    items(messages, key = { it.messageId }) { msg ->
        MessageRow(msg)
    }
}
```

*   **Avoid Hex Strings**: Never hardcode hex color strings inside visual functions. Use MaterialTheme scheme definitions (e.g., `MaterialTheme.colorScheme.primary`).

---

## 4. Testability & ID Naming Conventions

*   All interactive elements (buttons, inputs, checkboxes) **MUST** include unique `testTag` keys for automated target tracking:
    ```kotlin
    Button(
        onClick = { /* ... */ },
        modifier = Modifier.testTag("submit_button")
    ) {
        Text("Submit")
    }
    ```
*   User-facing texts **MUST** be placed in `strings.xml` to support localizations. Do not write raw literal strings directly in the layout files.
*   Vector assets and drawables **MUST** have a unique prefix `ic_` (e.g., `ic_back_arrow.xml`).
