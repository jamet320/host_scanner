# Host Intelligence — Android

Native Kotlin Android app for authorized domain/host diagnostics.

## Included
- DNS A/AAAA/CNAME/NS/MX/TXT via DNS-over-HTTPS
- Host IP resolution
- HTTPS status, final URL, response headers for CDN hints
- TLS certificate subject, issuer, validity and days remaining
- Certificate Transparency subdomain discovery via crt.sh
- Parallel subdomain IP/CDN inspection
- Optional basic TCP port checks (common ports)
- Last 20 scan reports saved locally
- Export JSON, CSV and TXT to Downloads (Android 10+)
- Follows Android system Day/Night theme

## Build APK using GitHub Actions
1. Create a GitHub repository and upload all project files.
2. Commit them to the `main` branch.
3. Open **Actions** → **Build Host Intelligence APK**.
4. Choose **Run workflow**.
5. When the run finishes, download artifact **Host-Intelligence-APK**.
6. Extract the downloaded ZIP; install `Host-Intelligence-debug.apk` on your phone.

The workflow builds a debug APK for testing. It is not signed as a Play Store release.

## Notes
- CDN detection is heuristic; `Unknown` does not prove there is no CDN.
- crt.sh only reports certificate-transparency names and does not enumerate every subdomain.
- A port shown as `closed_or_filtered` may be closed or blocked by a firewall/network.
- Use scanning features only on systems you own or are authorized to test.
