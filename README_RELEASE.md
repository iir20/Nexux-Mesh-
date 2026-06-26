# Release Engineering & Distribution Playbook

## Purpose
Standardizes compilation checklist, version tagging scheme, APK obfuscation verification, and artifact deployment.

## Scope
Covers Semantic Versioning (SemVer), play console artifact configurations, and offline APK direct distribution.

## Obfuscation Rules
All build rules are compiled via R8 / ProGuard. Reflection rules are defined inside `proguard-rules.pro` to prevent stripping of serialized data structures.

## Revision History
- **v1.0.0 (2026-06-26)**: Playbook release.