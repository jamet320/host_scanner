# Host Intelligence V2 — first integration milestone

This package extends the existing Kotlin Android Host Intelligence project rather than replacing the host finder.

## Included
- Preserves the existing multi-source subdomain discovery, DNS/HTTP/TLS checks, IPv4-only results, port checks, history, and export.
- Adds a Tunnel Center screen.
- Saves SSH, HTTP, VLESS, VMess, Trojan, and Shadowsocks profiles locally.
- Imports common URI-form share links for VLESS, VMess, Trojan, Shadowsocks, and SSH. Some legacy base64-only Shadowsocks links may need manual entry.
- Imports raw Xray JSON configuration.
- Supports manual profile creation/editing, selecting, deleting, and local persistence.

## Important status
This is the profile-management milestone, **not yet a working VPN client**. A full-device VPN requires an actual packet-forwarding engine wired to Android `VpnService`. Xray-core integration and SSH-to-TUN forwarding are the next engine milestones. The app deliberately does not show a fake connected state or route traffic into a dead VPN.

## Apply
1. Back up the current repository.
2. Extract this ZIP into the repository root, allowing files to replace matching paths.
3. Commit/push to `main` or run the existing `Build Host Intelligence APK` workflow manually.
4. Download the `Host-Intelligence-APK` artifact from the completed Actions run.

## Notes
- Profile credentials are stored in app-private SharedPreferences; they are not an encrypted vault.
- Only scan domains and services you own or are authorized to test.
- Android API 35 and Java 17 settings are unchanged.

See `V2-ROADMAP.md` for the staged engine plan.
