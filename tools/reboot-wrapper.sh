#!/system/bin/sh

LOG=/data/local/tmp/reboot-invocations.log
REAL=/data/local/tmp/reboot.real

{
  echo "===== reboot wrapper $(date) ====="
  echo "argv: $0 $*"
  echo "pid: $$ ppid: $PPID uid: $(id 2>/dev/null)"
  if [ -r "/proc/$PPID/cmdline" ]; then
    printf "parent_cmdline: "
    tr '\000' ' ' < "/proc/$PPID/cmdline"
    echo
  fi
  if [ -r "/proc/$PPID/status" ]; then
    echo "parent_status:"
    sed -n '1,30p' "/proc/$PPID/status"
  fi
  echo "process_snapshot:"
  ps -A -o USER,PID,PPID,NAME,ARGS 2>/dev/null | head -220
  echo
} >> "$LOG" 2>&1

case "$1" in
  --x88-probe)
    exit 0
    ;;
  loader|bootloader|fastboot|recovery)
    exec "$REAL" "$@"
    ;;
esac

if [ "$X88_ALLOW_REBOOT" = "1" ]; then
  exec "$REAL" "$@"
fi

echo "Blocked reboot request with args: $*" >> "$LOG"
exit 0
