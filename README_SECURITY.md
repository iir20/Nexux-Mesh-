# Security Model & Threat Mitigation Spec

## Purpose
Specifies the zero-trust security paradigm, data-at-rest protection profiles, over-the-air (OTA) transport encryption, and anti-intrusion mechanisms.

## Scope
Covers cryptographic identity generation, secure local database encryption, frame integrity verification, and replay attack prevention.

## Background
Ad-hoc networks are highly susceptible to malicious packet injection, device impersonation, and localized traffic snooping. NEXUS MESH assumes every intermediate routing node is untrusted.

## Cryptographic Architecture
- **Asymmetric Keypairs**: NIST curve secp256r1 (P-256) used for signatures and static ECDH.
- **Symmetric Encryption**: AES-GCM-256 with random 12-byte nonces.
- **Digest Algorithms**: SHA-256 for integrity verification and block chain validation.

## Security Controls
1. **Dynamic MAC Rotation**: Re-advertised IDs change periodically to protect user locations.
2. **Replay Window Filter**: Packets containing duplicate nonces or timestamps outside a 5-minute sliding window are dropped.
3. **Zero-Knowledge Challenge**: Peers prove reputation status without disclosing historical messages.

## Future Work
- Integration of Post-Quantum Cryptography (PQC) algorithms (Kyber/Dilithium) inside the Android Keystore.

## Revision History
- **v1.0.0 (2026-06-26)**: Comprehensive security model publication.