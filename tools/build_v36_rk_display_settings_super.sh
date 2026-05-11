#!/usr/bin/env bash
set -euo pipefail

ROOT="/mnt/c/Users/Student/Documents/X88 FIrmware"
PARTS="${ROOT}/firmware_unpacked/X88_Pro_13_AOSP.img.dump/super_parts"
WORK="${ROOT}/work_tvsettings_rk/v36_rk_display_settings"
LPTOOLS="${ROOT}/tools/aosp15_partition_tools-main/linux_glibc_x86_64"
PATCH_DIR="${ROOT}/tools/patches"

if [ ! -d "${PATCH_DIR}" ]; then
  PATCH_DIR="${ROOT}/tools"
fi

mkdir -p "${WORK}"
rm -f \
  "${WORK}/system.img" \
  "${WORK}/system_ext.img" \
  "${WORK}/super-v36-rk-display-settings.raw.img" \
  "${WORK}/super-v36-rk-display-settings.img" \
  "${WORK}/lpdumps-v36.txt"
cp -f "${PARTS}/system_ext.img" "${WORK}/system_ext.img"
cp -f "${PARTS}/system.img" "${WORK}/system.img"
cp -f "${ROOT}/work_tvsettings_rk/TvSettings_x88_rk_internal_signed.apk" /tmp/x88_v36_TvSettings.apk
cp -f "${ROOT}/tools/x88-image-files/system/etc/init/hw/init.rc" /tmp/x88_v36_init.rc
cp -f "${ROOT}/tools/x88-image-files/system/etc/x88-rom-version" /tmp/x88_v36_rom_version

debugfs -w -f "${PATCH_DIR}/patch_system_ext_v36_rk_display_settings.debugfs" "${WORK}/system_ext.img"
e2fsck -fy "${WORK}/system_ext.img" || [ "$?" -eq 1 ]
debugfs -w -f "${PATCH_DIR}/patch_system_v36_rk_display_settings.debugfs" "${WORK}/system.img"
e2fsck -fy "${WORK}/system.img" || [ "$?" -eq 1 ]

"${LPTOOLS}/lpmake" \
  --metadata-size 65536 \
  --super-name super \
  --metadata-slots 2 \
  --device super:4294963200 \
  --group rockchip_dynamic_partitions:4290768896 \
  --partition system:readonly:830652416:rockchip_dynamic_partitions \
  --image system="${WORK}/system.img" \
  --partition system_dlkm:readonly:262144:rockchip_dynamic_partitions \
  --image system_dlkm="${PARTS}/system_dlkm.img" \
  --partition system_ext:readonly:100663296:rockchip_dynamic_partitions \
  --image system_ext="${WORK}/system_ext.img" \
  --partition vendor:readonly:144228352:rockchip_dynamic_partitions \
  --image vendor="${PARTS}/vendor.img" \
  --partition vendor_dlkm:readonly:24817664:rockchip_dynamic_partitions \
  --image vendor_dlkm="${PARTS}/vendor_dlkm.img" \
  --partition odm:readonly:741376:rockchip_dynamic_partitions \
  --image odm="${PARTS}/odm.img" \
  --partition odm_dlkm:readonly:262144:rockchip_dynamic_partitions \
  --image odm_dlkm="${PARTS}/odm_dlkm.img" \
  --partition product:readonly:734003200:rockchip_dynamic_partitions \
  --image product="${PARTS}/product.img" \
  --output "${WORK}/super-v36-rk-display-settings.raw.img"

/usr/bin/img2simg \
  "${WORK}/super-v36-rk-display-settings.raw.img" \
  "${WORK}/super-v36-rk-display-settings.img"

"${LPTOOLS}/lpdumps" -i "${WORK}/super-v36-rk-display-settings.raw.img" > "${WORK}/lpdumps-v36.txt"
ls -lh "${WORK}/system_ext.img" "${WORK}/super-v36-rk-display-settings.img" "${WORK}/super-v36-rk-display-settings.raw.img"
