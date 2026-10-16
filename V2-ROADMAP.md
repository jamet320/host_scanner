# Host Intelligence V2 roadmap

## Milestone 1 — profiles + existing finder (this package)
- Keep the existing host discovery, DNS/HTTP/TLS, IPv4 filtering, CDN detection, history, and exports.
- Add local profiles, manual SSH/HTTP/VLESS/VMess/Trojan/Shadowsocks profile forms, share-link import, and raw Xray JSON import.
- Do not claim VPN connectivity until a packet engine is actually running.

## Milestone 2 — Xray full-device VPN
- Build and pin a compatible Android Xray core artifact.
- Add `VpnService`, foreground notification, consent flow, TUN setup, protected core sockets, start/stop/error lifecycle.
- Translate supported share links to validated Xray JSON, and support raw Xray JSON with validation.
- Test on real Android devices before exposing the Connect button.

## Milestone 3 — SSH / HTTP tunnel engine
- Add SSH authentication and SSH forwarding.
- Add HTTP CONNECT and optional TLS/SNI configuration.
- For full-device mode, bridge the Android TUN interface through a maintained tun2socks implementation into the local SSH/HTTP proxy.
- Keep local-proxy mode separate from full-device VPN mode; report the actual active mode clearly.

## Milestone 4 — polish
- Per-profile latency test, connection logs, traffic counters, favorites, profile backup/restore.
- Encrypt stored secrets or use Android Keystore-backed storage before calling the profile vault secure.
- Add tests for share-link parsing, JSON validation, and service start/stop recovery.

## Non-goals for the lightweight build
- No server list/subscription service is bundled.
- Host discovery does not imply a host is an authorized or usable tunnel endpoint.
- A discovered host must only be used with permission and a compatible server configuration.
