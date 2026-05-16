#!/usr/bin/env bash
set -euo pipefail

ROOT="/mnt/c/Users/Student/Documents/X88 FIrmware"
PARTS="${ROOT}/firmware_unpacked/X88_Pro_13_AOSP.img.dump/super_parts"
WORK="${ROOT}/work_tvsettings_rk/v73_4k_output_1080_ui"
TAG="v73-4k-output-1080-ui"
LPTOOLS="${ROOT}/tools/aosp15_partition_tools-main/linux_glibc_x86_64"
PATCH_DIR="${ROOT}/tools/patches"

if [ ! -d "${PATCH_DIR}" ]; then
  PATCH_DIR="${ROOT}/tools"
fi

mkdir -p "${WORK}"
rm -f \
  "${WORK}/system.img" \
  "${WORK}/system_ext.img" \
  "${WORK}/product.img" \
  "${WORK}/vendor.img" \
  "${WORK}/super-${TAG}.raw.img" \
  "${WORK}/super-${TAG}.img" \
  "${WORK}/lpdumps-${TAG}.txt"
cp -f "${PARTS}/system_ext.img" "${WORK}/system_ext.img"
cp -f "${PARTS}/system.img" "${WORK}/system.img"
cp -f "${PARTS}/product.img" "${WORK}/product.img"
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
cp -f "${ROOT}/tools/x88-image-files/system/usr/keylayout/Vendor_0508_Product_1980.kl" /tmp/x88_v52_ur02_kl
cp -f "${ROOT}/tools/x88-image-files/system/usr/keychars/Vendor_0508_Product_1980.kcm" /tmp/x88_v51_ur02_kcm
cp -f "${ROOT}/tools/x88-image-files/vendor/usr/keylayout/ffa90030_pwm.kl" /tmp/x88_v36_ffa90030_pwm_kl
cp -f "${ROOT}/tools/x88-image-files/vendor/etc/audio_policy_configuration.xml" /tmp/x88_v47_audio_policy_configuration
cp -f "${ROOT}/tools/x88-image-files/product/app/Gboard/Gboard.apk" /tmp/x88_v49_Gboard.apk
cp -f "${ROOT}/tools/x88-image-files/product/app/LeanKeyboard/base.apk" /tmp/x88_v54_LeanKeyboard_base.apk
cp -f "${ROOT}/tools/x88-image-files/product/app/LeanKeyboard/split_config.en.apk" /tmp/x88_v54_LeanKeyboard_split_config_en.apk
cp -f "${ROOT}/tools/x88-image-files/product/app/LeanKeyboard/split_config.fr.apk" /tmp/x88_v54_LeanKeyboard_split_config_fr.apk
cp -f "${ROOT}/tools/x88-image-files/product/app/LeanKeyboard/split_config.hdpi.apk" /tmp/x88_v54_LeanKeyboard_split_config_hdpi.apk
cp -f "${ROOT}/tools/x88-image-files/product/app/FileManagerPlus/base.apk" /tmp/x88_v54_FileManagerPlus_base.apk
cp -f "${ROOT}/tools/x88-image-files/product/etc/x88/AtvRemoteService-v57-audiopolicy.apk" /tmp/x88_v57_AtvRemoteService_audiopolicy.apk

debugfs -R "dump /build.prop /tmp/x88_v59_vendor_build_prop_base" "${WORK}/vendor.img"
grep -v -E '^(persist\.vendor\.framebuffer\.(main|aux)=|persist\.vendor\.gralloc\.(disable_afbc|no_afbc_for_fb_target_layer)=|codec2_fbc_disable=|sys\.video\.afbc=|vendor\.gralloc\.(disable_afbc|no_afbc_for_fb_target_layer)=|vendor\.hwc\.(enable_rga_policy|vop_max_overlay_4k_plane|smart_scale_enable|enable_composition_drop_mode|cluster_afbc_decode_max_rate|video_buf_cache_max_size)=|debug\.hwc\.enable_prescale_video=)' \
  /tmp/x88_v59_vendor_build_prop_base > /tmp/x88_v59_vendor_build_prop
{
  echo ""
  echo "# X88 4K60 video path experiment"
  echo "sys.video.afbc=1"
  echo "vendor.gralloc.disable_afbc=0"
  echo "persist.vendor.gralloc.disable_afbc=0"
  echo "vendor.gralloc.no_afbc_for_fb_target_layer=0"
  echo "persist.vendor.gralloc.no_afbc_for_fb_target_layer=0"
  echo "vendor.hwc.video_buf_cache_max_size=67108864"
  echo "vendor.hwc.enable_composition_drop_mode=0"
  echo "vendor.hwc.smart_scale_enable=1"
  echo "vendor.hwc.enable_rga_policy=1"
  echo "vendor.hwc.vop_max_overlay_4k_plane=1"
  echo "vendor.hwc.cluster_afbc_decode_max_rate=600000000"
} >> /tmp/x88_v59_vendor_build_prop

