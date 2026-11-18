#!/bin/bash
# One-time installation on the Ubuntu capture host.
set -euo pipefail
export PATH=/usr/sbin:/usr/bin:/sbin:/bin
[[ $EUID -eq 0 && $# -eq 0 ]] || {
    echo '用法：sudo bash /home/mxj/setup-nan-monitor-control.sh'
    exit 2
}
for bin in iw ip nmcli tcpdump timeout flock visudo pgrep pkill getent; do
    command -v "$bin" >/dev/null
done
getent passwd mxj >/dev/null
for helper in /usr/local/sbin/nan-capture /usr/local/sbin/nan-prepare-monitor; do
    [[ -f "$helper" && ! -L "$helper" && $(stat -c %u "$helper") == 0 ]] || {
        echo "缺少可信的现有辅助程序：$helper"; exit 1;
    }
    [[ -z $(find "$helper" -perm /022 -print) ]] || exit 1
done
install -d -o root -g root -m 0755 /usr/local/sbin
NAN_INSTALL_TMP=$(mktemp -d /run/nan-control-install.XXXXXX)
trap 'rm -rf -- "$NAN_INSTALL_TMP"' EXIT
cat > "$NAN_INSTALL_TMP/control" <<'CONTROL'
#!/bin/bash
set -euo pipefail
export PATH=/usr/sbin:/usr/bin:/sbin:/bin
umask 027
[[ $EUID -eq 0 && $# -ge 1 ]] || exit 2
stop_capture() {
    # Only this interface's fixed tcpdump invocation; no caller-supplied PID.
    local pattern='^tcpdump -Z root -i wlp3s0mon( |$)'
    pkill -INT -f "$pattern" || true
    for _ in 1 2 3 4 5; do
        pgrep -f "$pattern" >/dev/null || return 0
        sleep 1
    done
    pkill -KILL -f "$pattern" || true
}
case "$1" in
    prepare)
        [[ $# -eq 1 ]] || exit 2
        exec /usr/local/sbin/nan-prepare-monitor
        ;;
    capture)
        [[ $# -eq 3 ]] || exit 2
        # The existing root-owned wrapper validates channel and duration.
        exec /usr/local/sbin/nan-capture "$2" "$3"
        ;;
    stop)
        [[ $# -eq 1 ]] || exit 2
        stop_capture
        ;;
    restore)
        [[ $# -eq 1 ]] || exit 2
        stop_capture
        if iw dev wlp3s0mon info >/dev/null 2>&1; then
            ip link set wlp3s0mon down
            iw dev wlp3s0mon del
        fi
        if ! iw dev wlp3s0 info >/dev/null 2>&1; then
            iw phy phy0 interface add wlp3s0 type managed
        elif ! iw dev wlp3s0 info | grep -q 'type managed'; then
            ip link set wlp3s0 down
            iw dev wlp3s0 set type managed
        fi
        nmcli radio wifi on
        ip link set wlp3s0 up
        nmcli device set wlp3s0 managed yes
        nmcli --wait 30 connection up id lenovo-internet ifname wlp3s0
        iw dev
        nmcli -f DEVICE,TYPE,STATE,CONNECTION device status
        ip -4 route show default
        ;;
    status)
        [[ $# -eq 1 ]] || exit 2
        iw dev
        nmcli -f DEVICE,TYPE,STATE,CONNECTION device status
        ip -4 route show default
        ;;
    *) echo '允许操作：prepare | capture <6|149> <10..120秒> | stop | restore | status'; exit 2 ;;
esac
CONTROL
bash -n "$NAN_INSTALL_TMP/control"
printf '%s\n' 'mxj ALL=(root) NOPASSWD: /usr/local/sbin/nan-monitor-control' > "$NAN_INSTALL_TMP/sudoers"
chmod 0440 "$NAN_INSTALL_TMP/sudoers"
visudo -cf "$NAN_INSTALL_TMP/sudoers"
install -o root -g root -m 0755 "$NAN_INSTALL_TMP/control" /usr/local/sbin/nan-monitor-control
install -o root -g root -m 0440 "$NAN_INSTALL_TMP/sudoers" /etc/sudoers.d/nan-monitor-control
echo '固定命令已安装并授权；现在恢复普通 Wi-Fi。以后助手可免密执行这组固定操作。'
/usr/local/sbin/nan-monitor-control restore
