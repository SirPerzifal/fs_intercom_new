package io.ionic.starter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Log;

import java.util.List;
import java.util.Map;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;
import org.json.JSONException;

import com.thinmoo.facerecognition.FaceClient;
import com.thinmoo.facerecognition.FaceParam;
import com.thinmoo.facerecognition.FaceRecognizeType;
import com.thinmoo.facerecognition.FaceRect;
import com.thinmoo.facerecognition.InitLocalFaceCallback;
import com.thinmoo.facerecognition.ParameterHelper;
import com.thinmoo.facerecognition.bdface.BdFaceManager;
import com.thinmoo.facerecognition.facedb.FaceTemplateDao;
import com.thinmoo.facerecognition.facedb.FaceTemplateDom;
import com.thinmoo.facerecognition.facedb.ZKFaceDatabaseHelper;
import com.thinmoo.facerecognition.utils.DMAppUtils;
import com.thinmoo.facerecognition.utils.SPUtils;
import com.thinmoo.facerecognition.utils.TextUtil;

import io.ionic.starter.plugin.IntercomPlugin;

public class CameraController {

  private static IntercomPlugin plugin;
  private static Camera camera;
  private boolean isRunning = false;
  private SurfaceTexture previewTexture;
  private static final String TAG = "CameraController";

  public CameraController(Context ctx, IntercomPlugin plugin) {
    this.plugin = plugin;
    // FaceClient init dihilangkan agar tidak bentrok dengan FaceDetectHelper
  }

  private byte[] sharedRotated90Buffer;
  private byte[] sharedRotated180Buffer;
  private byte[] sharedRotated270Buffer;

