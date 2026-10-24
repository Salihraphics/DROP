DROP

<p align="center">
  <img src="drop-logo.svg" alt="DROP logo" width="120" />
</p><h3 align="center">Capture everything. Find it when it matters.</h3><p align="center">
  A privacy-first, offline-first Android workspace for your notes, ideas, links, tasks, and personal knowledge.
</p><p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Storage-Local--First-C4F879?labelColor=242424" alt="Local-first" />
  <img src="https://img.shields.io/badge/Status-In%20Development-orange" alt="In development" />
</p>---

What is DROP?

Ideas appear unexpectedly. Links get buried. Notes become difficult to find. Important thoughts disappear into folders you rarely revisit.

DROP is built to make capturing information effortless and rediscovering it just as easy.

Write a note, save a link, collect an idea, organize a project, or turn a thought into a task. When you need that information again, DROP gives you tools to search, connect, and rediscover what you have saved.

DROP is designed around three principles:

- Capture first. Saving information should take as little effort as possible.
- Local by default. Your personal information should not require an account.
- Useful over flashy. Every feature should solve a real problem.

Features

Capture and organize

- Quick Capture — Capture notes, ideas, text, and links.
- Rich Text Editing — Write and organize content with the editor's supported formatting tools.
- Projects — Keep related notes and tasks together.
- Daily Log — Maintain a daily record of thoughts and activities.
- Tasks and Reminders — Turn information into actionable tasks with local reminders.
- Pinning and Search — Keep important items accessible and find saved information.
- Trash and Recovery — Recover deleted items before permanent removal.
- Android Sharing — Send supported content to DROP from other apps.

Connect your knowledge

- Connected Notes — Link notes using "[[Note Title]]" references.
- Backlinks — Discover notes that refer to the current note.
- Idea Canvas — Arrange cards visually, connect ideas, pan around the canvas, and zoom.
- ECHO — Discover related notes and revisit information using local content-matching logic.
- Resurface — Bring older notes back into view when they may be useful.
- Related Ideas — Explore connections between notes without manually organizing everything.

Capture information in different ways

- Voice Dump — Capture thoughts through speech transcription where supported.
- Paper Capture — Extract searchable text from supported images and PDF documents using OCR.
- Quick Access — Use supported shortcuts and widgets to reach important parts of DROP faster.

Privacy and control

- Offline-first architecture — Core note-taking functionality is designed to work locally.
- No mandatory account — You can use the app without creating an account.
- Encrypted Vault — Keep private notes in a separate, passphrase-protected area.
- Manual Backup and Restore — Export and restore supported data.
- Local Reminders — Manage reminders on your device.
- No advertising or analytics in the current app design.

Vault content is kept separate from ordinary notes and excluded from regular search, ECHO recommendations, widgets, and standard backups.

«Privacy is a design priority. Review the implemented release and its permissions before relying on any particular security guarantee.»

ECHO: Rediscover what you saved

Most note apps help you store information. DROP also aims to help you find connections you may have forgotten.

ECHO looks for relationships between ordinary notes using local content matching and available connections. It can surface related ideas, suggest notes worth revisiting, and help you reconnect with information you have not opened recently.

ECHO is designed to assist discovery, not modify your notes automatically. You remain in control of your content.

Design philosophy

DROP follows a few simple principles:

- Capture first: Minimize friction when saving information.
- Local by default: Keep personal information on your device.
- Simple, not limited: Offer useful depth without unnecessary complexity.
- Your data, your control: Support recovery and portable backups.
- Useful over flashy: Prioritize real problems over feature count.

Technology

Technology| Purpose
Kotlin| Primary programming language
Jetpack Compose| Android user interface
Room| Local database
Android Notifications| Local reminders
Android Sharesheet| Sharing content from other apps
On-device OCR| Text extraction from supported documents
Local matching logic| ECHO recommendations

The implementation may evolve as DROP develops.

Roadmap

DROP is under active development. The roadmap describes planned work and does not guarantee that every feature will ship in a particular release.

Current development

- [x] Core note-taking and editing
- [x] Dashboard and navigation
- [x] Projects and Daily Log
- [x] Tasks and local reminders
- [x] Search, pinning, and recoverable trash
- [x] Manual backup and restore
- [x] Connected Notes and backlinks
- [x] Idea Canvas
- [x] ECHO and note resurfacing
- [x] Voice and document capture features
- [x] Encrypted Vault
- [x] Onboarding and personalization

Planned for 0.9.x

- [ ] Rich Text Editor 2.0
- [ ] Note revision history and safe restore
- [ ] Custom templates
- [ ] Markdown, text, and PDF export
- [ ] Portable archive and attachment support
- [ ] Smart Collections
- [ ] Knowledge Health checks
- [ ] Focus Writer
- [ ] Bulk actions, metadata, and aliases

Planned for 1.x

- [ ] Stabilization, accessibility, and broader device testing
- [ ] Improved data portability and recovery
- [ ] Optional account and cross-device synchronization, subject to architecture and privacy review
- [ ] Continued performance and usability improvements

Long-term vision

- [ ] DROP Desktop
- [ ] A richer workspace for connected knowledge and projects
- [ ] Expanded views and workflows for organizing information across devices

Roadmap items may change as development, testing, and user feedback progress.

Installation

DROP is currently in development.

When a verified public release is available, installation instructions and release downloads will be published here.

For now, check the repository's Releases section for available builds.

Note: Debug APKs are intended for testing and should not be treated as stable production releases.

Backups and data portability

Your notes matter. Before testing development builds or changing devices, create a backup whenever the current version provides that option.

- Keep backups somewhere safe.
- Test restoration with non-critical data first.
- Do not assume every feature or attachment is included in every backup format.
- Vault data is intentionally excluded from standard backups.

The exact contents and compatibility of a backup depend on the implemented version and format.

Privacy

DROP is designed around local data ownership and offline-first use.

The current development direction avoids mandatory accounts, cloud dependencies for core note-taking, advertising, and analytics. Optional synchronization may be considered in a future release.

Check the permissions, documentation, and implementation of the exact version you install. Do not assume planned privacy or security features are already available.

Contributing

Bug reports, usability feedback, and focused contributions can help improve DROP.

You can contribute by:

- Reporting reproducible bugs through GitHub Issues.
- Suggesting improvements to existing workflows.
- Testing builds on different Android devices.
- Discussing proposed features before implementation.
- Submitting focused pull requests.

Please avoid including private notes, personal data, credentials, or unredacted backups in bug reports.

Contribution guidelines may be expanded as the project matures.

License

The project's license has not yet been finalized.

Until a license is published in the repository, do not assume the code is available for unrestricted reuse, redistribution, or commercial use.

Project status

DROP is an actively developed personal project. Features, interfaces, and data formats may change before the first stable release.

Testing, reliability, privacy, and data integrity take priority over adding features simply to increase the feature count.

---

<p align="center">
  <strong>Less friction. More clarity.</strong><br />
  DROP — Your personal space for things worth keeping.
</p>
