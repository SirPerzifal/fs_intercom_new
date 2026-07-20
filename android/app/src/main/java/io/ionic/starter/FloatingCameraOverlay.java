package io.ionic.starter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.hardware.Camera;

import androidx.core.app.NotificationCompat;

import java.io.ByteArrayOutputStream;

public class FloatingCameraOverlay extends Service {

  private static final String TAG = "FloatingOverlay";
  private long lastDetectTime = 0;

  private static FloatingCameraOverlay instance;

  private WindowManager windowManager;
  private ImageView previewView;
  private View rootView;
  private WindowManager.LayoutParams params;
  private Handler uiHandler;
  private Camera camera;

  @Override
  public void onCreate() {
    super.onCreate();

    // 🔥 1. Create notification channel (Android 8+ REQUIRED)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel channel = new NotificationChannel(
        "camera_channel",
        "Camera Service",
        NotificationManager.IMPORTANCE_LOW
      );

      NotificationManager manager = getSystemService(NotificationManager.class);
      if (manager != null) {
        manager.createNotificationChannel(channel);
      }
    }

    // 🔥 2. Create notification
    Notification notification = new NotificationCompat.Builder(this, "camera_channel")
      .setContentTitle("Face Detection Running")
      .setContentText("Camera is active")
      .setSmallIcon(R.mipmap.ic_launcher) // make sure icon exists
      .setOngoing(true)
      .build();

    // 🔥 3. Start foreground IMMEDIATELY
    startForeground(1, notification);

    // 🔥 4. Setup overlay AFTER foreground
    windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

    FrameLayout rootLayout = new FrameLayout(this);

    // Create visible SurfaceView
    SurfaceView surfaceView = new SurfaceView(this);

    FrameLayout.LayoutParams surfaceParams =
      new FrameLayout.LayoutParams(200, 200); // visible size

    // Attach to FaceDetectHelper
   FaceDetectHelper fdh = FaceDetectHelper.getInstance();
   fdh.setMulticolorSurfaceView(surfaceView);

    rootLayout.addView(surfaceView, surfaceParams);

