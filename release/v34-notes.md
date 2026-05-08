# v0.34-smarttube-libs

## Artifact

- File: `super-zygotequiesce-v34-smarttube-libs-lpmake.img`
- Target partition: `super`
- Size: `1,745,257,112` bytes
- SHA256: `506261023D35B12114782750008A2DF510B70E20BE126DE1724D65A3E1A7780B`

## Changes Since v33

- Added unpacked SmartTube native libraries under `/product/app/SmartTube/lib/arm64/`.
- Fixes SmartTube playback failure caused by missing `libj2v8.so`.

## Verified

- Android booted and `sys.boot_completed=1`.
- ADB returned after flash.
- `/product/app/SmartTube/lib/arm64/libj2v8.so` exists on the live device.
- SmartTube launched a YouTube URL into `PlaybackActivity`.
- Logcat showed normal `VideoLoaderController` and ExoPlayer activity without the previous `j2v8`/`dlopen` failure.
- X88 remote and front-panel services remained running.

## Flash Command

```powershell
.\tools\RockusbCli.ps1 write-partition -Partition super -Image .\super-zygotequiesce-v34-smarttube-libs-lpmake.img -Sparse -ChunkSectors 1024
.\tools\RockusbCli.ps1 reset
```
