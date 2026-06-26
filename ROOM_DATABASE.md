# Room SQLite Persistence Layer Specification

## Purpose
Specifies local entities, DAO queries, migration patterns, thread pools, and SQLite transactions.

## Scope
Details Database Entities, DAOs, and schema exports.

## Entity Definitions
- `MeshNodeEntity`: Holds peer identifiers, BLE RSSI signals, and battery stats.
- `MessageEntity`: Persistent ledger of off-grid chat messages.
- `SocialPostEntity`: Off-grid posts.

## Revision History
- **v1.0.0 (2026-06-26)**: Initial Room Database spec sheet.