# Changelog

## 1.1.11 - 2026-02-17

### Fixed
- Fixed server crashes caused by Hytale Update 3 (2026.02.17) breaking packet API compatibility
  - Hytale split the `Packet` interface into `Packet` and `ToClientPacket`, changing the signature of `PacketHandler.write()` 
  - This caused `NoSuchMethodError` crashes in AFK tab list updates, vanish player list updates, fly toggle, freeze, and any feature that sends packets directly
  - The crash would kill the entire world thread, disconnecting all players
- Updated Server API dependency from `2026.01.24` to `2026.02.17-255364b8e`
- Added CodeMC Hytale repository as a dependency mirror

## 1.1.10

- Previous release