// 🔥 VERY IMPORTANT PART
    surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {

      @Override
      public void surfaceCreated(SurfaceHolder holder) {
        Log.d("Overlay", "Surface CREATED");

        FaceDetectHelper fdh =
          FaceDetectHelper.getInstance();

        try {
          try {
              camera = Camera.open(0); 
              Log.d("Overlay", "Successfully opened Camera 0 (RGB) in Overlay");
          } catch (Exception e) {
              Log.e("Overlay", "Failed to open Camera 0, trying Camera 1 as fallback", e);
              camera = Camera.open(1);
          }

          Camera.Parameters params = camera.getParameters();
          params.setPreviewSize(640, 480);   // MUST match detect(data, 640, 480)
          params.setRotation(270);
          camera.setDisplayOrientation(270);
          camera.setParameters(params);

          camera.setPreviewDisplay(holder);

          camera.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
              long now = System.currentTimeMillis();

              if (now - lastDetectTime > 1000) { // 1 second interval
                lastDetectTime = now;

                Log.d("Overlay", "SENDING DATA TO THE FDH, size: " + (data != null ? data.length : "null"));
                byte[] rotatedData = rotateNV21_270(data, 640, 480);
                FaceDetectHelper.getInstance().setCacheMulticolor(rotatedData);
              }
            }
          });

          camera.startPreview();

          // Start SDK AFTER camera is ready
          Log.d("Overlay", "Starting face detect");
          fdh.initFaceDetect();

        } catch (Exception e) {
          Log.e("Overlay", "Exception inside surfaceCreated: " + e.getMessage(), e);
          e.printStackTrace();
        }
      }

      @Override
      public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
      }

      @Override
      public void surfaceDestroyed(SurfaceHolder holder) {
        if (camera != null) {
          camera.setPreviewCallback(null);
          camera.stopPreview();
          camera.release();
          camera = null;
        }
      }
    });

    // Window overlay size
    WindowManager.LayoutParams params =
      new WindowManager.LayoutParams(
        200,  // width
        200,  // height
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
          WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
          WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT);

    // Position it top-center
    params.gravity = Gravity.TOP | Gravity.END;
    params.x = 0;
    params.y = 100;

    windowManager.addView(rootLayout, params);
  }


  private void makeDraggable() {
    rootView.setOnTouchListener(new View.OnTouchListener() {
      int x, y;
      float touchX, touchY;

      @Override
      public boolean onTouch(View v, MotionEvent e) {
        switch (e.getAction()) {
          case MotionEvent.ACTION_DOWN:
            x = params.x;
            y = params.y;
            touchX = e.getRawX();
            touchY = e.getRawY();
            return true;

          case MotionEvent.ACTION_MOVE:
            params.x = x + (int) (touchX - e.getRawX());
            params.y = y + (int) (e.getRawY() - touchY);
            windowManager.updateViewLayout(rootView, params);
            return true;
        }
        return false;
      }
    });
  }

  // 🔥 THIS IS THE ONLY ENTRY POINT FOR CAMERA FRAMES
  public static void updateCameraFrame(byte[] nv21, int width, int height) {
    if (instance != null) {
      instance.renderFrame(nv21, width, height);
    }
  }

  private void renderFrame(byte[] data, int w, int h) {
    uiHandler.post(() -> {
      try {
        YuvImage yuv = new YuvImage(data, ImageFormat.NV21, w, h, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuv.compressToJpeg(new Rect(0, 0, w, h), 60, out);

        Bitmap bmp = BitmapFactory.decodeByteArray(
          out.toByteArray(), 0, out.size());

        previewView.setImageBitmap(bmp);

      } catch (Exception e) {
        Log.e(TAG, "Frame render failed", e);
      }
    });
  }

  private Notification buildNotification() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel ch = new NotificationChannel(
        "overlay",
        "Camera Overlay",
        NotificationManager.IMPORTANCE_LOW
      );
      getSystemService(NotificationManager.class)
        .createNotificationChannel(ch);

      return new Notification.Builder(this, "overlay")
        .setContentTitle("Camera Running")
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .build();
    }
    return new Notification.Builder(this)
      .setContentTitle("Camera Running")
      .setSmallIcon(android.R.drawable.ic_menu_camera)
      .build();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    instance = null;
    if (rootView != null) windowManager.removeView(rootView);
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  public static void start(Context ctx) {
    Intent i = new Intent(ctx, FloatingCameraOverlay.class);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      ctx.startForegroundService(i);
    } else {
      ctx.startService(i);
    }
  }

  public static void stop(Context ctx) {
    Intent i = new Intent(ctx, FloatingCameraOverlay.class);
    ctx.stopService(i);
  }

  private byte[] rotateNV21_270(byte[] data, int imageWidth, int imageHeight) {
      if (data == null) return null;
      byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
      int wh = imageWidth * imageHeight;
      int uvHeight = imageHeight >> 1;
      int k = 0;
      for (int i = 0; i < imageWidth; i++) {
          int nPos = 0;
          for (int j = 0; j < imageHeight; j++) {
              yuv[k] = data[nPos + i];
              k++;
              nPos += imageWidth;
          }
      }

      for (int i = 0; i < imageWidth; i += 2) {
          int nPos = wh;
          for (int j = 0; j < uvHeight; j++) {
              yuv[k] = data[nPos + i];
              yuv[k + 1] = data[nPos + i + 1];
              k += 2;
              nPos += imageWidth;
          }
      }
      return rotateNV21_180(yuv, imageWidth, imageHeight);
  }

  private byte[] rotateNV21_180(byte[] data, int imageWidth, int imageHeight) {
      if (data == null) return null;
      byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
      int i;
      int count = 0;

      for (i = imageWidth * imageHeight - 1; i >= 0; i--) {
          yuv[count] = data[i];
          count++;
      }

      for (i = imageWidth * imageHeight * 3 / 2 - 1; i >= imageWidth * imageHeight; i -= 2) {
          yuv[count] = data[i - 1];
          count++;
          yuv[count] = data[i];
          count++;
      }
      return yuv;
  }
}
