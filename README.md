# X88 Pro 13 AOSP Android TV ROM

Custom Android TV firmware work for the X88 Pro 13 RK3528 Android TV box.

The current public test build is v34. It is based on the AOSP/Android TV image chain, with stock Rockchip/X88 hardware pieces retained where needed and stock bundled third-party launcher/apps removed from the user-facing experience.

## Current Release

- Release tag: `v0.34-smarttube-libs`
- Flashable image: `super-zygotequiesce-v34-smarttube-libs-lpmake.img`
- SHA256: `506261023D35B12114782750008A2DF510B70E20BE126DE1724D65A3E1A7780B`
- Target partition: `super`
- Device: X88 Pro 13 RK3528 Android TV box

## What Is Included

- Android TV launcher instead of the stock X88 launcher.
- Rooted ADB support.
- X88 front-panel helper service.
- X88 remote helper service and stock remote keylayout support.
- YouTube for Android TV.
- SmartTube `31.63`.
- Downloader `2.0.3-ForGoogleAndroidDevices`.
- Amaze File Manager `3.11.2`.

## v34 Fixes

- Fixes SmartTube playback error:
  `J2V8 native library not loaded ... dlopen failed: library "libj2v8.so" not found`
- The SmartTube native libraries are unpacked into:
  `/product/app/SmartTube/lib/arm64/`

## Flashing

Boot box into loader mode by holding the recovery mode button while powering on the box. This can be done by inserting a slim device into the 3.5mm port to push the hidden button. Use a USB-A to USB-A cable to connect the box to your computer using the USB 2.0 port on the box.

Use `tools/RockusbCli.ps1` from Windows PowerShell after rebooting the box into RockUSB loader mode:

```powershell
.\tools\platform-tools\adb.exe reboot loader
.\tools\RockusbCli.ps1 write-partition -Partition super -Image C:\path\to\super-zygotequiesce-v34-smarttube-libs-lpmake.img -Sparse -ChunkSectors 1024
.\tools\RockusbCli.ps1 reset
```

The release image rewrites `super` only. It does not intentionally wipe `userdata`.

## Repository Layout

- `tools/RockusbCli.ps1`: Windows RockUSB flashing helper.
- `tools/frontpanel-test/src`: Java source for the front-panel and remote helper daemons.
- `tools/x88-image-files`: files overlaid into the system image.
- `tools/patches`: debugfs patch scripts used for image surgery.
- `release/v34.sha256`: checksum for the flashable v34 image.

## Notes

This repository contains the source/configuration/scripts used for the custom ROM work. Large generated images, UART tooling/logs, handoff notes, and proprietary stock firmware extracts are not stored in git; the flashable build is attached to the GitHub release.
