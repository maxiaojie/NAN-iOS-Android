#!/usr/bin/env python3
"""Lock a trusted USB iPhone once and verify SpringBoard state (iOS 27.2+).

Run with the Python environment containing pymobiledevice3. An explicit UDID
is required. Already locked devices are left alone; failures never trigger a
second power-button press, which could wake the device again.
"""
import argparse
import asyncio

from pymobiledevice3.remote.core_device.hid_service import (
    HID_BUTTON_STATE_DOWN,
    HID_BUTTON_STATE_UP,
    IndigoHIDService,
)
from pymobiledevice3.remote.rsd_tunnel import PreferredRsdTunnel
from pymobiledevice3.services.notification_proxy import RemoteNotificationProxyService

LOCK_STATE = "com.apple.springboard.lockstate"


async def lock_device(udid):
    async with PreferredRsdTunnel(serial=udid, autopair=False) as rsd:
        async with RemoteNotificationProxyService(rsd) as notifications:
            state = await notifications.notify_get_state(LOCK_STATE)
            if state == 1:
                print("Already locked; no button event sent.")
                return
            if state != 0:
                raise RuntimeError(f"Unknown lock state {state}; no button event sent")
            async with IndigoHIDService(rsd) as hid:
                await hid.send_button(0x0C, 0x30, HID_BUTTON_STATE_DOWN)
                try:
                    await asyncio.sleep(0.05)
                finally:
                    await hid.send_button(0x0C, 0x30, HID_BUTTON_STATE_UP)
                await asyncio.sleep(0.1)
            for _ in range(10):
                await asyncio.sleep(0.3)
                if await notifications.notify_get_state(LOCK_STATE) == 1:
                    print("Locked: SpringBoard lockstate changed from 0 to 1.")
                    return
            raise RuntimeError("Lock was not confirmed; no automatic retry was sent")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("udid", help="USB iPhone UDID (required, no implicit device)")
    args = parser.parse_args()
    asyncio.run(asyncio.wait_for(lock_device(args.udid), timeout=30))
