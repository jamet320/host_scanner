# Host Intelligence for Android

Host Intelligence is a native Android hostname and domain reconnaissance utility. The app runs directly on the phone; it does not require a paid backend or embedded API keys. Use it only for domains and systems you own or are explicitly authorized to assess.

## Current features

- Passive subdomain discovery from Certificate Transparency (`crt.sh`), HackerTarget host search, and AlienVault OTX passive DNS.
- Bounded recursive Certificate Transparency lookups (up to 8 child-name queries per scan).
- Source attribution per hostname, source status/error reporting, deduplication, and a 500-name discovery cap.
- DNS-over-HTTPS record lookups for A, AAAA, CNAME, NS, MX, and TXT, with local resolver IP checks as a secondary signal.
- HTTP/HTTPS reachability, status code, redirect destination, response headers, timing, TLS certificate details, and heuristic CDN identification.
- Up to 200 discovered names are verified per scan to limit network load.
- JSON, CSV, and TXT report export to Downloads on Android 10+. Scan history is stored locally on the device.

## Build APK using GitHub Actions

1. Upload/replace the project files in your repository.
2. Open **Actions** and run **Build Host Intelligence APK** (or push a commit to `main`).
3. Open the successful workflow run and download the `Host-Intelligence-APK` artifact.

The workflow builds a debug APK with JDK 17 and Android SDK 35.

## Limitations

- Public discovery services are rate-limited, can be unavailable, and do not provide complete coverage. A source error is reported rather than interpreted as proof that no subdomains exist.
- Recursive lookups are deliberately bounded to keep the app usable and avoid excessive requests. This is not an exhaustive brute-force engine.
- HTTP status and CDN detection are observations/heuristics, not proof of vulnerability or ownership.
- Android may restrict background work if the app is closed during a long scan. Keep the app open for the most reliable results.
- Free public endpoints and DNS-over-HTTPS providers may change behavior without notice.
