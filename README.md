# Host Intelligence Tunnel

Android source project for a lightweight V2Ray/Xray VPN client, with SSH profile UI planned as a separate tunnel mode. It includes VPN consent, Android `VpnService` TUN setup, raw Xray JSON input, and VLESS URI conversion for common TCP/WS/gRPC + TLS/Reality configurations.

## Build APK
1. Upload the folder contents to a GitHub repository on branch `main`.
2. Open **Actions → Build Tunnel APK → Run workflow**.
3. Download artifact **Host-Intelligence-Tunnel-APK**.

## Important status
This is a development scaffold, **not a verified working VPN release**. The GitHub workflow fetches the official libXray Android release and checks whether it contains an AAR. Upstream release packaging / Java binding names can change; inspect build output if integration fails. The VPN service does not claim success when engine startup fails. A real device test with a valid profile is required. SSH profile handling / SSH full-device tunneling is not implemented in this milestone; the app is intentionally honest about that limitation.
