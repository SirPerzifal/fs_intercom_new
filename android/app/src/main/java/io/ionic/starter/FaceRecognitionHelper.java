package io.ionic.starter;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.thinmoo.facerecognition.FaceClient;
import com.thinmoo.facerecognition.FaceRect;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FaceRecognitionHelper {

  private static final String TAG = "FaceRecognitionHelper";

  private final Context context;
  private final Handler mainHandler;
  private final ExecutorService cameraExecutor;

  private RecognitionCallback callback;
  private boolean isRecognizing = false;

  private long lastRecognitionTime = 0;
  private static final long RECOGNITION_INTERVAL = 500;

  private ProcessCameraProvider cameraProvider;
  private Camera camera;

  public interface RecognitionCallback {
    void onFaceDetected(int faceCount);
    void onFaceRecognized(String userId, int score, String status);
    void onNoFaceDetected();
  }

  public FaceRecognitionHelper(Context context) {
    this.context = context;
    this.mainHandler = new Handler(Looper.getMainLooper());
    this.cameraExecutor = Executors.newSingleThreadExecutor();
  }

  public void setRecognitionCallback(RecognitionCallback callback) {
    this.callback = callback;
  }

  // =========================
  // START RECOGNITION (CameraX)
  // =========================
  public void startRecognition() {
    if (isRecognizing) {
      Log.w(TAG, "Recognition already running");
      return;
    }

    isRecognizing = true;
    Log.d(TAG, "STARTING RECOGNITION (CameraX)");

    ListenableFuture<ProcessCameraProvider> future =
      ProcessCameraProvider.getInstance(context);

    future.addListener(() -> {
      try {
        cameraProvider = future.get();
        bindCameraUseCases();
      } catch (Exception e) {
        Log.e(TAG, "Failed to get CameraProvider", e);
      }
    }, ContextCompat.getMainExecutor(context));
  }

  private void bindCameraUseCases() {
    if (cameraProvider == null) return;

    cameraProvider.unbindAll();

    ImageAnalysis analysis =
      new ImageAnalysis.Builder()
        .setBackpressureStrategy(
          ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build();

    analysis.setAnalyzer(cameraExecutor, this::onFrameAvailable);

    camera = cameraProvider.bindToLifecycle(
      new FakeLifecycleOwner(),
      CameraSelector.DEFAULT_FRONT_CAMERA,
      analysis
    );

    Log.d(TAG, "CameraX bound successfully (FRONT CAMERA)");
  }

  // =========================
  // FRAME CALLBACK (CameraX)
  // =========================
  private void onFrameAvailable(@NonNull ImageProxy image) {
    if (!isRecognizing) {
      image.close();
      return;
    }

    long now = System.currentTimeMillis();
    if (now - lastRecognitionTime < RECOGNITION_INTERVAL) {
      image.close();
      return;
    }
    lastRecognitionTime = now;

    // ✅ LOG ROTATION HERE
    int rotation = image.getImageInfo().getRotationDegrees();
    Log.d(TAG, "CameraX rotation = " + rotation);

    int width = image.getWidth();
    int height = image.getHeight();

    // ✅ Convert YUV_420_888 → NV21
    byte[] nv21 = yuv420ToNv21(image);

    // VERY IMPORTANT: close after reading planes
    image.close();

    // 🔥 send to overlay
    FloatingCameraOverlay.updateCameraFrame(nv21, width, height);

    // 🔥 send to face recognition
    processFrameForRecognition(nv21, width, height);
  }

  private static byte[] yuv420ToNv21(ImageProxy image) {
    int width = image.getWidth();
    int height = image.getHeight();

    ImageProxy.PlaneProxy yPlane = image.getPlanes()[0];
    ImageProxy.PlaneProxy uPlane = image.getPlanes()[1];
    ImageProxy.PlaneProxy vPlane = image.getPlanes()[2];

    ByteBuffer yBuffer = yPlane.getBuffer();
    ByteBuffer uBuffer = uPlane.getBuffer();
    ByteBuffer vBuffer = vPlane.getBuffer();

    int ySize = yBuffer.remaining();
    int uSize = uBuffer.remaining();
    int vSize = vBuffer.remaining();

    byte[] nv21 = new byte[ySize + uSize + vSize];

    // Y
    yBuffer.get(nv21, 0, ySize);

    // VU (NV21 format)
    int uvOffset = ySize;
    for (int i = 0; i < vSize; i += 2) {
      nv21[uvOffset++] = vBuffer.get(i);
      nv21[uvOffset++] = uBuffer.get(i);
    }

    return nv21;
  }

  // =========================
  // STOP RECOGNITION
  // =========================
  public void stopRecognition() {
    isRecognizing = false;

    if (cameraProvider != null) {
      cameraProvider.unbindAll();
      cameraProvider = null;
    }

    Log.d(TAG, "CameraX stopped");
  }

  // =========================
  // FACE PROCESSING (UNCHANGED LOGIC)
  // =========================
  private void processFrameForRecognition(byte[] data, int width, int height) {
    new Thread(() -> {
      try {
        List<FaceRect> faceList =
          FaceClient.getInstance().detect(data, width, height);

        if (faceList != null && !faceList.isEmpty()) {
          int faceCount = faceList.size();

          mainHandler.post(() -> {
            if (callback != null) {
              callback.onFaceDetected(faceCount);
            }
          });

          FaceClient.getInstance().process(data, width, height);

          Map<String, Object> result =
            FaceClient.getInstance().recognize(data, width, height);

          if (result != null) {
            String userId = (String) result.get("userID");
            int score = result.get("score") != null
              ? (int) result.get("score")
              : 0;

            String status = score > 80
              ? "recognized"
              : "unknown";

            mainHandler.post(() -> {
              if (callback != null) {
                callback.onFaceRecognized(userId, score, status);
              }
            });
          }

        } else {
          mainHandler.post(() -> {
            if (callback != null) {
              callback.onNoFaceDetected();
            }
          });
        }
      } catch (Exception e) {
        Log.e(TAG, "Error processing frame", e);
      }
    }).start();
  }
}
