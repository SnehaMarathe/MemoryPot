# Memory Pot (Android)

Privacy-first app to remember where you kept things by saving a photo + note and later searching to retrieve it.

## How to run
1. Open the project in **Android Studio** (Giraffe+ recommended).
2. Let Gradle sync.
3. Run on a device/emulator (minSdk 26).

## Permissions used (and why)
- **CAMERA**: capture item/location photos (CameraX).
- **ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION** (optional): save last-known location + show nearby “mark as found” prompt.
- **POST_NOTIFICATIONS** (optional / Android 13+): reserved for future reminders (MVP does not schedule background notifications).

The app works without location permission: you can still save photos + label/note and manually type a place.

## Data storage (privacy-first)
- **Room DB** stores all metadata locally: label, note, placeText, timestamp, archived flag, optional lat/lon + geokey.
- Photos are stored in **app-internal storage** (`files/photos/`) and are removed when the memory is deleted.
- Settings are stored in **DataStore (Preferences)**.

No accounts, no analytics, no network calls.

## Export / Backup (v0)
Settings → Export data exports a **JSON** file containing all memories’ metadata.
(Photos are not embedded in v0 to keep export simple; v1 can add encrypted backup including photos.)

## Future roadmap ideas
- Encrypted full backup/restore (photos + DB) with user passphrase.
- Better search (Room FTS5) + synonyms.
- Optional tags + smart categories (drawer/shelf/bag).
- Home widgets / quick capture.
- Offline map preview via static tiles cache.
