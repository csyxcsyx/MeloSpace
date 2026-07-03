# Music Metadata Backfill

`scripts/backfill_music_metadata.py` is a production-side helper for deployed
MeloSpace catalogs. It is designed to be repeatable: audit first, then enrich
safe fields, and keep state under `/opt/melospace/backfill-state`.

Available tasks:

- `audit-catalog`: write a JSON report with catalog counts, missing media,
  generated placeholder art, song-album artist mismatches, duplicate title and
  artist groups, and songs missing lyrics/covers/audio.
- `real-art`: download likely real artist photos and album covers for missing
  or generated artwork. Artist photos currently use Deezer artist search; album
  covers can use iTunes Search, Deezer album search, and MusicBrainz Cover Art
  Archive. Candidates must pass a title-and-artist similarity threshold before
  the database is updated.
- `identity-art`: generate solid-color English-letter SVG initials art for
  artist avatars and selected albums that still need a fallback image.
- `release-dates`: fill empty album `release_date` values from public metadata
  sources.
- `lyrics`: invoke `scripts/import_lddc_lyrics.py` to create LDDC enhanced LRC
  files and update `song.lyric_url`.

Useful source docs:

- iTunes Search API: https://performance-partners.apple.com/search-api
- MusicBrainz Cover Art Archive API: https://musicbrainz.org/doc/Cover_Art_Archive/API
- Deezer developer FAQ: https://support.deezer.com/hc/en-gb/articles/360011538897-Deezer-FAQs-For-Developers

Dry-run audit and artwork lookup:

```bash
cd /opt/melospace/repo
python3 scripts/backfill_music_metadata.py \
  --tasks audit-catalog,real-art \
  --env-file /opt/melospace/env/backend.env \
  --media-root /opt/melospace/media \
  --state-dir /opt/melospace/backfill-state \
  --real-art-limit 10
```

Apply a controlled production batch:

```bash
cd /opt/melospace/repo
python3 scripts/backfill_music_metadata.py \
  --execute \
  --tasks audit-catalog,real-art \
  --env-file /opt/melospace/env/backend.env \
  --media-root /opt/melospace/media \
  --state-dir /opt/melospace/backfill-state \
  --real-art-limit 25 \
  --real-art-min-score 0.84
```

Full maintenance run after the artwork batches look correct:

```bash
cd /opt/melospace/repo
python3 scripts/backfill_music_metadata.py \
  --execute \
  --tasks audit-catalog,real-art,identity-art,release-dates,lyrics \
  --env-file /opt/melospace/env/backend.env \
  --media-root /opt/melospace/media \
  --state-dir /opt/melospace/backfill-state \
  --cover-results /opt/melospace/backfill-state/cover_results.json \
  --release-sources itunes,musicbrainz \
  --lyrics-sources QM,KG,NE,LRCLIB
```

`real_art_results.json`, `release_dates.json`, `lyrics.json`, and
`catalog_audit.json` are kept in the state directory. Interrupted runs can
resume without redoing successful lyric matches, and each audit report gives a
clear before/after picture of catalog quality.
