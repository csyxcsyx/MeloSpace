# Music Metadata Backfill

`scripts/backfill_music_metadata.py` is a production-side helper for deployed
MeloSpace catalogs. It performs three scoped maintenance tasks:

- Generate MeloSpace-style SVG initials art for artist avatars and selected
  albums that do not have a real album cover.
- Fill empty album `release_date` values from public metadata sources.
- Invoke the existing `scripts/import_lddc_lyrics.py` module to create LDDC
  enhanced LRC files and update `song.lyric_url`.

Example server run:

```bash
cd /opt/melospace/repo
python3 scripts/backfill_music_metadata.py \
  --execute \
  --env-file /opt/melospace/env/backend.env \
  --media-root /opt/melospace/media \
  --cover-results /opt/melospace/backfill-state/cover_results.json \
  --release-sources itunes,musicbrainz \
  --lyrics-sources QM,KG,NE,LRCLIB
```

State is written to `/opt/melospace/backfill-state` by default so interrupted
runs can resume without redoing successful lyric matches.
