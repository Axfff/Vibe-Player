# Tagged display fixtures

These three locally generated, 30-second mono MP3 tones have embedded ID3 metadata
with distinct titles and albums for the Title/Filename UI regression. Their titles differ from
the temporary filenames assigned by `RegressionFixture`. No recorded or licensed
music is included.

Generated with FFmpeg/libmp3lame, using this command for each label and frequency
(A: 440 Hz; B: 554 Hz; C: 659 Hz):

```sh
ffmpeg -f lavfi -i 'sine=frequency=440:sample_rate=22050:duration=30' \
  -codec:a libmp3lame -b:a 32k -id3v2_version 3 \
  -metadata 'title=Regression display title A' \
  -metadata 'artist=Vibe regression fixtures' \
  -metadata 'album=Regression display album A' playlist-display-A.mp3
```

Only the presentation test opts into these assets. Existing RG tests keep their
original WAV fixture. The app reads the embedded metadata through real MediaStore
scanning; the tests do not replace the ViewModel's library state.
