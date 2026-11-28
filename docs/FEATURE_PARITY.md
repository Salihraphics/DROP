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
| Home (Dashboard) | Partial | Top bar, search box, section order, ECHO and projects done (personalisation rules match 0.10.0 on 3,000 cases, `ExperienceGoldenTest`). Capture buttons follow the personalised order; Scan and Voice are back. Missing: link capture; tips; due-reminder banner; exact 0.10.0 layout |
| Library | Partial | Top bar, selection bar, command palette and the Tasks/Links/Pinned/search requests from Home and the palette restored as in 0.10.0, bulk Projects… done. Missing: bulk property, bulk export, due-reminder banner, attachment indicators; the spaces menu offers only the spaces rebuilt so far |
| Note editor | Partial | [[link]] suggestions, backlinks panel, attachments, reminders, aliases and properties, Time Machine, Focus Writer, outline and counts, note export; the rest of the editor menu (links, projects, history, properties, resurface, export, Focus Writer, attachments) |
| Tasks by day, completed today, reopen | Done | `NoteEditorViewModelTest`, `CoreFlowUiTest` |
| Trash | Done | `CoreFlowUiTest` |
| Settings | Partial | ECHO, Vault and Voice Dump sections done. Missing: personalisation, introduction, What's new, history, Resurface and Import & Export entries |
| Backup export and import (UI) | Done | Logic tested; UI reachable from Settings |
| Onboarding and What's New | Not yet | |
| Template Studio | Done | `TemplatesTest` (412 cases match 0.10.0 exactly), `TemplatesUiTest`. Opened from the Library spaces menu; the command palette entry returns with the palette |
| ECHO | Done | Engine matches 0.10.0 on 150 recorded libraries (`EchoGoldenTest`); live index, memory and switch (`EchoRepositoryTest`); Home suggestions, related notes under a note and the Settings switch (`EchoUiTest`). ECHO in the Resurface screen returns with that screen |
| Projects | Done | Projects list, project page (rename, add and remove notes, delete keeps the notes), Home section, a note's Projects from its menu and bulk Projects… from a selection (`ProjectsUiTest`). Project export returns with Import & Export |
| Daily Log screen | Not yet | |
| Idea Canvas | Done | Canvas list and canvas: add notes, text blocks, drag, nudge (accessibility actions), connect, shape and colour, remove (the note stays), rename, delete (notes stay), pinch and buttons to zoom, show all, remembered view. Viewport maths match 0.10.0 on 2,000 recorded cases (`CanvasTest`), `CanvasUiTest`. Dragging and pinching were not exercised by a test; check them on a phone |
| Connected notes and backlinks | Partial | Link resolving and saving done (`LinkResolverTest`); suggestions while typing, the links sheet and backlinks screens missing |
| Aliases and properties | Partial | Value rules only; editing sheet and repository missing |
| Smart Collections | Not yet | |
| Knowledge Health | Not yet | |
| Time Machine (note history) | Not yet | Database only |
| Command palette | Partial | Screen, keyboard keys, Ctrl+K on Home and the button on Home and the Library done (`PaletteGoldenTest`: 1,200 recorded queries match 0.10.0; `PaletteUiTest`). Offers only the commands rebuilt so far: Resurface, Daily Log, Smart Collections, Knowledge Health and Import & Export join as they return |
| Import & Export (Markdown, ZIP, PDF) | Not yet | |
| Vault | Done | Passphrase setup with the no-recovery acknowledgement, unlock with a 30-second cooldown after every 5 wrong tries, encrypted list with search, notes that save as you type, change passphrase, delete Vault, erase after a forgotten passphrase. Locks on leaving DROP, screen off, leaving the Vault and after 5 minutes idle; the editor drops its plaintext on lock. Vault screens and dialogs block screenshots. Opens from Settings and the command palette. `VaultTest`: a vault sealed by 0.10.0 opens and records encode byte-identically; `VaultUiTest`: create, write, lock, wrong and right passphrase, no plaintext in the file, lock on stop, erase |
| Paper Capture and OCR | Done, improved | Take a photo, choose an image or a PDF; preview, title, save as a note or attach to one (editor menu "Add scan or file", Home's Scan button, the command palette). Text is read on the phone in the background (English and Turkish, up to 30 PDF pages), survives restarts, and is searchable. **Improved over 0.10.0**: the sheet is found and straightened, clear columns are read one after the other, Sauvola thresholding handles shadows, sideways pages are turned, and wrapped lines are rejoined. On 33 phone-photo test pages character errors fell from 23.1% to 1.0% (word errors 50.8% to 4.8%); the benchmark harness `OcrBenchHarness` runs the real pipeline when given `-DocrBench`. A phone without the native OCR library marks files "couldn't read" instead of crashing. Tests: `PagePrepTest`, `OcrQueueTest`, `PaperCaptureTest`. Not tested on a phone yet: real camera photos |
| Voice Dump | Done, improved | Transcribe (on-device, or the phone's speech service only when allowed) or record audio (AAC, up to 30 minutes), keeps listening through pauses, keeps what was heard after an error, review and edit before saving, saves a text note or a voice note without duplicates on retry, stops and keeps on leaving the app, launcher shortcut starts at once, Home's Voice button and the palette. Settings has the speech-service switch. **1.0 brief P1.4**: transcription and recording are clearly separate; when transcription can't run, the screen says why and offers *Record audio instead*, *Use the phone's speech service* (with the privacy warning first) and, on Android 13+, *Download speech for this language*; a failing capability check no longer hides an available speech service; nothing is ever shown as transcribed that wasn't heard. The microphone permission and the speech-service query were missing from the rebuilt manifest and are back. Tests: `VoiceTest`, `VoiceUiTest`. Not tested on a phone yet: real speech recognition and recording |
| Resurface (reminders) | Partial | Scheduling, alarms and notifications done; Resurface screen and reminder dialog missing |
| Share to DROP | Not yet | |
| Quick capture | Not yet | |
| Widgets (quick capture, recent, tasks) | Done | Quick capture (note, task), recent notes (date and link host, rows fit the widget's height and text size) and open tasks (count in the title, tick to complete without opening DROP). Follow DROP's light or dark theme, redraw shortly after notes change and when the theme changes. The quick capture window saves a note or a task (first line as the title), shows a link it found, and never saves twice on a retry. Tests: `WidgetsTest`, `QuickCaptureUiTest`. Not tested on a phone yet: placing widgets on a real launcher |
| Attachment screens (list, viewer, audio player) | Done | List in the editor, attachment screen with preview, PDF pages, open in another app, delete, and the text section (copy, add to note, correct and save, read again with a warning when corrected). Voice notes play in the audio player (pauses when DROP leaves the screen) |

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