debugfs -R "dump /etc/media_codecs_c2_base.xml /tmp/x88_v65_media_codecs_c2_base.xml" "${WORK}/vendor.img"
awk '
  /<MediaCodec name="c2\.rk\.vp9\.decoder"/ { in_vp9 = 1 }
  in_vp9 && /<Limit name="blocks-per-second"/ {
    sub(/range="1-[0-9]+"/, "range=\"1-2000000\"")
  }
  in_vp9 && /performance-point-3840x2160/ {
    sub(/value="[0-9]+"/, "value=\"60\"")
  }
  { print }
  in_vp9 && /<\/MediaCodec>/ { in_vp9 = 0 }
' /tmp/x88_v65_media_codecs_c2_base.xml > /tmp/x88_v65_media_codecs_c2_base_patched.xml

debugfs -R "dump /lib64/libcodec2_rk_component.so /tmp/x88_v72_libcodec2_rk_component.so" "${WORK}/vendor.img"
debugfs -R "dump /lib/libcodec2_rk_component.so /tmp/x88_v72_libcodec2_rk_component_32.so" "${WORK}/vendor.img"
python3 <<'PY'
from pathlib import Path

def apply_patches(path, patches):
    data = bytearray(path.read_bytes())
    for offset, expected, patched in patches:
        actual = bytes(data[offset:offset + len(expected)])
        if actual != expected:
            raise SystemExit(
                f"{path.name} patch guard failed at 0x{offset:x}: "
                f"got {actual.hex(' ')}, expected {expected.hex(' ')}"
            )
        data[offset:offset + len(patched)] = patched
    path.write_bytes(data)

# Force C2RKMpiDec::checkPreferFbcOutput toward MPP FBC. v70 proved this
# has to patch the active 32-bit C2 library; v72 fixes v70's Thumb branch
# target so the final fallback returns through the full stack-canary path.
apply_patches(Path("/tmp/x88_v72_libcodec2_rk_component.so"), [
    (0x28dac, bytes.fromhex("48 01 00 34"), bytes.fromhex("0a 00 00 14")),
    (0x28ddc, bytes.fromhex("88 02 00 34"), bytes.fromhex("14 00 00 14")),
    (0x29010, bytes.fromhex("e1 fe ff f0 02 ff ff 90"), bytes.fromhex("20 00 80 52 7d ff ff 17")),
])
apply_patches(Path("/tmp/x88_v72_libcodec2_rk_component_32.so"), [
    (0x198ac, bytes.fromhex("40 b1"), bytes.fromhex("08 e0")),
    (0x198c4, bytes.fromhex("b0 b1"), bytes.fromhex("16 e0")),
    (0x19a68, bytes.fromhex("15 49 40 f2"), bytes.fromhex("01 20 39 e7")),
])
print("patched 64-bit and active 32-bit libcodec2_rk_component.so with corrected FBC preference")
PY

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
truncate -s 900M "${WORK}/product.img"
resize2fs "${WORK}/product.img"
debugfs -w -f "${PATCH_DIR}/patch_product_v54_tv_keyboard_filemanagerplus.debugfs" "${WORK}/product.img"
e2fsck -fy "${WORK}/product.img" || [ "$?" -eq 1 ]
VENDOR_PATCH="/tmp/x88_v59_vendor_patch.debugfs"
cp -f "${PATCH_DIR}/patch_vendor_v36_rk_display_settings.debugfs" "${VENDOR_PATCH}"
cat >> "${VENDOR_PATCH}" <<'EOF'
cd /
rm build.prop
write /tmp/x88_v59_vendor_build_prop build.prop
sif build.prop mode 0100644
ea_set build.prop security.selinux u:object_r:vendor_file:s0
cd /etc
rm media_codecs_c2_base.xml
write /tmp/x88_v65_media_codecs_c2_base_patched.xml media_codecs_c2_base.xml
sif media_codecs_c2_base.xml mode 0100644
ea_set media_codecs_c2_base.xml security.selinux u:object_r:vendor_configs_file:s0
cd /lib64
rm libcodec2_rk_component.so
write /tmp/x88_v72_libcodec2_rk_component.so libcodec2_rk_component.so
sif libcodec2_rk_component.so mode 0100644
ea_set libcodec2_rk_component.so security.selinux u:object_r:vendor_file:s0
cd /lib
rm libcodec2_rk_component.so
write /tmp/x88_v72_libcodec2_rk_component_32.so libcodec2_rk_component.so
sif libcodec2_rk_component.so mode 0100644
ea_set libcodec2_rk_component.so security.selinux u:object_r:vendor_file:s0
EOF
debugfs -w -f "${VENDOR_PATCH}" "${WORK}/vendor.img"
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
  --partition product:readonly:943718400:rockchip_dynamic_partitions \
  --image product="${WORK}/product.img" \
  --output "${WORK}/super-${TAG}.raw.img"

/usr/bin/img2simg \
  "${WORK}/super-${TAG}.raw.img" \
  "${WORK}/super-${TAG}.img"

"${LPTOOLS}/lpdumps" -i "${WORK}/super-${TAG}.raw.img" > "${WORK}/lpdumps-${TAG}.txt"
ls -lh "${WORK}/system_ext.img" "${WORK}/super-${TAG}.img" "${WORK}/super-${TAG}.raw.img"
