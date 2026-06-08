# Changelog

## [0.2.0-alpha] - 2026-06-09

### Added
- Documentation split into `docs/` with separation of concerns
  - `docs/architecture.md` — MVVM layers, process flows, component details
  - `docs/features.md` — Detailed feature descriptions
  - `docs/database.md` — Room entities, DAO, repository
  - `docs/notifications.md` — Notification flow & action handling
  - `docs/cleanup.md` — WorkManager-based cleanup pipeline
  - `docs/development.md` — Principles, design goals, MVP scope
- Standard GitHub README replacing monolithic README

### Removed
- Root `architecture.md` (superseded by `docs/architecture.md`)

## [0.1.0-alpha] - 2026-06-09

### Added
- Material You splash screen support
- Adaptive icon support for Android
- Architecture documentation for ScreenshotJanitor
- Project structure refactored and renamed to ScreenshotJanitor
- GitHub workflows configuration

### Changed
- Refactored icon resources
- Updated splash screen theme for Material You support
- Updated notification icons

### Fixed
- Icon and splash screen issues
