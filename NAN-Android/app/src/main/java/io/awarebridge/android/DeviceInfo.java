package io.awarebridge.android;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.net.wifi.aware.WifiAwareManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class DeviceInfo {
    static byte[] build(Context context) {
        ByteArrayOutputStream txt = new ByteArrayOutputStream();
        for (Map.Entry<String, String> entry : read(context).entrySet()) {
            byte[] record = (entry.getKey() + "=" + entry.getValue()).getBytes(StandardCharsets.UTF_8);
            txt.write(record.length);
            txt.write(record, 0, record.length);
        }
        byte[] txtData = txt.toByteArray();
        ByteArrayOutputStream ssi = new ByteArrayOutputStream();
        ssi.write(0x04);
        // The little-endian length covers TXT bytes only, excluding this header.
        ssi.write(txtData.length & 0xff);
        ssi.write((txtData.length >> 8) & 0xff);
        ssi.write(txtData, 0, txtData.length);
        return ssi.toByteArray();
    }

    @TargetApi(37)
    static byte[] buildBootstrappingMessage(Context context) {
        return WifiAwareManager.createTxtRecordTlvBuffer(read(context));
    }

    @SuppressLint("MissingPermission")
    private static Map<String, String> read(Context context) {
        String deviceName = null;
        try {
            deviceName = Settings.Global.getString(context.getContentResolver(), "device_name");
        } catch (SecurityException e) {
            Log.w("AwareBridge", "Unable to read device name; using model", e);
        }
        if (deviceName == null || deviceName.trim().isEmpty()) {
            deviceName = Build.MODEL;
        }

        Map<String, String> info = new LinkedHashMap<>();
        info.put("modelName", boundedValue(Build.MODEL));
        info.put("pairingName", boundedValue(deviceName));
        info.put("vendorName", boundedValue(Build.MANUFACTURER));
        return info;
    }

    private static String boundedValue(String value) {
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
        return value.substring(0, end);
    }

}
