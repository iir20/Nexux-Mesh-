# ADR 0003: Local-First Hardware-Backed Encryption and Signatures

## Status
Accepted

## Context
Decentralized networks are highly vulnerable to Sybil attacks, impersonation, and eavesdropping. In disaster or censorship scenarios, users need a guarantees that:
1.  Their local data-at-rest (chats, keys, databases) is secure against physical theft or forensic extraction.
2.  Incoming messages are genuinely authored by the stated sender (authenticity/non-repudiation).
3.  Their identity can be easily backed up, exported, or restored using offline credentials.

## Decision
We implement a zero-trust, hardware-backed identity and cryptographic engine:
1.  **Hardware-Backed Key Generation**: On first launch (onboarding), the system uses the `AndroidKeyStore` provider to generate:
    *   Symmetric 128-bit AES key (`nexus_db_encryption_key`) with GCM block mode and no padding.
    *   Asymmetric Elliptic Curve (EC) key pair (`nexus_identity_key`) using `SHA256withECDSA` signature formats.
2.  **Payload Encryption-at-Rest**: Sensitive Room tables (messages, social posts, wiki pages) are encrypted on-the-fly inside the repository layer using the hardware-backed symmetric key. Database file leakage on rooted devices yields only cipher-text.
3.  **Digital Signatures on Air**: All transactions (marketplace listings, collaborative wiki edits) are signed using the private EC key. Peers receiving updates extract the sender's public key from the transaction payload and verify the signature locally before writing to database.
4.  **BIP-39 Mnemonic Backup**: To support fully offline identity recovery, we derive key seeds from a 128-bit entropy source formatted as a 12-word mnemonic backup phrase. Users write down this phrase to restore their cryptographic identity on a new physical device without an internet connection.

## Consequences
-   **Pros**:
    -   High-grade security protecting user privacy against physical device extraction.
    -   Strong cryptographic verification preventing spam or spoofing in public mesh channels.
    -   Offline restoration of identical cryptographic identities.
-   **Cons**:
    -   KeyStore access is slow; database performance benchmarks are implemented to monitor query latencies.
    -   If a user loses their 12-word recovery phrase, there is no centralized "forgot password" service, resulting in permanent identity loss.
