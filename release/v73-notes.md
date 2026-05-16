# X88 Pro 13 AOSP Android TV ROM v73

## Artifact

- File: `X88_Pro_13_AOSP.img`
- Image type: Rockchip RKFW factory firmware package
- Size: `1,958,869,578` bytes
- SHA256: `7D9EC919F269752B14B18DB8225F2D01231599F420C165ED22966420F7742562`
- Device: X88 Pro 13 RK3528 Android TV box
- Included partitions/payloads: loader, parameter, uboot, misc, dtbo, vbmeta, boot, recovery, baseparameter, super
- ROM marker: `version=v73-4k-output-1080-ui`

## Changes Since v72

- Keeps HDMI output at the best sink mode, including `3840x2160p60`, while keeping the Android/UI framebuffer at `1920x1080@60`.
- This avoids the v72 behavior where the launcher and app UI rendered through a 4K framebuffer and made the whole box feel slow/low-fps.
- Defaults the Mali GPU devfreq governor to `performance`; set `persist.x88.gpu_max_lock=0` to return to `simple_ondemand`.
- Changes the Rockchip HWC experiment props to `vendor.hwc.enable_composition_drop_mode=0` and `vendor.hwc.video_buf_cache_max_size=67108864`.
- Keeps the corrected active 32-bit Rockchip Codec2 VP9 FBC decoder patch from v72.
- Keeps UR02 remote microphone fixes, Katniss default speech recognition, LeanKeyboard TV defaults, File Manager Plus, SmartTube, rooted ADB, Android TV launcher, HDMI-CEC defaults/settings, Rockchip display controls, and CPU max-speed setting.

## Validation

- Built sparse and raw `super` images, then repacked a full RKFW factory image from the v73 `super` image and the v72/v58 factory-image template.
- Verified the v73 system image reports `/system/etc/x88-rom-version -> version=v73-4k-output-1080-ui`.
- Verified the boot helper now writes `persist.vendor.framebuffer.main=1920x1080@60` and `persist.vendor.framebuffer.aux=1920x1080@60`.
- Verified the v73 vendor image contains `vendor.hwc.enable_composition_drop_mode=0` and `vendor.hwc.video_buf_cache_max_size=67108864`.
- Live v72 property testing on a 4K TV showed the desired mode split: 1080p UI framebuffer, 4K60 HDMI output, and 4K VP9 video still direct-composed as `Fourcc=YU08`, `afbcd=1`.
- Live HWC no-drop/cache testing improved the 4K FBC layer from about 43 fps to about 56.9 fps in one sample.
- eMMC spot check read 512 MiB from `/dev/block/by-name/super` at about 72 MB/s, so storage speed does not look like the 4K streaming bottleneck.

## Known Caveat

This is an improvement build, not a final 4K60 victory lap. Native 4K playback still logs `C2RKMpiDec: failed to enqueue packet` and occasional ExoPlayer `dropOutputBuffer` lines. The next target remains the Rockchip Codec2/rkvdec/MPP output queue path while preserving FBC output.

## Flashing

Boot the box into loader mode, connect it with a USB-A to USB-A cable through the USB 2.0 port, then manually select `X88_Pro_13_AOSP.img` in Rockchip FactoryTool or another RKFW-compatible flashing tool.
