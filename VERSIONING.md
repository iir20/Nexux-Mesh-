# Software Versioning Policy

Nexus Mesh adheres strictly to **Semantic Versioning (SemVer) 2.0.0** standards. This document details how versions are assigned, incremented, and released across the codebase, database schemas, and protocols.

---

## 1. Version Format

Every release version takes the following form:
`MAJOR.MINOR.PATCH[-PRERELEASE]`

*   **MAJOR Version**: Incremented for incompatible database structural changes, major protocol design breakages, or total UI theme paradigm shifts.
*   **MINOR Version**: Incremented for new fully completed feature releases (e.g., adding collaborative wiki or SOS mode), minor protocol extensions, or backward-compatible schema changes.
*   **PATCH Version**: Incremented for backward-compatible bug fixes, styling polishes, performance optimizations, or translation additions.
*   **PRERELEASE / RELEASE CANDIDATE (RC)**: Suffixes such as `-rc1` or `-beta` indicate pre-production testing candidates.

---

## 2. Android Version Code Calculations

In addition to the user-facing SemVer, the Google Play Console and Android package manager require an integer `versionCode` which must be strictly monotonically increasing.

We calculate the `versionCode` using a base-100 padding strategy to encapsulate the SemVer numbers:
`versionCode = (MAJOR * 10000) + (MINOR * 100) + PATCH`

### Examples:
*   **v1.0.0** -> `versionCode` = `10000` (1 * 10000 + 0 * 100 + 0)
*   **v1.4.12** -> `versionCode` = `10412` (1 * 10000 + 4 * 100 + 12)
*   **v9.0.0** -> `versionCode` = `90000`

---

## 3. Database Migration Versioning

Because Nexus Mesh stores all data locally, database schema upgrades must be handled with care to prevent deleting user chats during updates.

*   The local Room database version is represented by an integer in `MeshDatabase.kt` (e.g., `@Database(version = 1, ...)`).
*   Any change to entities in `/app/src/main/java/com/example/database/Entities.kt` (such as adding columns, renaming fields, or creating tables) **MUST** trigger a database version increment.
*   Developers must write appropriate `Migration` classes inside `/app/src/main/java/com/example/database/MeshDatabase.kt` and add them to the database builder.
*   **Destructive Migrations**: Using `.fallbackToDestructiveMigration()` is **STRICTLY FORBIDDEN** in production releases, as it wipes out all offline user records. It is permitted only in experimental/develop branches for speed of development.

---

## 4. Git Tagging and Releases

Every official production-ready build compiled from the `main` branch must be cryptographically signed and tagged in git:

```bash
# Example tag command for v9.0.0 release
git tag -a v9.0.0 -m "Release version 9.0.0 — Open Source Audit Completion"
git push origin v9.0.0
```
Tag descriptions should contain comprehensive changelogs detailing additions, bug fixes, and security patches.
