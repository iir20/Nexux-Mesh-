# Networking Protocol Specification (Physical & Data-Link Layers)

## Purpose
Defines BLE GAP roles, GATT profiles, Wi-Fi Direct peer discovery states, and low-level socket protocol structures.

## Scope
Details payload structures, BLE advertisement packet structures, connection states, and Wi-Fi socket interfaces.

## Protocol Structure
BLE Advertisement payload (31 Bytes):
```
+---------------------+-------------------+------------------+
| Flags (3B)          | Service UUID (16B)| Custom Hash (12B)|
+---------------------+-------------------+------------------+
```

## Packet Types
1. **CONTROL-BEACON**: Low-overhead packet advertising local node existence.
2. **METADATA-EXCHANGE**: GATT characteristic read containing bloom-filters.
3. **BULK-TRANSMIT**: Raw TCP stream over Wi-Fi Direct carrier.

## Revision History
- **v1.0.0 (2026-06-26)**: Core transport and physical layer document.