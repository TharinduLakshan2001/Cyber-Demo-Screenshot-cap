package com.demo.photoeditor;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenshotService extends Service {

    public static final String ACTION_SCREENSHOT_CAPTURED = "SCREENSHOT_CAPTURED";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";

    private static final String TAG = "ScreenshotService";
    private static final String CHANNEL_ID = "screenshot_service";
    private static final int NOTIFICATION_ID = 1;
    private static final long CAPTURE_INTERVAL_MS = 5000L;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler handler;

    private int screenWidth;
    private int screenHeight;
    private int densityDpi;
    private int screenshotCount = 0;
    private boolean isRunning = false;

    private final Runnable captureRunnable = new Runnable() {
        @Override
        public void run() {
            takeScreenshot();
            if (isRunning) {
                handler.postDelayed(this, CAPTURE_INTERVAL_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand received");

        if (intent == null) {
            Log.e(TAG, "Null intent, stopping service");
            stopSelf();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent data;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        } else {
            data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        }

        if (resultCode != Activity.RESULT_OK || data == null) {
            Log.e(TAG, "Invalid media projection extras");
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            createMediaProjection(resultCode, data);
            setupScreenCapture();
            isRunning = true;
            handler.postDelayed(captureRunnable, CAPTURE_INTERVAL_MS);
            Log.d(TAG, "Capture loop started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start screen capture", e);
            stopSelf();
        }
        return START_STICKY;
    }

    private void createMediaProjection(int resultCode, Intent data) {
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (projectionManager == null) {
            throw new IllegalStateException("MediaProjectionManager unavailable");
        }
        mediaProjection = projectionManager.getMediaProjection(resultCode, data);
        if (mediaProjection == null) {
            throw new IllegalStateException("MediaProjection is null");
        }
        Log.d(TAG, "MediaProjection created");
    }

    private void setupScreenCapture() {
        WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);

        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        densityDpi = metrics.densityDpi;
        Log.d(TAG, "Screen: " + screenWidth + "x" + screenHeight + " @" + densityDpi + "dpi");

        imageReader = ImageReader.newInstance(
                screenWidth, screenHeight, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                // Frames are consumed periodically via acquireLatestImage()
            }
        }, handler);

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenshotService",
                screenWidth, screenHeight, densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                handler);
        Log.d(TAG, "Virtual display created");
    }

    private void takeScreenshot() {
        if (imageReader == null || !isRunning) {
            Log.w(TAG, "Screenshot skipped: not running or reader is null");
            return;
        }

        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) {
                Log.d(TAG, "No image available, skipping capture");
                return;
            }

            Bitmap bitmap = imageToBitmap(image);
            if (bitmap == null) {
                Log.w(TAG, "Failed to convert image to bitmap");
                return;
            }

            saveBitmap(bitmap);
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }

            screenshotCount++;
            updateNotification();
            broadcastCountUpdate();
            Log.d(TAG, "Screenshot captured. Total: " + screenshotCount);
        } catch (Exception e) {
            Log.e(TAG, "Failed to take screenshot", e);
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) {
            return null;
        }

        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();

        try {
            Bitmap bitmap = Bitmap.createBitmap(
                    image.getWidth() + rowPadding / pixelStride,
                    image.getHeight(),
                    Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            if (rowPadding > 0) {
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, image.getWidth(), image.getHeight());
            }
            return bitmap;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to create bitmap from image", e);
            return null;
        }
    }

    private void saveBitmap(Bitmap bitmap) {
        String fileName = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg";
        OutputStream outputStream = null;
        Uri uri = null;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Screenshots");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);

                uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    Log.e(TAG, "Failed to insert into MediaStore");
                    return;
                }

                outputStream = getContentResolver().openOutputStream(uri);
                if (outputStream == null) {
                    Log.e(TAG, "Failed to open output stream");
                    return;
                }
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream);
                outputStream.close();

                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(uri, values, null, null);
                Log.d(TAG, "Saved via MediaStore: " + uri);
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "Screenshots");
                if (!dir.exists() && !dir.mkdirs()) {
                    Log.e(TAG, "Failed to create directory: " + dir.getAbsolutePath());
                    return;
                }

                File file = new File(dir, fileName);
                outputStream = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream);
                outputStream.close();
                Log.d(TAG, "Saved to: " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save bitmap", e);
            if (uri != null) {
                getContentResolver().delete(uri, null, null);
            }
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to close stream", e);
            }
        }
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name_pro))
                .setContentText(getString(R.string.notification_text, screenshotCount))
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void broadcastCountUpdate() {
        Intent intent = new Intent(ACTION_SCREENSHOT_CAPTURED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.notification_channel_desc));
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        isRunning = false;

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
