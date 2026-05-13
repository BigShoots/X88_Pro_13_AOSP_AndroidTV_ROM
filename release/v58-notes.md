# X88 Pro 13 AOSP Android TV ROM v58

## Artifact

- File: `X88_Pro_13_AOSP.img`
- Image type: Rockchip RKFW factory firmware package
- Size: `1,958,865,482` bytes
- SHA256: `85594F1E8191CFD264C6C01A2E08FB9B8C4FAA46D2E47F729E9E50AC6250C69D`
- Device: X88 Pro 13 RK3528 Android TV box
- Included partitions/payloads: loader, parameter, uboot, misc, dtbo, vbmeta, boot, recovery, baseparameter, super
- ROM marker: `version=v58-blehid-mic-keyboard`

## Changes Since v46

- Fixes app microphone buttons for Play Store, YouTube, and other apps by exposing the UR02 BLE remote microphone through the vendor `blehid` audio HAL as Android's default built-in mic route.
- Keeps Katniss as the default TV speech recognizer and avoids the blank Google TTS resolver path.
- Fixes the LeanKeyboard mic button black-screen loop by pre-granting its voice and legacy storage permissions.
- Keeps LeanKeyboard TV as the default keyboard and File Manager Plus as the baked file manager.
- Retains rooted ADB, Android TV launcher, HDMI-CEC defaults/settings, Rockchip display controls, CPU max-speed setting, and stock remote helper support.

## Validation

- Flashed and booted on hardware.
- Verified `/system/etc/x88-rom-version` reports `version=v58-blehid-mic-keyboard`.
- Verified Android audio policy exposes `hbg_audio` through the `blehid` HAL as `AUDIO_DEVICE_IN_BUILTIN_MIC`.
- Verified generic speech recognition opens Katniss with `RecognitionService#onMicrophoneOpened`.
- Verified app microphone requests no longer fail with `getInputForAttr source 6` / `createRecord -22`.
- Verified the LeanKeyboard mic button reaches Katniss instead of reopening the permission screen.

## Flashing

Boot the box into loader mode, connect it with a USB-A to USB-A cable through the USB 2.0 port, then manually select `X88_Pro_13_AOSP.img` in Rockchip FactoryTool or another RKFW-compatible flashing tool.
