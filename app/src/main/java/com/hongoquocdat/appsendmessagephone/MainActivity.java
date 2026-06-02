package com.hongoquocdat.appsendmessagephone;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private EditText sourceContactEdit;
    private EditText targetNumberEdit;
    private SharedPreferences preferences;

    // Bug #4: Khai báo đầy đủ 4 permissions cần kiểm tra
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS
    };

    // Bug #2: Thay onActivityResult deprecated bằng ActivityResultLauncher
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        Toast.makeText(this, "Cần cấp đủ quyền để ứng dụng hoạt động", Toast.LENGTH_LONG).show();
                        return;
                    }
                }
            });

    // Bug #2: Thay onActivityResult deprecated bằng ActivityResultLauncher
    private final ActivityResultLauncher<Intent> contactPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    resolveContact(result.getData().getData());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sourceContactEdit = findViewById(R.id.sourceContactEdit);
        targetNumberEdit = findViewById(R.id.targetNumberEdit);
        Button selectContactButton = findViewById(R.id.selectContactButton);
        Button saveButton = findViewById(R.id.saveButton);

        preferences = getSharedPreferences("SMSForwarder", MODE_PRIVATE);
        sourceContactEdit.setText(preferences.getString("sourceName", ""));
        targetNumberEdit.setText(preferences.getString("targetNumber", ""));

        selectContactButton.setOnClickListener(v -> pickContact());
        saveButton.setOnClickListener(v -> saveSettings());

        checkPermissions();
    }

    // Bug #4: Check từng permission riêng lẻ thay vì chỉ check READ_CONTACTS
    private void checkPermissions() {
        boolean anyMissing = false;
        for (String perm : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                anyMissing = true;
                break;
            }
        }
        if (anyMissing) {
            permissionLauncher.launch(REQUIRED_PERMISSIONS);
        }
    }

    private void pickContact() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    // Bug #1 + Bug #6: Lấy số điện thoại (không phải tên) + đóng cursor bằng try-with-resources
    private void resolveContact(Uri contactUri) {
        String[] projection = {ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME};
        try (Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) return;
            int idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID);
            int nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
            if (idIdx < 0 || nameIdx < 0) return;

            String contactId = cursor.getString(idIdx);
            String displayName = cursor.getString(nameIdx);

            // Bug #1: Lấy số điện thoại thực để matching, không dùng tên hiển thị
            String phoneNumber = getFirstPhoneNumber(contactId);
            sourceContactEdit.setText(phoneNumber != null ? phoneNumber : displayName);
        }
    }

    // Bug #6: Dùng try-with-resources để tránh cursor leak
    private String getFirstPhoneNumber(String contactId) {
        String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};
        String selection = ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?";
        try (Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, selection, new String[]{contactId}, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                if (numIdx >= 0) return cursor.getString(numIdx);
            }
        }
        return null;
    }

    private void saveSettings() {
        String sourceName = sourceContactEdit.getText().toString().trim();
        String targetNumber = targetNumberEdit.getText().toString().trim();

        // Bug #3: Validate input trước khi lưu
        if (sourceName.isEmpty()) {
            sourceContactEdit.setError("Vui lòng nhập số/shortcode người gửi");
            return;
        }
        if (targetNumber.isEmpty()) {
            targetNumberEdit.setError("Vui lòng nhập số điện thoại đích");
            return;
        }
        if (!targetNumber.matches("^[+]?[0-9]{9,15}$")) {
            targetNumberEdit.setError("Số điện thoại không hợp lệ (9-15 chữ số)");
            return;
        }

        preferences.edit()
                .putString("sourceName", sourceName)
                .putString("targetNumber", targetNumber)
                .apply();

        // Bug #2: Start service ngay khi lưu, không chờ reboot
        Intent serviceIntent = new Intent(this, SMSForwarderService.class);
        ContextCompat.startForegroundService(this, serviceIntent);

        Toast.makeText(this, "Đã lưu cài đặt", Toast.LENGTH_SHORT).show();
    }
}
