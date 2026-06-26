# Build & Compiler Configurations

## Purpose
Documents the compiler pipeline, dependency locks, Kotlin Symbol Processing (KSP) setup, and dynamic compilation flags.

## Scope
Details target architectures, version catalysts, local property overrides, and APK signing configurations.

## Build Setup
The build is automated using Gradle Kotlin DSL and utilizes Maven Central repositories. KSP is enabled for compiling the Room database classes.

## Commands
```bash
# Clean builds
gradle clean assembleDebug
# Release obfuscated compilation
gradle assembleRelease
```

## Revision History
- **v1.0.0 (2026-06-26)**: Initial build configuration reference.