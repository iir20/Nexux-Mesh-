# ADR 0002: Eventual Consistency and CRDT for Decentralized State Merging

## Status
Accepted

## Context
In a decentralized, serverless peer-to-peer mesh network, nodes connect opportunistically. There is no central timestamp authority, consensus directory, or database coordinator.
We need a data model that allows multiple nodes to make edits to their local databases independently and merge their tables automatically when they come into proximity, guaranteeing **Eventual Consistency** (all nodes converge to the same state regardless of the merge sequence).

## Decision
We implement a hybrid eventual-consistency state machine combining **Conflict-Free Replicated Data Types (CRDTs)** and **Lamport Logical Clocks**:
1.  **Unique Identifiers**: Every database entity (messages, posts, listings, wiki pages) is given a universally unique identifier (UUID) generated on the author device.
2.  **Lamport Logical Clocks**: Every edit or creation increment a local integer counter (the Lamport clock). When a message is synchronized, the receiver updates its local clock to `max(localClock, incomingClock) + 1`.
3.  **Last-Write-Wins (LWW) Register with Logical Tie-Breaking**:
    -   When two versions of a wiki page or message state conflict, the version with the highest Lamport clock wins.
    -   If the Lamport clocks are identical, we fall back to a deterministic cryptographic tie-breaker: the version with the lexicographically higher Public Key Hash wins.
4.  **Tombstone Deletion**: Deleting messages or collaborative items does not remove records from the database immediately. Instead, a boolean `isDeleted` flag (a tombstone) is propagated. This prevents deleted items from "resurrecting" during synchronizations with nodes that haven't received the deletion command.

## Consequences
-   **Pros**:
    -   Guaranteed database convergence across disjoint, ad-hoc network topologies.
    -   Enables fully offline updates with reliable conflict resolution.
    -   No consensus or coordination overhead.
-   **Cons**:
    -   **Tombstone Accumulation**: Over time, deleted records (tombstones) consume storage. We address this by running automatic local database pruning policies (cleaning expired entries after 30 days via `Room` database routines).
    -   Clock drifts or incorrect logical setups can cause a valid edit to be overridden by a stale edit if logical clock sequences are manipulated.
