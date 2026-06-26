# Known Technical Limitations

## Constraints
- **BLE Packet Size**: BLE advertisement payloads are restricted to 31 bytes.
- **OS Wake Locks**: Android aggressively restricts background tasks, requiring foreground service allocations.
- **Device Pairing Limits**: Android GATT limits simultaneous BLE client connections to 7 nodes maximum.