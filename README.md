# X88 Pro 13 AOSP Android TV ROM

Custom Android TV firmware work for the X88 Pro 13 RK3528 Android TV box.

The current public test build is v72. It is based on the AOSP/Android TV image chain, with stock Rockchip/X88 hardware pieces retained where needed and stock bundled third-party launcher/apps removed from the user-facing experience. This release keeps the Android TV/Google TV app stack, preserves the UR02 remote microphone fixes, and corrects the active Rockchip Codec2 4K video buffer path so VP9 2160p60 can use MPP FBC output without crashing the decoder service.

## Current Release

- Release tag: `v0.72`
- Flashable image: `X88_Pro_13_AOSP.img`
- Image type: Rockchip RKFW factory firmware package
- Size: `1,958,869,578` bytes
- SHA256: `C08F42A037D1ADD7CD2375F2A919FB432C78D7E3373C6DA2BAB399F1D76F007E`
- Device: X88 Pro 13 RK3528 Android TV box
- Included partitions/payloads: loader, parameter, uboot, misc, dtbo, vbmeta, boot, recovery, baseparameter, super
- ROM marker: `version=v72-corrected-fbc-decoder`
- Baseline: v72 tested build

## What Is Included

- Android TV launcher instead of the stock X88 launcher.
- Rooted ADB support.
- X88 front-panel helper service.
- X88 remote helper service and stock remote keylayout support.
- IR cursor/mouse mode for the stock remote through a UHID virtual mouse.
- HDMI-CEC defaults enabled at first boot.
- HDMI-CEC settings integrated into Android TV Display & Sound settings.
- Stock Bluetooth remote pairing helper included in the release image.
- Rockchip display controls integrated into Android TV Display settings.
- CPU max-speed lock setting under Device Preferences.
- UR02 BLE remote microphone exposed through the BLEHID audio HAL for app voice search.
- Katniss selected as the default speech recognizer.
- LeanKeyboard TV selected as the default IME, with voice/search permissions pre-granted.
- File Manager Plus replaces Amaze File Manager.
- SmartTube `31.63`.
- Downloader `2.0.3-ForGoogleAndroidDevices`.
- Corrected Rockchip Codec2/MPP FBC path for 4K VP9 playback.

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

This repository contains the source/configuration/scripts used for the custom ROM work. Large generated images, UART tooling/logs, handoff notes, proprietary stock firmware extracts, and the optional stock Bluetooth pairing helper APK are not stored in git; the flashable build is attached to the GitHub release.
