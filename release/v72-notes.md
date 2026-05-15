# X88 Pro 13 AOSP Android TV ROM v72

## Artifact

- File: `X88_Pro_13_AOSP.img`
- Image type: Rockchip RKFW factory firmware package
- Size: `1,958,869,578` bytes
- SHA256: `C08F42A037D1ADD7CD2375F2A919FB432C78D7E3373C6DA2BAB399F1D76F007E`
- Device: X88 Pro 13 RK3528 Android TV box
- Included partitions/payloads: loader, parameter, uboot, misc, dtbo, vbmeta, boot, recovery, baseparameter, super
- ROM marker: `version=v72-corrected-fbc-decoder`

## Changes Since v58

- Corrects the active 32-bit Rockchip Codec2 decoder patch for the 4K VP9 path.
- Fixes the v70 no-video regression caused by a bad Thumb branch in `C2RKMpiDec::checkPreferFbcOutput`.
- Enables the MPP FBC output path for 3840x2160 VP9 playback while keeping the full stack-canary function exit intact.
- Keeps the 64-bit Codec2 patch for completeness, but the important active decoder path is `/vendor/lib/libcodec2_rk_component.so`.
- Keeps the Rockchip HWC/AFBC properties used for the 4K60 video path.
- Keeps the UR02 remote microphone fixes, Katniss default speech recognition, LeanKeyboard TV defaults, File Manager Plus, SmartTube, rooted ADB, Android TV launcher, HDMI-CEC defaults/settings, Rockchip display controls, and CPU max-speed setting.
- Updates `RockusbCli.ps1` raw image writing so non-sector-aligned raw images are padded safely and partition size is checked before writing.

## Validation

- Full RKFW factory image was packed from the v72 `super` image and the v58 factory image template.
- Flashed and booted on hardware over Rockusb/USB ADB.
- Verified `/system/etc/x88-rom-version` reports `version=v72-corrected-fbc-decoder`.
- Verified the active 32-bit decoder hash is `dce3e78e1c391c452e875760c5c1bb12d797e5a49110211d6d68acc36582a4ef`.
- Verified SmartTube can start VP9 `3840x2160@60` playback on the 1080p bench monitor.
- Verified the active decoder logs `use mpp fbc output mode`.
- Verified SurfaceFlinger reports the 4K video layer as `Fourcc=YU08`, `afbcd=1`, and about 60 fps while scaling to the 1080p sink.
- Verified no new Codec2 tombstone or `__stack_chk_fail` crash in the v72 playback captures.

## Known Caveat

Native 4K-TV validation is still needed. The 1080p bench test proves the decoder is no longer crashing and the 4K60 stream reaches the FBC path, but logs still show periodic MPP queue starvation/drop messages. If visible skipping remains on a 4K TV, the next fix should preserve FBC and focus on the remaining rkvdec/MPP queue behavior.

## Flashing

Boot the box into loader mode, connect it with a USB-A to USB-A cable through the USB 2.0 port, then manually select `X88_Pro_13_AOSP.img` in Rockchip FactoryTool or another RKFW-compatible flashing tool.
