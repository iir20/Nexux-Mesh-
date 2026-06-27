# Nexus Mesh — Known Technical Limitations

Nexus Mesh operates in highly constrained environments where standard cellular networks and servers are unavailable. Because of the decentralized, peer-to-peer nature of the application, several technical and hardware limitations must be recognized.

---

## 1. Physical Layer Constraints

### A. Bluetooth Low Energy (BLE) Range & Packet Limits
*   **Effective Range**: BLE signals degrade rapidly depending on physical obstacles. In open-air configurations, connection ranges can reach 50–100 meters. Indoors (reinforced concrete, metallic shielding, multiple partitions), the effective range drops to **10–15 meters**.
*   **Payload Constraints**: Standard BLE advertising packets are limited to **31 bytes**. To propagate metadata, custom fragmentation and scanning parameters must be utilized, resulting in slower handshake speeds compared to traditional Wi-Fi.
*   **Connection Limits**: The Android GATT client stack limits concurrent active BLE connections per device. Most standard chipsets support a maximum of **7 concurrent BLE connections**. Nexus Mesh addresses this by rotating active connections dynamically.

### B. Wi-Fi Direct Compatibility
*   **Device Coexistence**: Devices cannot act as a Wi-Fi Direct Group Owner (GO) and connect to traditional Wi-Fi networks simultaneously on single-antenna chipsets.
*   **Group Owner Negotiation**: The standard Wi-Fi P2P Group Owner negotiation takes between **5 to 15 seconds**, which limits instant opportunistic data exchange compared to BLE advertisements.
*   **iOS Cross-Platform Interoperability**: Android Wi-Fi Direct is historically incompatible with Apple Multipeer Connectivity. Nexus Mesh uses BLE advertising with standardized schemas as the primary bridging format for cross-platform metadata.

---

## 2. Android OS Background Restrictions

### A. Background Execution & Doze Mode
*   **Background Killing**: Android aggressively optimizes battery consumption by putting apps into **Doze Mode** or **App Standby** when the screen is off. Background BLE scans and advertisements are throttled to a fraction of their foreground rates (typically once every 5–15 minutes instead of continuous scanning).
*   **Foreground Services**: To maintain continuous mesh tracking, the app must run as a **Foreground Service** with an active notification icon. Users must manually grant the `POST_NOTIFICATIONS` permission.
*   **Wakelocks**: Standard wakelocks cannot be held indefinitely without severe battery drain and OS warnings. The system governor rules throttle mesh sync intervals dynamically based on device battery state.

### B. Manufacturer-Specific Battery Optimizations
*   Certain OEMs (Original Equipment Manufacturers) like **Samsung (OneUI)**, **Xiaomi (MIUI)**, **Huawei (EMUI)**, and **OnePlus (OxygenOS)** utilize non-standard background task managers that kill foreground services despite system-level whitelist permissions.
*   *Workaround*: Users must manually configure their device's settings to select "No restrictions" under battery optimization configurations. Links and diagnostics are provided within the **System Diagnostics Panel** of the app.

---

## 3. Large File Transfer Limitations

### A. Transmission Speeds
*   **BLE Throughput**: Peak theoretical BLE throughput is ~2 Mbps (BLE 5.0+), but effective application throughput sits around **50–100 Kbps** due to framing overhead, interference, and retransmissions.
*   **Wi-Fi Direct Fallback**: Large files (e.g., videos, ZIP archives, APKs) are queued and only transmitted when a high-speed Wi-Fi Direct connection is established. Direct transfer over BLE is restricted to files smaller than **50 KB**.

### B. Memory & Storage Constraints
*   **Chunking Strategy**: Files are divided into **512 KB chunks** to avoid out-of-memory (OOM) exceptions. Each chunk is hashed using SHA-256 for integrity.
*   **Interrupted Transfers**: If a peer moves out of physical range during a transfer, chunk state is saved locally. The transfer resumes automatically from the last successful chunk once range is re-established. If the session expires, the temporary cache is automatically pruned after **24 hours**.

---

## 4. Synchronization Edge Cases (CRDT & Lamport Clocks)

### A. Clock Drift & Merge Order
*   Nexus Mesh uses **Lamport Logical Clocks** combined with UTC timestamps to merge offline data updates (collaborative Wiki, message threads) deterministically.
*   If a device's physical hardware clock is severely drifted (e.g., set to the year 2010), UTC timestamps may cause synchronization visual glitches, though the logical Lamport clock will guarantee eventual state consistency.

### B. Collaborative Conflict Resolution
*   **Wiki Merging**: Editing the exact same paragraph of a Wiki page on two offline devices simultaneously will trigger a logical merge order where the modification with the higher Lamport clock / public key hash sequence "wins". True multi-cursor collaborative editing is not supported under high-latency mesh transports.
*   **No Central Directory**: There is no global consensus registry. If two users change their username to the exact same display moniker offline, their unique Public Key Hash remains distinct, but visual ambiguities can occur in the UI until they connect to a shared peer who resolves the cryptographic distinction.
