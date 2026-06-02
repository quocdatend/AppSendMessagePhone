package com.hongoquocdat.appsendmessagephone;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS
    };

    // Views — Status card
    private View statusDot;
    private TextView statusText;
    private Button toggleServiceButton;
    private TextView forwardCountText;

    // Views — Config card
    private TextInputLayout sourceInputLayout;
    private TextInputLayout targetInputLayout;
    private TextInputEditText sourceContactEdit;
    private TextInputEditText targetNumberEdit;

    // Views — History card
    private TextView historyEmptyText;
    private RecyclerView historyRecyclerView;
    private HistoryAdapter historyAdapter;

    private SharedPreferences preferences;

    // ActivityResultLaunchers — phải register trong onCreate trước setContentView
    private ActivityResultLauncher<String[]> permissionLauncher;
    private ActivityResultLauncher<Intent> contactPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Đăng ký launchers trước khi inflate layout
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                });

        contactPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        resolveContact(result.getData().getData());
                    }
                });

        setContentView(R.layout.activity_main);
        preferences = getSharedPreferences("SMSForwarder", MODE_PRIVATE);

        setupToolbar();
        bindViews();
        setupRecyclerView();
        loadSavedConfig();
        setupListeners();
        checkPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusCard();
        refreshHistory();
    }

    // ──────────────────────────────────────────────
    // Setup helpers
    // ──────────────────────────────────────────────

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    private void bindViews() {
        statusDot           = findViewById(R.id.statusDot);
        statusText          = findViewById(R.id.statusText);
        toggleServiceButton = findViewById(R.id.toggleServiceButton);
        forwardCountText    = findViewById(R.id.forwardCountText);
        sourceInputLayout   = findViewById(R.id.sourceInputLayout);
        targetInputLayout   = findViewById(R.id.targetInputLayout);
        sourceContactEdit   = findViewById(R.id.sourceContactEdit);
        targetNumberEdit    = findViewById(R.id.targetNumberEdit);
        historyEmptyText    = findViewById(R.id.historyEmptyText);
        historyRecyclerView = findViewById(R.id.historyRecyclerView);
    }

    private void setupRecyclerView() {
        historyAdapter = new HistoryAdapter();
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        historyRecyclerView.setAdapter(historyAdapter);
        historyRecyclerView.addItemDecoration(
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
    }

    private void loadSavedConfig() {
        sourceContactEdit.setText(preferences.getString("sourceName", ""));
        targetNumberEdit.setText(preferences.getString("targetNumber", ""));
    }

    private void setupListeners() {
        ImageButton selectContactButton = findViewById(R.id.selectContactButton);
        Button saveButton               = findViewById(R.id.saveButton);
        Button clearHistoryButton       = findViewById(R.id.clearHistoryButton);

        selectContactButton.setOnClickListener(v -> pickContact());
        saveButton.setOnClickListener(v -> saveSettings());
        toggleServiceButton.setOnClickListener(v -> toggleService());
        clearHistoryButton.setOnClickListener(v -> clearHistory());
    }

    // ──────────────────────────────────────────────
    // Status card
    // ──────────────────────────────────────────────

    private void updateStatusCard() {
        boolean running = preferences.getBoolean("serviceRunning", false);

        // Màu chấm trạng thái
        int color = ContextCompat.getColor(this,
                running ? R.color.status_active : R.color.status_stopped);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        statusDot.setBackground(dot);

        statusText.setText(running ? R.string.status_active : R.string.status_stopped);
        statusText.setTextColor(color);
        toggleServiceButton.setText(running ? R.string.btn_stop_service : R.string.btn_start_service);

        // Đếm tin hôm nay (reset khi sang ngày mới)
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String savedDate = preferences.getString("forwardCountDate", "");
        int count = today.equals(savedDate) ? preferences.getInt("forwardCountToday", 0) : 0;
        forwardCountText.setText(getString(R.string.forwarded_today, count));
    }

    private void toggleService() {
        Intent serviceIntent = new Intent(this, SMSForwarderService.class);
        boolean running = preferences.getBoolean("serviceRunning", false);
        if (running) {
            stopService(serviceIntent);
        } else {
            ContextCompat.startForegroundService(this, serviceIntent);
        }
        // Delay nhỏ để service cập nhật SharedPreferences trước khi đọc lại
        historyRecyclerView.postDelayed(this::updateStatusCard, 300);
    }

    // ──────────────────────────────────────────────
    // Config card
    // ──────────────────────────────────────────────

    private void checkPermissions() {
        boolean anyMissing = false;
        for (String perm : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                anyMissing = true;
                break;
            }
        }
        if (anyMissing) permissionLauncher.launch(REQUIRED_PERMISSIONS);
    }

    private void pickContact() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private void resolveContact(Uri contactUri) {
        String[] projection = {ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME};
        try (Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) return;
            int idIdx   = cursor.getColumnIndex(ContactsContract.Contacts._ID);
            int nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
            if (idIdx < 0 || nameIdx < 0) return;

            String contactId  = cursor.getString(idIdx);
            String displayName = cursor.getString(nameIdx);
            String phone      = getFirstPhoneNumber(contactId);
            // Điền số điện thoại (dùng để matching), fallback sang tên nếu không có số
            sourceContactEdit.setText(phone != null ? phone : displayName);
        }
    }

    private String getFirstPhoneNumber(String contactId) {
        String[] projection = {ContactsContract.CommonDataKinds.Phone.NUMBER};
        String selection    = ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?";
        try (Cursor cursor  = getContentResolver().query(
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
        String sourceName   = sourceContactEdit.getText() != null
                ? sourceContactEdit.getText().toString().trim() : "";
        String targetNumber = targetNumberEdit.getText() != null
                ? targetNumberEdit.getText().toString().trim() : "";

        sourceInputLayout.setError(null);
        targetInputLayout.setError(null);

        if (sourceName.isEmpty()) {
            sourceInputLayout.setError(getString(R.string.error_source_empty));
            return;
        }
        if (targetNumber.isEmpty()) {
            targetInputLayout.setError(getString(R.string.error_target_empty));
            return;
        }
        if (!targetNumber.matches("^[+]?[0-9]{9,15}$")) {
            targetInputLayout.setError(getString(R.string.error_target_invalid));
            return;
        }

        preferences.edit()
                .putString("sourceName", sourceName)
                .putString("targetNumber", targetNumber)
                .apply();

        Intent serviceIntent = new Intent(this, SMSForwarderService.class);
        ContextCompat.startForegroundService(this, serviceIntent);

        Toast.makeText(this, getString(R.string.save_success), Toast.LENGTH_SHORT).show();
        historyRecyclerView.postDelayed(this::updateStatusCard, 300);
    }

    // ──────────────────────────────────────────────
    // History card
    // ──────────────────────────────────────────────

    private void refreshHistory() {
        List<HistoryAdapter.HistoryItem> items = loadHistory();
        historyAdapter.updateItems(items);
        boolean empty = items.isEmpty();
        historyEmptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        historyRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private List<HistoryAdapter.HistoryItem> loadHistory() {
        List<HistoryAdapter.HistoryItem> items = new ArrayList<>();
        String json = preferences.getString("forwardHistory", "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                items.add(new HistoryAdapter.HistoryItem(
                        obj.getString("sender"),
                        obj.getLong("time"),
                        obj.getString("message")));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Lỗi khi load lịch sử: " + e.getMessage());
        }
        return items;
    }

    private void clearHistory() {
        preferences.edit()
                .remove("forwardHistory")
                .remove("forwardCountToday")
                .remove("forwardCountDate")
                .apply();
        refreshHistory();
        updateStatusCard();
    }
}
