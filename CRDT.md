# Conflict-Free Replicated Data Types (CRDTs) Specification

## Purpose
Defines the eventual consistency logic, logical timestamps, and state-based merge structures powering off-grid social posts and messages.

## Scope
Details observed-remove sets (OR-Sets) and Lamport logical clocks.

## Architectural Flow
```
[Node A (Lamport: 5)] --Gossip Sync--> [Node B (Lamport: 3)]
                                          |
                                    Evaluates L-Clocks
                                          |
                                          v
                               Merge and Update Local SQLite
```

## Revision History
- **v1.0.0 (2026-06-26)**: Core consistency protocol specification.