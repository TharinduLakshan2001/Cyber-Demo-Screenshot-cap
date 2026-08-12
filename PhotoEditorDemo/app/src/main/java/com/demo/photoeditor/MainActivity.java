package com.demo.photoeditor;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_PERMISSIONS = 100;
    private static final int REQ_SCREEN_CAPTURE = 101;

    private MediaProjectionManager projectionManager;
    private Button btnStart;
    private Button btnStop;
    private TextView tvCounter;

    private int screenshotCount = 0;

    private final BroadcastReceiver screenshotReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ScreenshotService.ACTION_SCREENSHOT_CAPTURED.equals(intent.getAction())) {
                screenshotCount++;
                tvCounter.setText(getString(R.string.counter_format, screenshotCount));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        tvCounter = findViewById(R.id.tvCounter);

        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestScreenCapture();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopProcessing();
            }
        });

        btnStop.setEnabled(false);

        requestPermissionsIfNeeded();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ContextCompat.registerReceiver(
                this,
                screenshotReceiver,
                new IntentFilter(ScreenshotService.ACTION_SCREENSHOT_CAPTURED),
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(screenshotReceiver);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                startProcessing(resultCode, data);
            } else {
                Toast.makeText(this, R.string.msg_permission_denied, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void requestScreenCapture() {
        if (projectionManager == null) {
            Toast.makeText(this, R.string.msg_capture_not_supported, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent captureIntent = projectionManager.createScreenCaptureIntent();
            startActivityForResult(captureIntent, REQ_SCREEN_CAPTURE);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.msg_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void startProcessing(int resultCode, Intent data) {
        Intent serviceIntent = new Intent(this, ScreenshotService.class);
        serviceIntent.putExtra(ScreenshotService.EXTRA_RESULT_CODE, resultCode);
        serviceIntent.putExtra(ScreenshotService.EXTRA_RESULT_DATA, data);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            Toast.makeText(this, R.string.msg_processing_started, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.msg_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void stopProcessing() {
        stopService(new Intent(this, ScreenshotService.class));
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        Toast.makeText(this, R.string.msg_processing_stopped, Toast.LENGTH_SHORT).show();
    }

    private void requestPermissionsIfNeeded() {
        List<String> needed = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfNeeded(needed, Manifest.permission.READ_MEDIA_IMAGES);
            addIfNeeded(needed, Manifest.permission.POST_NOTIFICATIONS);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            addIfNeeded(needed, Manifest.permission.READ_EXTERNAL_STORAGE);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                addIfNeeded(needed, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        if (!needed.isEmpty()) {
            requestPermissions(needed.toArray(new String[0]), REQ_PERMISSIONS);
        }
    }

    private void addIfNeeded(List<String> needed, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            needed.add(permission);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.msg_storage_permission_needed, Toast.LENGTH_LONG).show();
                    break;
                }
            }
        }
    }
}
