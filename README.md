# X88 Pro 13 AOSP Android TV ROM

Custom Android TV firmware work for the X88 Pro 13 RK3528 Android TV box.

The current public test build is v36. It is based on the AOSP/Android TV image chain, with stock Rockchip/X88 hardware pieces retained where needed and stock bundled third-party launcher/apps removed from the user-facing experience.

## Current Release

- Release tag: `v0.36`
- Flashable image: `X88_Pro_13_AOSP.img`
- Image type: Rockchip RKFW factory firmware package
- Size: `1,844,374,090` bytes
- SHA256: `902179FBD08A9142F7343809C20FB872BBC54CF947042338F6E46E4A9F092BCD`
- Device: X88 Pro 13 RK3528 Android TV box
- Included partitions/payloads: loader, parameter, uboot, misc, dtbo, vbmeta, boot, recovery, baseparameter, super
- ROM marker: `version=v36-rk-display-settings`

## What Is Included

- Android TV launcher instead of the stock X88 launcher.
- Rooted ADB support.
- X88 front-panel helper service.
- X88 remote helper service and stock remote keylayout support.
- Rockchip display controls integrated into Android TV Display settings.
- YouTube for Android TV.
- SmartTube `31.63`.
- Downloader `2.0.3-ForGoogleAndroidDevices`.
- Amaze File Manager `3.11.2`.

## Flashing

Boot box into loader mode by holding the recovery mode button while powering on the box. This can be done by inserting a slim device into the 3.5mm port to push the hidden button. Use a USB-A to USB-A cable to connect the box to your computer using the USB 2.0 port on the box.

Use Rockchip FactoryTool or another Rockchip RKFW-compatible flashing tool. Manually select `X88_Pro_13_AOSP.img` in the tool before flashing.

The release image is a full factory firmware package, not a raw `super` partition image.

## Repository Layout

- `tools/RockusbCli.ps1`: Windows RockUSB flashing helper.
- `tools/frontpanel-test/src`: Java source for the front-panel and remote helper daemons.
- `tools/x88-image-files`: files overlaid into the system image.
- `tools/patches`: debugfs patch scripts used for image surgery.
- `release/X88_Pro_13_AOSP.sha256`: checksum for the flashable factory image.

## Notes

This repository contains the source/configuration/scripts used for the custom ROM work. Large generated images, UART tooling/logs, handoff notes, and proprietary stock firmware extracts are not stored in git; the flashable build is attached to the GitHub release.
