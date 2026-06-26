# Local Data Retention & Purging Protocols

## Purpose
Establishes the standard data retention schedules, automated purging triggers, and memory cleanup protocols.

## Scope
Details retention policies for messages, stories, and network diagnostics.

## Retention Schedules
- **Standard Chats**: Retained up to 90 days.
- **Mesh Stories**: Automatically removed from the active feed after **24 hours**.
- **System Diagnostics Logs**: Purged automatically when the local database size exceeds **250MB** to protect device memory.