#!/bin/bash
# Reverse prepare-nan-monitor.sh on the Ubuntu capture host.
set -euo pipefail
export PATH=/usr/sbin:/usr/bin:/sbin:/bin

if [[ $EUID -ne 0 ]]; then
    echo '请执行 sudo bash /home/mxj/restore-normal-wifi.sh'
    exit 1
fi

for command_name in iw ip nmcli; do
    command -v "$command_name" >/dev/null || {
        echo "缺少命令：$command_name"
        exit 1
    }
done

# Remove only the capture interface created by prepare-nan-monitor.sh.
if iw dev wlp3s0mon info >/dev/null 2>&1; then
    echo '正在关闭并删除 monitor 接口 wlp3s0mon……'
    ip link set wlp3s0mon down
    iw dev wlp3s0mon del
fi

# Normally the preparation script retains wlp3s0; recreate it if absent.
if ! iw dev wlp3s0 info >/dev/null 2>&1; then
    echo '正在重建普通 Wi-Fi 接口 wlp3s0……'
    iw phy phy0 interface add wlp3s0 type managed
elif ! iw dev wlp3s0 info | awk '$1 == "type" { print $2 }' | /usr/bin/grep -qx managed; then
    ip link set wlp3s0 down
    iw dev wlp3s0 set type managed
fi

nmcli radio wifi on
ip link set wlp3s0 up
nmcli device set wlp3s0 managed yes

echo '普通 Wi-Fi 接口已恢复，正在尝试连接已保存的网络……'
if nmcli --wait 30 device connect wlp3s0; then
    echo '已连接 Wi-Fi。'
else
    echo '接口已恢复，但自动连接未成功；请在系统 Wi-Fi 设置中选择网络。'
fi

iw dev wlp3s0 info
nmcli -f DEVICE,TYPE,STATE,CONNECTION device status
printf '\n恢复完成；有线网络未修改。\n'
