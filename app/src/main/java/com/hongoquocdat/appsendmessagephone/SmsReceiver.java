package com.hongoquocdat.appsendmessagephone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SmsReceiver extends BroadcastReceiver {
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    private static final String TAG = "SMSReceiver";
    private static final int MAX_HISTORY = 10;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!SMS_RECEIVED.equals(intent.getAction())) return;

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null || pdus.length == 0) return;

        SharedPreferences preferences = context.getSharedPreferences("SMSForwarder", Context.MODE_PRIVATE);
        String sourceName  = preferences.getString("sourceName", "");
        String targetNumber = preferences.getString("targetNumber", "");
        if (sourceName.isEmpty() || targetNumber.isEmpty()) return;

        // Bug #7: Gộp tất cả PDU thành 1 message trước khi xử lý
        String format = bundle.getString("format");
        StringBuilder messageBuilder = new StringBuilder();
        String senderNumber = null;

        for (Object pdu : pdus) {
            SmsMessage sms = createSmsFromPdu((byte[]) pdu, format);
            if (sms == null) continue;
            if (senderNumber == null) senderNumber = sms.getOriginatingAddress();
            messageBuilder.append(sms.getMessageBody());
        }

        if (senderNumber == null) return;
        String message = messageBuilder.toString();

        if (!matchesSender(senderNumber, sourceName)) return;

        String result  = message.replaceAll("SD:.*", "").trim();
        String vndRegex = "-\\d{1,3}(,\\d{3})*VND";

        if (!result.contains("TUYET DOI KHONG CUNG CAP MA XAC NHAN CHO BAT KY AI")
                && !result.matches(".*" + vndRegex + ".*")) {
            forwardSMS(context, targetNumber, result);
            saveToHistory(context, preferences, senderNumber, result);
        }
    }

    // Bug #5: Dùng tham số format để tránh deprecated + hỗ trợ CDMA
    private SmsMessage createSmsFromPdu(byte[] pdu, String format) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && format != null) {
            return SmsMessage.createFromPdu(pdu, format);
        }
        return SmsMessage.createFromPdu(pdu);
    }

    // Bug #1: So sánh có chuẩn hoá (+84 vs 0) và case-insensitive cho shortcode
    private boolean matchesSender(String senderNumber, String source) {
        if (senderNumber == null || source.isEmpty()) return false;
        if (senderNumber.trim().equalsIgnoreCase(source.trim())) return true;
        return normalizePhone(senderNumber).equals(normalizePhone(source));
    }

    private String normalizePhone(String number) {
        String s = number.trim().replaceAll("[\\s\\-()]", "");
        if (s.startsWith("+84")) return "0" + s.substring(3);
        if (s.startsWith("0084")) return "0" + s.substring(4);
        return s;
    }

    private void forwardSMS(Context context, String targetNumber, String message) {
        try {
            // Bug #5: Dùng context.getSystemService cho API 31+
            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = context.getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }
            if (smsManager == null) {
                Log.e(TAG, "Không thể lấy SmsManager");
                return;
            }
            smsManager.sendTextMessage(targetNumber, null, message, null, null);
            Log.d(TAG, "SMS đã được chuyển tiếp thành công");
        } catch (Exception e) {
            Log.e(TAG, "Lỗi khi chuyển tiếp SMS: " + e.getMessage());
        }
    }

    private void saveToHistory(Context context, SharedPreferences prefs, String sender, String message) {
        try {
            // Cập nhật bộ đếm hôm nay
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            String savedDate = prefs.getString("forwardCountDate", "");
            int count = today.equals(savedDate) ? prefs.getInt("forwardCountToday", 0) : 0;

            // Thêm item mới vào đầu danh sách, giữ tối đa MAX_HISTORY items
            JSONArray history;
            try {
                history = new JSONArray(prefs.getString("forwardHistory", "[]"));
            } catch (JSONException e) {
                history = new JSONArray();
            }

            JSONObject newItem = new JSONObject();
            newItem.put("sender", sender);
            newItem.put("time", System.currentTimeMillis());
            // Chỉ lưu tối đa 120 ký tự để tiết kiệm bộ nhớ
            newItem.put("message", message.length() > 120 ? message.substring(0, 120) + "…" : message);

            JSONArray updated = new JSONArray();
            updated.put(newItem);
            for (int i = 0; i < Math.min(history.length(), MAX_HISTORY - 1); i++) {
                updated.put(history.get(i));
            }

            prefs.edit()
                    .putString("forwardHistory", updated.toString())
                    .putString("forwardCountDate", today)
                    .putInt("forwardCountToday", count + 1)
                    .apply();

        } catch (JSONException e) {
            Log.e(TAG, "Lỗi khi lưu lịch sử: " + e.getMessage());
        }
    }
}
