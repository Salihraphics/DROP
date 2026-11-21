# Feature parity with DROP 0.10.0

The DROP source was rebuilt from the 0.10.0 release build. This file tracks every 0.10.0
feature against the rebuilt code. DROP stays in beta until every row is **Done** and the
release gates at the end are met.

**Done** means rebuilt and covered by automated tests. **Partial** lists what is still missing.
Nothing here has been tested on a real phone yet.

## Foundations

| Area | Status | Evidence |
|---|---|---|
| Database schema 7, all tables and indexes | Done | Room identity hash equals 0.10.0's (`a7697d07…`) |
| All 155 database queries | Done | Generated SQL compared with 0.10.0: no differences |
| Migrations 1→7 | Done | `MigrationTest` opens libraries from versions 1, 4 and 6 |
| Backup format 3, reading formats 1–3 | Done | `BackupTest` (12 tests) |
| Rich text model and Markdown storage | Done | `RichTextGoldenTest`: 6,500 cases recorded from 0.10.0's own code |
| Search folding (Turkish ı/İ, accents) | Done | `SearchTest`, `LibraryStateTest` |
| Settings and personalisation storage (same keys) | Done | `AppSettingsTest` |
| Theme, palette, typography, note fonts | Done | Exact colours read from the 0.10.0 build |
| DROP's icons (47) | Done | Generated from 0.10.0's own path data |
| Strings, English and Turkish (964 / 962 + 47 plurals) | Done | Copied from the 0.10.0 build |

## Features

| Feature | Status | Missing |
|---|---|---|
| Home (Dashboard) | Partial | Top bar, search box, section order, ECHO and projects done (personalisation rules match 0.10.0 on 3,000 cases, `ExperienceGoldenTest`). Missing: link, voice and scan capture and their personalised order; tips; due-reminder banner; exact 0.10.0 layout |
| Library | Partial | Top bar, selection bar, command palette and the Tasks/Links/Pinned/search requests from Home and the palette restored as in 0.10.0, bulk Projects… done. Missing: bulk property, bulk export, due-reminder banner, attachment indicators; the spaces menu offers only the spaces rebuilt so far |
| Note editor | Partial | [[link]] suggestions, backlinks panel, attachments, reminders, aliases and properties, Time Machine, Focus Writer, outline and counts, note export; the rest of the editor menu (links, projects, history, properties, resurface, export, Focus Writer, attachments) |
| Tasks by day, completed today, reopen | Done | `NoteEditorViewModelTest`, `CoreFlowUiTest` |
| Trash | Done | `CoreFlowUiTest` |
| Settings | Partial | ECHO section done. Missing: personalisation, introduction, What's new, Voice, history, Vault, Resurface and Import & Export entries |
| Backup export and import (UI) | Done | Logic tested; UI reachable from Settings |
| Onboarding and What's New | Not yet | |
| Template Studio | Done | `TemplatesTest` (412 cases match 0.10.0 exactly), `TemplatesUiTest`. Opened from the Library spaces menu; the command palette entry returns with the palette |
| ECHO | Done | Engine matches 0.10.0 on 150 recorded libraries (`EchoGoldenTest`); live index, memory and switch (`EchoRepositoryTest`); Home suggestions, related notes under a note and the Settings switch (`EchoUiTest`). ECHO in the Resurface screen returns with that screen |
| Projects | Done | Projects list, project page (rename, add and remove notes, delete keeps the notes), Home section, a note's Projects from its menu and bulk Projects… from a selection (`ProjectsUiTest`). Project export returns with Import & Export |
| Daily Log screen | Not yet | |
| Idea Canvas | Not yet | Database and backup only |
| Connected notes and backlinks | Partial | Link resolving and saving done (`LinkResolverTest`); suggestions while typing, the links sheet and backlinks screens missing |
| Aliases and properties | Partial | Value rules only; editing sheet and repository missing |
| Smart Collections | Not yet | |
| Knowledge Health | Not yet | |
| Time Machine (note history) | Not yet | Database only |
| Command palette | Partial | Screen, keyboard keys, Ctrl+K on Home and the button on Home and the Library done (`PaletteGoldenTest`: 1,200 recorded queries match 0.10.0; `PaletteUiTest`). Offers only the commands rebuilt so far: Voice Dump, Paper Capture, Resurface, Daily Log, Idea Canvas, Vault, Smart Collections, Knowledge Health and Import & Export join as they return |
| Import & Export (Markdown, ZIP, PDF) | Not yet | |
| Vault | Not yet | |
| Paper Capture and OCR | Partial | File storage and checks done; OCR engine, queue and screens missing |
| Voice Dump | Not yet | |
| Resurface (reminders) | Partial | Scheduling, alarms and notifications done; Resurface screen and reminder dialog missing |
| Share to DROP | Not yet | |
| Quick capture | Not yet | |
| Widgets (quick capture, recent, tasks) | Not yet | |
| Attachment screens (list, viewer, audio player) | Not yet | |

## Release gates for 1.0.0

- [ ] Every feature above is Done, with regression tests.
- [ ] The 1.0 brief's fixes are made (empty export files, OCR, Voice Dump availability, and so on).
- [ ] A real 0.10.0 backup restores completely into the rebuilt app.
- [ ] Release signing decided and verified (the 0.10.0 key could not be recovered; see below).
- [ ] Release build, lint and the full test suite pass.
- [ ] Tested on real phones.

### Signing

0.10.0 was signed with an Android debug key (certificate SHA-256
`a6597e5a602846bef7de75d52a2855455665567e5f50a855aab923edaa4b8dc6`). That key existed only on
the build machine that was lost. Every reachable source was searched and it was not found. An
APK contains only the public certificate, never the private key, so it can't be recovered from
the APK. No new release key has been created yet.
