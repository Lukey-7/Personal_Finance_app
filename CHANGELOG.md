# Changelog

All notable changes are recorded here. Versions follow [Semantic Versioning](https://semver.org): `MAJOR.MINOR.PATCH`.
Each release is a git tag `vX.Y.Z` with the signed APK attached on the GitHub Releases page.

## [1.0.0] - 2026-09-17

First release.

- Layered, bank-agnostic SMS parser with confidence scoring and a manual review queue
- Encrypted on-device storage (Room on SQLCipher, Keystore-backed key)
- Rule-based auto-categorisation with manual override; manual add, edit, delete
- Dashboard, weekly and monthly trends, reduce-spending insights
- Per-category budgets with overspend alerts
- Optional, on-demand OpenAI monthly summary using aggregated totals only
- CSV export and clear-all-data