  private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
    @Override
    public void onPreviewFrame(byte[] data, Camera cam) {
      if (!isRunning) {
          cam.addCallbackBuffer(data);
          return;
      }

      if (!FaceDetectHelper.startFaceDetect || !FaceClient.getInstance().isInited()) {
          cam.addCallbackBuffer(data);
          return;
      }

      int width = 640;
      int height = 480;
      // Berubah dari 270 ke 90 karena posisi gambar terbalik
      byte[] rotatedData = rotateNV21_90(data, width, height);
      
      // We must pass a clone since FDH modifies it asynchronously on a background thread.
      // Passing a shared buffer causes tearing (the camera overwrites it during detection),
      // which crashes or stalls the SDK ("detect exceeded 2.5s").
      FaceDetectHelper.getInstance().setCacheMulticolor(rotatedData.clone());

      // Add the buffer back to the camera queue to prevent XCAM errors and frame drops
      cam.addCallbackBuffer(data);
    }
  };

  private byte[] rotateNV21_90(byte[] data, int imageWidth, int imageHeight) {
      if (data == null) return null;
      int length = imageWidth * imageHeight * 3 / 2;
      if (sharedRotated90Buffer == null || sharedRotated90Buffer.length != length) {
          sharedRotated90Buffer = new byte[length];
      }
      byte[] yuv = sharedRotated90Buffer;
      int k = 0;
      for (int i = 0; i < imageWidth; i++) {
          for (int j = imageHeight - 1; j >= 0; j--) {
              yuv[k++] = data[j * imageWidth + i];
          }
      }

      int wh = imageWidth * imageHeight;
      for (int i = 0; i < imageWidth; i += 2) {
          for (int j = imageHeight / 2 - 1; j >= 0; j--) {
              yuv[k++] = data[wh + j * imageWidth + i];
              yuv[k++] = data[wh + j * imageWidth + i + 1];
          }
      }
      return yuv;
  }

  private byte[] rotateNV21_270(byte[] data, int imageWidth, int imageHeight) {
      if (data == null) return null;
      int length = imageWidth * imageHeight * 3 / 2;
      if (sharedRotated270Buffer == null || sharedRotated270Buffer.length != length) {
          sharedRotated270Buffer = new byte[length];
      }
      byte[] yuv = sharedRotated270Buffer;
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
      return rotateNV21_180(yuv, imageHeight, imageWidth); // width and height swapped after 90 deg rotation
  }

  private byte[] rotateNV21_180(byte[] data, int imageWidth, int imageHeight) {
      if (data == null) return null;
      int length = imageWidth * imageHeight * 3 / 2;
      if (sharedRotated180Buffer == null || sharedRotated180Buffer.length != length) {
          sharedRotated180Buffer = new byte[length];
      }
      byte[] yuv = sharedRotated180Buffer;
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

  public void start() {
    Log.d(TAG, "start() called");
    isRunning = true;

    try {
        if (camera != null) {
            stop();
            isRunning = true;
        }

        // Membuka kamera 0 (biasanya kamera RGB) karena kamera 1 adalah IR di perangkat ini
        try {
            camera = Camera.open(0);
            // Log.d(TAG, "Successfully opened Camera 0 (RGB)");
        } catch (Exception e) {
            // Log.e(TAG, "Failed to open Camera 0, trying Camera 1 as fallback", e);
            camera = Camera.open(1);
        }

        Camera.Parameters params = camera.getParameters();
        params.setPreviewSize(640, 480);
        params.setPreviewFormat(ImageFormat.NV21);
        params.setRotation(270);
        camera.setDisplayOrientation(270);
        camera.setParameters(params);

        // Log.d(TAG, "Camera opened successfully");
        if (previewTexture == null) {
            previewTexture = new SurfaceTexture(10);
        }
        camera.setPreviewTexture(previewTexture);
        
        int dataBufferSize = (int) (480 * 640 * (ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8.0));
        camera.addCallbackBuffer(new byte[dataBufferSize]);
        camera.addCallbackBuffer(new byte[dataBufferSize]);
        camera.setPreviewCallbackWithBuffer(previewCallback);
        
        camera.startPreview();
        isRunning = true;
        // Log.d(TAG, "Camera preview started");

    } catch (Exception e) {
        isRunning = false;
        // Log.e(TAG, "Camera failed to open: " + e.getMessage(), e);
    }
  }

  public void stop() {
    isRunning = false;
    if (camera != null) {
        try {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
        } catch (Exception e) {
            Log.e(TAG, "Error while stopping camera: " + e.getMessage(), e);
        }
        camera = null;
    }
  }

  // openCamera() method is removed as it is now integrated into start()

  private long lastRecognize = 0;
  /* 
  private java.util.concurrent.ExecutorService recogExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
  private volatile boolean isProcessingFrame = false;

  public void processFrame(final byte[] data, final int w, final int h) {
    if (!FaceDetectHelper.startFaceDetect || !FaceClient.getInstance().isInited()) {
        // Log sparingly
        if (System.currentTimeMillis() % 5000 < 50) {
            Log.d(TAG, "processFrame skipped: Face SDK not ready");
        }
        return;
    }

    if (isProcessingFrame) return; // Drop frame if still processing previous one to avoid queue buildup
    
    // Throttle recognition attempts
    if (System.currentTimeMillis() - lastRecognize < 1000) return;

    isProcessingFrame = true;
    recogExecutor.execute(() -> {
        try {
            // 1️⃣ Detect first
            List<FaceRect> faces = FaceClient.getInstance().detect(data, w, h);
            
            if (faces == null || faces.isEmpty()) {
                // Log sparingly if no face is detected by RGB camera
                if (System.currentTimeMillis() % 2000 < 50) {
                    Log.d("ScanningTest", "RGB Camera detect() returned 0 faces");
                }
                return;
            }

            Log.d("ScanningTest", "RGB Camera detected " + faces.size() + " faces!");
            lastRecognize = System.currentTimeMillis();

            // 3️⃣ Recognize
            Map<String, Object> ret = FaceClient.getInstance().recognize(data, w, h);
            if (ret != null) {
              if (ret.get("score") != null) {
                int score = (int) ret.get("score");
                String userId = (String) ret.get("userID");
                
                // Add debug Toast on UI Thread
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    Toast.makeText(io.ionic.starter.DmApplication.getInstance(), 
                        "Face: " + userId + " | Score: " + score, Toast.LENGTH_SHORT).show();
                });

                Log.d("ScanningTest", "Recognition result: " + userId + " score: " + score);
                if (score > 50) {
                  plugin.emitFace(userId, score);
                }
              }
            }
        } finally {
            isProcessingFrame = false;
        }
    });
  }
    */

  public byte[] bitmapToNV21(Bitmap bitmap) {
    int width = bitmap.getWidth();
    int height = bitmap.getHeight();

    int[] argb = new int[width * height];
    bitmap.getPixels(argb, 0, width, 0, 0, width, height);

    byte[] yuv = new byte[width * height * 3 / 2];

    int yIndex = 0;
    int uvIndex = width * height;

    for (int j = 0; j < height; j++) {
      for (int i = 0; i < width; i++) {
        int color = argb[j * width + i];

        int r = (color >> 16) & 0xff;
        int g = (color >> 8) & 0xff;
        int b = color & 0xff;

        int y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
        int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
        int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;

        yuv[yIndex++] = (byte) Math.max(0, Math.min(255, y));

        if (j % 2 == 0 && i % 2 == 0) {
          yuv[uvIndex++] = (byte) Math.max(0, Math.min(255, v));
          yuv[uvIndex++] = (byte) Math.max(0, Math.min(255, u));
        }
      }
    }
    return yuv;
  }

}
