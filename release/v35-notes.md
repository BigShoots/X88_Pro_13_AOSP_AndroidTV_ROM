# X88 Pro 13 AOSP Android TV ROM

## Artifact

- File: `X88_Pro_13_AOSP.img`
- Image type: Rockchip RKFW factory firmware package
- Size: `1,844,339,274` bytes
- SHA256: `4D5D9510A1DF6AD8D9A32E8DA881148F0E8B0C787EE26B3A7FA2E4EE1FD2C111`
- Device: X88 Pro 13 RK3528 Android TV box
- Included partitions/payloads: loader, parameter, uboot, misc, dtbo, vbmeta, boot, recovery, baseparameter, super
- ROM marker: `ro.x88.rom.version=v35-frontpanel-timefix`

## Included

- Android TV launcher instead of the stock X88 launcher.
- Rooted ADB support.
- X88 front-panel helper service.
- X88 remote helper service and stock remote keylayout support.
- YouTube for Android TV.
- SmartTube `31.63`.
- Downloader `2.0.3-ForGoogleAndroidDevices`.
- Amaze File Manager `3.11.2`.

## Flashing

Boot the box into loader mode, connect it with a USB-A to USB-A cable through the USB 2.0 port, then manually select `X88_Pro_13_AOSP.img` in Rockchip FactoryTool or another RKFW-compatible flashing tool.
