package io.awarebridge.android;
import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

final class DeviceInfo {
    @SuppressLint("MissingPermission")
    static byte[] build(Context context) {
        String deviceName = null;
        try {
            deviceName = Settings.Global.getString(context.getContentResolver(), "device_name");
        } catch (SecurityException e) {
            Log.w("AwareBridge", "Unable to read device name; using model", e);
        }
        if (deviceName == null || deviceName.trim().isEmpty()) {
            deviceName = Build.MODEL;
        }

        ByteArrayOutputStream txt = new ByteArrayOutputStream();
        writeDeviceTxtRecord(txt, "modelName", Build.MODEL);
        writeDeviceTxtRecord(txt, "pairingName", deviceName);
        writeDeviceTxtRecord(txt, "vendorName", Build.MANUFACTURER);
        byte[] txtData = txt.toByteArray();

        ByteArrayOutputStream ssi = new ByteArrayOutputStream();
        ssi.write(0x04);
        // The little-endian length covers TXT bytes only, excluding this header.
        ssi.write(txtData.length & 0xff);
        ssi.write((txtData.length >> 8) & 0xff);
        ssi.write(txtData, 0, txtData.length);
        return ssi.toByteArray();
    }

    private static void writeDeviceTxtRecord(ByteArrayOutputStream txt, String key, String value) {
        if (value == null || value.trim().isEmpty()) {
            value = "Android";
        }
        // Keep names within 63 UTF-8 bytes without splitting a Unicode code point.
        int end = 0;
        int byteLength = 0;
        while (end < value.length()) {
            int next = end + Character.charCount(value.codePointAt(end));
            int size = value.substring(end, next).getBytes(StandardCharsets.UTF_8).length;
            if (byteLength + size > 63) break;
            byteLength += size;
            end = next;
        }
        byte[] record = (key + "=" + value.substring(0, end)).getBytes(StandardCharsets.UTF_8);
        txt.write(record.length);
        txt.write(record, 0, record.length);
    }

}
