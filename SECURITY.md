# Security Policy — Wergêr

## Supported versions

| Version | Supported |
|---------|-----------|
| 1.0.x   | ✅ |
| < 1.0   | ❌ |

We generally support the latest released version of Wergêr on Android and
Windows. Please make sure you are on the most recent release before reporting.

## Reporting a vulnerability

If you discover a security vulnerability in Wergêr, **please do not open a public
GitHub issue.** Instead, report it privately:

- 📧 Email: **security@cybergah.com**
- Optionally, use GitHub's [private security advisory](../../security/advisories/new)
  feature for this repository.

Please include:
- A description of the vulnerability and its impact.
- Steps to reproduce (proof-of-concept if possible).
- The affected platform (Android / Windows) and app version.
- Any suggested remediation, if you have one.

## What to expect

- **Acknowledgement** within **72 hours**.
- An initial assessment and severity classification within **7 days**.
- We will keep you updated on remediation progress and coordinate a disclosure
  timeline with you.
- With your permission, we will credit you in the release notes / advisory once
  the issue is resolved.

## Scope

In scope:
- The Wergêr Android app (`app/`).
- The Wergêr Windows/desktop app (`desktop/`).
- Model download/verification logic and local storage handling.

Out of scope:
- Vulnerabilities in third-party services (e.g. a self-hosted LibreTranslate
  server you configure) — report those to the respective project.
- Issues in the NLLB-200 model itself (a Meta research artifact).

## Privacy note

Wergêr's offline engine performs translation **on-device** — your text does not
leave your device when using the offline NLLB-200 engine. When you use the
online LibreTranslate engine, text is sent to the endpoint you configure.

Thank you for helping keep Wergêr and its users safe.

— [Cybergah Group](https://cybergah.com)
