package io.ionic.starter;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.widget.Toast;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import java.util.List;

import com.thinmoo.facerecognition.facedb.FaceTemplateDom;
import io.ionic.starter.serialportdemo.ImageApiService;

public class ScheduledImageService extends Service implements ImageApiService.ImageApiListener {
    private static final String TAG = "ScheduledImageService";
    private static final String ACTION_FETCH_IMAGE = "com.thinmoo.dmfacesdkdemo.FETCH_IMAGE";
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable fetchRunnable;

    private ImageApiService apiService;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize API service with your server URL
        String url_image_api = "https://ifs360-sg.com";
        apiService = new ImageApiService(url_image_api, this);
        apiService.setListener(this);

        Log.d(TAG, "ScheduledImageService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_FETCH_IMAGE.equals(intent.getAction())) {
            Log.d(TAG, "Starting scheduled image fetch");
            fetchImageFromServer();
        }

        // return START_NOT_STICKY;
        // Log.d(TAG, "LITERALLY ON COMMAND RAHHHHHHHHHHHH");

        // startForeground(1, createNotification("Synchronizing face data"));
        // startRepeatingTask();

        return START_STICKY;

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


  private void startRepeatingTask() {
    fetchRunnable = new Runnable() {
      @Override
      public void run() {
        Log.d(TAG, "Fetching image every 30 seconds");
        fetchImageFromServer();
        handler.postDelayed(this, 30_000);
      }
    };
    handler.post(fetchRunnable);
  }

    private void fetchImageFromServer() {
        Log.d(TAG, "Fetching image information from server...");
        updateNotification("Download face data...");
        apiService.getImageInfoAndDownload();
    }

    private Notification createNotification(String contentText) {
      Log.d(TAG, "IM STARTING DIGGLET");

      NotificationChannel channel = new NotificationChannel(
        "face_sync",
        "Face Sync Service",
        NotificationManager.IMPORTANCE_LOW
      );

      NotificationManager manager =
        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      manager.createNotificationChannel(channel);

      return new Notification.Builder(this, "face_sync")
        .setContentTitle("Face sync running")
        .setContentText(contentText)
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .build();
    }

    private void updateNotification(String contentText) {
        NotificationManager manager =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(1, createNotification(contentText));
    }
    // Schedule every 15 minutes starting immediately
    public static void scheduleEvery15MinutesImmediate(Context context) {
        Log.d(TAG, "immediately started");
        Log.d(TAG, "SCHEDULED STARTED");
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ImageFetchReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long currentTime = System.currentTimeMillis();
        long interval = 60000;

        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                currentTime,
                interval,
                pendingIntent
        );

        Log.d(TAG, "Scheduled fetch every 15 minutes starting immediately");
    }

    // Cancel the scheduled fetch
    public static void cancelScheduledFetch(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ImageFetchReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pendingIntent);
        Log.d(TAG, "Cancelled scheduled image fetch");
    }

    // ImageApiService.ImageApiListener implementations
    @Override
    public void onImageInfoReceived(ImageApiService.ImageInfo imageInfo) {
        Log.d(TAG, "Image info received: " + imageInfo.imageName + " for user: " + imageInfo.userId);
    }

    // Implement the new callback method
    @Override
    public void onSDKKeyReceived(String key) {
        Log.d(TAG, "SDK key received from server");
    }

    @Override
    public void onImageReceived(Bitmap image) {
        Log.d(TAG, "Image downloaded successfully");
        stopSelf();
    }

    @Override
    public void onError(String error) {
        Log.e(TAG, "Error fetching image: " + error);
        stopSelf();
    }

    @Override
    public void onDestroy() {
      handler.removeCallbacks(fetchRunnable);
      super.onDestroy();
    }

    @Override
    public void onRetryAttempt(int attemptNumber, int maxRetries) {
        Log.d(TAG, "Retry attempt " + attemptNumber + " of " + maxRetries);
    }

    @Override
    public void onFaceDataReceived(Bitmap image, String userId, String imageName) {
        Log.d(TAG, "Face data received for user: " + userId + " (" + imageName + ")");
        registerFaceInBackground(image, userId, imageName);
    }

    @Override
    public void onMultipleFacesReceived(List<ImageApiService.FaceRegistrationData> faceDataList) {
        Log.d(TAG, "Received " + faceDataList.size() + " faces from server");
        updateNotification("Syncronization Finish: " + faceDataList.size() + " face registered");

        handler.post(() -> Toast.makeText(getApplicationContext(), "Finish Synch: " + faceDataList.size() + " face registered", Toast.LENGTH_SHORT).show());

        int successCount = 0;
        int errorCount = 0;

        for (ImageApiService.FaceRegistrationData faceData : faceDataList) {
            try {
                registerFaceInBackground(faceData.bitmap, faceData.userId, faceData.imageName);
                successCount++;
                Log.d(TAG, "Registered face for user: " + faceData.userId);
            } catch (Exception e) {
                errorCount++;
                Log.e(TAG, "Error registering face for user: " + faceData.userId, e);
            }
        }

        Log.d(TAG, "Face registration batch completed. Success: " + successCount + ", Errors: " + errorCount);

        // Save last sync time
        com.thinmoo.facerecognition.utils.SPUtils.put("last_sync_time", System.currentTimeMillis(), getApplicationContext());

        stopSelf();
    }

    private void registerFaceInBackground(Bitmap bitmap, String userId, String imageName) {
        if (bitmap == null) {
            Log.e(TAG, "Bitmap is null, cannot register face");
            return;
        }

        try {
            FaceTemplateDom faceTemplateDom = new FaceTemplateDom();
            faceTemplateDom.setBm(bitmap);
            faceTemplateDom.setUserID(userId);

            int index = 1;
            try {
                String numericPart = userId.replaceAll("[^0-9]", "");
                if (!numericPart.isEmpty()) {
                    index = Integer.parseInt(numericPart);
                } else {
                    index = Integer.parseInt(userId);
                }
            } catch (NumberFormatException e) {
                Log.w(TAG, "Could not parse index from userId: " + userId + ", using default index: 1");
                index = 1;
            }

            faceTemplateDom.setIndex(index);
            faceTemplateDom.setTemplateUrl("http://server-image.jpg");

            FaceDetectHelper.getInstance().registerFromBitmapInThread(faceTemplateDom);
            Log.d(TAG, "Face registration completed for user: " + userId + " with index: " + index);

        } catch (Exception e) {
            Log.e(TAG, "Error in background face registration for user: " + userId, e);
        }
    }


    public static class ImageFetchReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Alarm triggered - starting image fetch service");

            Intent serviceIntent = new Intent(context, ScheduledImageService.class);
            serviceIntent.setAction(ACTION_FETCH_IMAGE);
            context.startService(serviceIntent);
        }
    }
}
