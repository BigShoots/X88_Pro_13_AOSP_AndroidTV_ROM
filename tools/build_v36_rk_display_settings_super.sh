#!/usr/bin/env bash
set -euo pipefail

ROOT="/mnt/c/Users/Student/Documents/X88 FIrmware"
PARTS="${ROOT}/firmware_unpacked/X88_Pro_13_AOSP.img.dump/super_parts"
WORK="${ROOT}/work_tvsettings_rk/v43_cec_bt"
TAG="v43-cec-bt"
LPTOOLS="${ROOT}/tools/aosp15_partition_tools-main/linux_glibc_x86_64"
PATCH_DIR="${ROOT}/tools/patches"

if [ ! -d "${PATCH_DIR}" ]; then
  PATCH_DIR="${ROOT}/tools"
fi

mkdir -p "${WORK}"
rm -f \
  "${WORK}/system.img" \
  "${WORK}/system_ext.img" \
  "${WORK}/vendor.img" \
  "${WORK}/super-${TAG}.raw.img" \
  "${WORK}/super-${TAG}.img" \
  "${WORK}/lpdumps-${TAG}.txt"
cp -f "${PARTS}/system_ext.img" "${WORK}/system_ext.img"
cp -f "${PARTS}/system.img" "${WORK}/system.img"
cp -f "${PARTS}/vendor.img" "${WORK}/vendor.img"
cp -f "${ROOT}/work_tvsettings_rk/TvSettings_x88_rk_internal_signed.apk" /tmp/x88_v36_TvSettings.apk
cp -f "${ROOT}/tools/x88-image-files/system/etc/init/hw/init.rc" /tmp/x88_v36_init.rc
cp -f "${ROOT}/tools/x88-image-files/system/etc/x88-rom-version" /tmp/x88_v36_rom_version
cp -f "${ROOT}/tools/x88-image-files/system/bin/x88-cpu-lock" /tmp/x88_v36_cpu_lock
cp -f "${ROOT}/tools/x88-image-files/system/bin/x88-cec-bt-setup" /tmp/x88_v36_cec_bt_setup
cp -f "${ROOT}/tools/frontpanel-test/build/x88-hw.jar" /tmp/x88_v36_x88_hw.jar
cp -f "${ROOT}/tools/x88-image-files/system/bin/x88-remote" /tmp/x88_v36_x88_remote
cp -f "${ROOT}/tools/x88-image-files/system/bin/x88-remote-support" /tmp/x88_v36_x88_remote_support
cp -f "${ROOT}/tools/x88-image-files/system/etc/x88-remote.conf" /tmp/x88_v36_x88_remote_conf
cp -f "${ROOT}/tools/x88-image-files/system/etc/x88-build.prop.append" /tmp/x88_v36_build_prop_append
cp -f "${ROOT}/tools/x88-image-files/system/etc/permissions/privapp-permissions-com.swe.myapplication.xml" /tmp/x88_v36_btremotehelp_privapp
cp -f "${ROOT}/tools/x88-image-files/vendor/usr/keylayout/ffa90030_pwm.kl" /tmp/x88_v36_ffa90030_pwm_kl

SYSTEM_PATCH="/tmp/x88_v36_system_patch.debugfs"
cp -f "${PATCH_DIR}/patch_system_v36_rk_display_settings.debugfs" "${SYSTEM_PATCH}"
BTREMOTE_SRC="${ROOT}/tools/x88-image-files/system/priv-app/BtRemotehelp/BtRemotehelp.apk"
if [ -f "${BTREMOTE_SRC}" ]; then
  cp -f "${BTREMOTE_SRC}" /tmp/x88_v36_BtRemotehelp.apk
  cat "${PATCH_DIR}/patch_system_v43_btremotehelp.debugfs" >> "${SYSTEM_PATCH}"
else
  echo "WARN: BtRemotehelp.apk not present; image will omit the stock Bluetooth remote pairing helper." >&2
fi

debugfs -R "dump /system/build.prop /tmp/x88_v36_build_prop_base" "${WORK}/system.img"
awk -F= '
  NR == FNR {
    if ($0 !~ /^#/ && $1 != "") {
      skip[$1] = 1
    }
    next
  }
  {
    split($0, parts, "=")
    if (!skip[parts[1]]) {
      print
    }
  }
' /tmp/x88_v36_build_prop_append /tmp/x88_v36_build_prop_base > /tmp/x88_v36_build_prop
{
  echo ""
  echo "# X88 IR remote and display support"
  cat /tmp/x88_v36_build_prop_append
} >> /tmp/x88_v36_build_prop

debugfs -w -f "${PATCH_DIR}/patch_system_ext_v36_rk_display_settings.debugfs" "${WORK}/system_ext.img"
e2fsck -fy "${WORK}/system_ext.img" || [ "$?" -eq 1 ]
debugfs -w -f "${SYSTEM_PATCH}" "${WORK}/system.img"
e2fsck -fy "${WORK}/system.img" || [ "$?" -eq 1 ]
debugfs -w -f "${PATCH_DIR}/patch_vendor_v36_rk_display_settings.debugfs" "${WORK}/vendor.img"
e2fsck -fy "${WORK}/vendor.img" || [ "$?" -eq 1 ]

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
  --image vendor="${WORK}/vendor.img" \
  --partition vendor_dlkm:readonly:24817664:rockchip_dynamic_partitions \
  --image vendor_dlkm="${PARTS}/vendor_dlkm.img" \
  --partition odm:readonly:741376:rockchip_dynamic_partitions \
  --image odm="${PARTS}/odm.img" \
  --partition odm_dlkm:readonly:262144:rockchip_dynamic_partitions \
  --image odm_dlkm="${PARTS}/odm_dlkm.img" \
  --partition product:readonly:734003200:rockchip_dynamic_partitions \
  --image product="${PARTS}/product.img" \
  --output "${WORK}/super-${TAG}.raw.img"

/usr/bin/img2simg \
  "${WORK}/super-${TAG}.raw.img" \
  "${WORK}/super-${TAG}.img"

"${LPTOOLS}/lpdumps" -i "${WORK}/super-${TAG}.raw.img" > "${WORK}/lpdumps-${TAG}.txt"
ls -lh "${WORK}/system_ext.img" "${WORK}/super-${TAG}.img" "${WORK}/super-${TAG}.raw.img"
