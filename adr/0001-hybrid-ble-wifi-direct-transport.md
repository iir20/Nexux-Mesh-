# ADR 0001: Hybrid BLE and Wi-Fi Direct Peer-to-Peer Transport

## Status
Accepted

## Context
Nexus Mesh is built to provide peer-to-peer social networking and communication without server access.
We need a wireless communication transport mechanism that:
1.  Is natively supported by modern commercial Android devices.
2.  Operates fully offline.
3.  Balancing energy-efficiency and transfer bandwidth constraints.

Standard Bluetooth (Classic) requires manual pairing PIN prompts for every device connection, which is unusable for fluid, ad-hoc mesh formation. Bluetooth Low Energy (BLE) offers automatic ad-hoc connection and low power draw, but is bandwidth-constrained (~100 Kbps effective application speed). Wi-Fi Direct offers high-speed transfers (~10–50 Mbps) but requires high power draw and long setup handshakes (5–15 seconds).

## Decision
We implement a hybrid transport architecture:
1.  **BLE (Primary Discovery & Metadata Sync)**: Use BLE Advertisements and GATT services as the low-power "control plane." Devices advertise their identity hashes, Lamport clock levels, and tiny message summaries continuously.
2.  **Wi-Fi Direct (High-Bandwidth Data Plane)**: When two peers need to synchronize large chunks of data (such as files, wiki contents, or database histories) as determined by BLE metadata comparison, they spin up a temporary Wi-Fi Direct group connection.
3.  **Dynamic Connection Lifetimes**: Wi-Fi Direct connections are disconnected immediately after queue synchronization finishes to return the wireless chipsets to low-power BLE configurations.

## Consequences
-   **Pros**:
    -   Drastically reduced battery consumption during quiet background operations.
    -   High-speed file transfer capabilities when required.
    -   Seamless, automatic discovery without user pairing prompts.
-   **Cons**:
    -   Increased protocol complexity (handshake transitions between BLE and Wi-Fi Direct state machines).
    -   Temporary local network interruptions (disconnection from home Wi-Fi networks when negotiating a Wi-Fi Direct group).
