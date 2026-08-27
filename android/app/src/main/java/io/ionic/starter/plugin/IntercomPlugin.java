package io.ionic.starter.plugin;

import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.content.Context;
import android.media.AudioManager;

import io.ionic.starter.FaceRecognitionHelper;
import io.ionic.starter.FloatingCameraOverlay;
import io.ionic.starter.ScheduledImageService;
import io.ionic.starter.CameraController;
import io.ionic.starter.serialportdemo.ImageApiService;
import io.ionic.starter.FaceDetectHelper;
import io.ionic.starter.DMAccessUtil;
import io.ionic.starter.DmApplication;
import io.ionic.starter.DMFaceIRCameraUtil;
import io.ionic.starter.DMFaceCameraUtil;
import io.ionic.starter.MainActivity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.PluginMethod;


import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import com.thinmoo.facerecognition.FaceClient;
import com.thinmoo.facerecognition.facedb.FaceTemplateDao;
import com.thinmoo.facerecognition.facedb.FaceTemplateDom;
import com.thinmoo.facerecognition.InitLocalFaceCallback;
import com.thinmoo.facerecognition.utils.SPUtils;

import android.os.Handler;
import android.os.Looper;

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.view.Surface;
import android.view.SurfaceView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;
import org.json.JSONException;

@CapacitorPlugin(name = "Intercom",permissions = {
  @Permission(
    alias = "camera",
    strings = { Manifest.permission.CAMERA }
  ),
  @Permission(
    alias = "phone",
    strings = { Manifest.permission.READ_PHONE_STATE }
  )
})
public class IntercomPlugin extends Plugin implements ImageApiService.ImageApiListener{
    private static final String TAG = "IntercomTAG";
    private String sdkKey = null; // Store the key
    private DMAccessUtil sdk;
    private ImageApiService ias;
    private CameraController fdh;
    private ScheduledImageService sic;
    private FaceRecognitionHelper frh;
    private DMFaceIRCameraUtil irCamera; //new modification 24/03/2026
    private DMFaceCameraUtil rgbCamera; //new modification 24/03/2026
    private SurfaceView irSurfaceView;
    private SurfaceView rgbSurfaceView;
    private boolean isApproachDetectionRunning = false;
    private long lastNotificationTime = 0;
    private int lastReportedFaceCount = 0;
    private volatile boolean isAutoScanning = false;
    private final Object scanLock = new Object();

    @PluginMethod
    public void openSettings(PluginCall call) {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            this.getActivity().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error opening settings: " + e.getMessage(), e);
            call.reject("Error opening settings: " + e.getMessage());
        }
    }

    @PluginMethod
    public void openRedLed(PluginCall call) {
        try {
            DMAccessUtil.getInstance().openRedLed();
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error opening Red Led: " + e.getMessage(), e);
            call.reject("Error opening Red Led: " + e.getMessage());
        }
    }

    @PluginMethod
    public void openGreenLed(PluginCall call) {
        try {
            DMAccessUtil.getInstance().openGreenLed();
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error opening Green Led: " + e.getMessage(), e);
            call.reject("Error opening Green Led: " + e.getMessage());
        }
    }

    @PluginMethod
    public void openWhiteLed(PluginCall call) {
        try {
            DMAccessUtil.getInstance().openWhiteLed();
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error opening white Led: " + e.getMessage(), e);
            call.reject("Error opening white Led: " + e.getMessage());
        }
    }

    private void closeLedInternal() {
        try {
            DMAccessUtil.getInstance().closeRedLed();
            DMAccessUtil.getInstance().closeWhiteLed();
            DMAccessUtil.getInstance().closeGreenLed();
        } catch (Exception e) {
            Log.e(TAG, "Error closing Leds: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void closeLed(PluginCall call) {
        try {
            closeLedInternal();
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Error opening Red Led: " + e.getMessage(), e);
            call.reject("Error opening Red Led: " + e.getMessage());
        }
    }



    @Override
    public void load() {
        super.load();
//        FaceDetectHelper.getInstance().initFaceDetect();
//        FaceDetectHelper.getInstance().enableLivenessDetect(getContext());
//        Log.e(TAG, "Exception while waiting for INITFACE DETECT ON LOAD" + FaceDetectHelper.getInstance().initFaceDetect());
        Log.e(TAG, "SUPER LOG: Plugin load() started");
        sdk = DMAccessUtil.getInstance();
        String url_image_api = "https://ifs360-sg.com";
        ias = new ImageApiService(url_image_api, getContext());
        ias.setListener(this);
        Log.e(TAG, "SUPER LOG: ImageApiService initialized, fetching SDK key...");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.e(TAG, "SUPER LOG: Starting delayed fetch of SDK key...");
            fetchSDKKeyFromAPI();
        }, 2000); // 2 second delay

        // FORCING activitySDK for debugging since ImageApiService seems empty
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.e(TAG, "SUPER LOG: Forcing activitySDK() after 3s delay");
            activitySDK();
        }, 3000);
        Log.e(TAG, "SDK initialized");
    }

    @Override
    public void handleOnPause() {
        super.handleOnPause();
        Log.e(TAG, "SUPER LOG: handleOnPause() called. Clearing FaceDetectHelper cache.");
        FaceDetectHelper.getInstance().clearCache();
    }

    @Override
    public void handleOnStop() {
        super.handleOnStop();
        Log.e(TAG, "SUPER LOG: handleOnStop() called. Clearing FaceDetectHelper cache.");
        FaceDetectHelper.getInstance().clearCache();
    }

    @Override
    public void onImageInfoReceived(ImageApiService.ImageInfo imageInfo) {
        getActivity().runOnUiThread(() -> {
            String info = "Image: " + imageInfo.imageName + " (User: " + imageInfo.userId + ")";
            // if (statusText != null) {
            //     statusText.setText(info);
            // }
            Log.d(TAG, "Received image info: " + imageInfo.imageName + " for user: " + imageInfo.userId);
        });
    }

    @Override
    public void onImageReceived(Bitmap image) {
        getActivity().runOnUiThread(() -> {
            // Display the received image
            // if (serverImageView != null) {
            //     serverImageView.setImageBitmap(image);
            // }
            // if (statusText != null) {
            //     statusText.setText("Image received and displayed successfully!");
            // }
            // Toast.makeText(this, "Image received from server!", Toast.LENGTH_SHORT).show();

            // Note: Face registration is now handled in onFaceDataReceived()
            Log.d(TAG, "Image received and displayed");
        });
    }

    @Override
    public void onError(String error) {
        // getActivity().runOnUiThread(() -> {
        //     if (statusText != null) {
        //         statusText.setText("Error: " + error);
        //     }
        //     Toast.makeText(this, "Failed to fetch image: " + error, Toast.LENGTH_LONG).show();
        //     Log.e(TAG, "Image fetch error: " + error);
        // });
    }

    @Override
    public void onRetryAttempt(int attemptNumber, int maxRetries) {
        getActivity().runOnUiThread(() -> {
            // if (statusText != null) {
            //     statusText.setText("Retrying... Attempt " + attemptNumber + " of " + maxRetries);
            // }
            Log.d(TAG, "Retry attempt: " + attemptNumber + "/" + maxRetries);
        });
    }

    @Override
    public void onFaceDataReceived(Bitmap image, String userId, String imageName) {
        getActivity().runOnUiThread(() -> {
            Log.d(TAG, "Received face data for user: " + userId);

            // if (statusText != null) {
            //     statusText.setText("Processing face registration for user: " + userId);
            // }

            // Register the face using the server data
            registerFaceFromServerData(image, userId, imageName);
        });
    }

    @Override
    public void onMultipleFacesReceived(List<ImageApiService.FaceRegistrationData> faceDataList) {
        getActivity().runOnUiThread(() -> {
            Log.d(TAG, "Received " + faceDataList.size() + " faces from server");

            // if (statusText != null) {
            //     statusText.setText("Processing " + faceDataList.size() + " faces from server...");
            // }

            int successCount = 0;
            int errorCount = 0;

            // Process each face
            for (ImageApiService.FaceRegistrationData faceData : faceDataList) {
                try {
                    // Display the first image in the ImageView
                    // if (serverImageView != null && successCount == 0) {
                    //     serverImageView.setImageBitmap(faceData.bitmap);
                    // }

                    // Register each face
                    registerFaceFromServerData(faceData.bitmap, faceData.userId, faceData.imageName);
                    successCount++;

                    Log.d(TAG, "Registered face for user: " + faceData.userId);

                } catch (Exception e) {
                    errorCount++;
                    Log.e(TAG, "Error registering face for user: " + faceData.userId, e);
                }
            }

            // Update UI with results
            String resultMessage = "Processed " + faceDataList.size() + " faces. " +
                    "Success: " + successCount + ", Errors: " + errorCount;

            // if (statusText != null) {
            //     statusText.setText(resultMessage);
            // }

            // Toast.makeText(this, resultMessage, Toast.LENGTH_LONG).show();

            Log.d(TAG, "Face registration batch completed: " + resultMessage);
        });
    }


    private void registerFaceFromServerData(Bitmap bitmap, String userId, String imageName) {
        if (bitmap == null) {
            Log.e(TAG, "Bitmap is null, cannot register face");
            // Toast.makeText(this, "Image data is null", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Starting face registration for user: " + userId);
        Log.d(TAG, "Image dimensions: " + bitmap.getWidth() + "x" + bitmap.getHeight());

        try {
            // Create FaceTemplateDom object
            Log.d(TAG, "Start Face Template Dom ");
            FaceTemplateDom faceTemplateDom = new FaceTemplateDom();
            faceTemplateDom.setBm(bitmap);
            faceTemplateDom.setUserID(userId);
            Log.d(TAG, "Set Bitmap and User ID for Face Template Dom ");


            // Parse index from userId (fallback to 1 if parsing fails)
            int index = 1;
            try {
                // Extract numbers from userId, or use the userId directly if it's numeric
                String numericPart = userId.replaceAll("[^0-9]", "");
                if (!numericPart.isEmpty()) {
                    index = Integer.parseInt(numericPart);
                } else {
                    // If userId is already numeric, use it directly
                    index = Integer.parseInt(userId);
                }
            } catch (NumberFormatException e) {
                Log.w(TAG, "Could not parse index from userId: " + userId + ", using default index: 1");
                index = 1;
            }

            Log.d(TAG, "Before Set Index For FaceTemplateDom ");

            faceTemplateDom.setIndex(index);
            faceTemplateDom.setTemplateUrl("http://server-image.jpg"); // You can customize this URL

            Log.d(TAG, "Before Set Index For FaceTemplateDom ");

            // Register the face using FaceDetectHelper
            //Log.d(TAG, "Face registration started 11 ------- for user: " + userId + " with index: " + index);
            FaceDetectHelper.getInstance().registerFromBitmapInThread(faceTemplateDom);

            // Since the method is void, we assume it started successfully if no exception was thrown
            // Toast.makeText(this, "Registering face for user: " + userId, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Face registration started for user: " + userId + " with index: " + index);

            // if (statusText != null) {
            //     statusText.setText("Face registered for user: " + userId);
            // }

        } catch (Exception e) {
            Log.e(TAG, "Error in face registration for user: " + userId, e);
            //Toast.makeText(this, "Face registration error: " + userId, Toast.LENGTH_LONG).show();

            // if (statusText != null) {
            //     statusText.setText("Error: Face registration failed");
            // }
        }
    }

    @PluginMethod
    public void openGateNative(PluginCall call) {
        Log.d(TAG, "Open Gate Process");
        try {
            sdk.openDoor();

            sdk.closeRedLed();
            sdk.closeWhiteLed();
            sdk.openGreenLed();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    sdk.closeDoor();
                    sdk.closeGreenLed();
                    sdk.closeRedLed();
                    sdk.closeWhiteLed();
                    Log.e(TAG, "Open Gate: door closed automatically after 5s");
                } catch (Exception e) {
                    Log.e(TAG, "Open Gate: error closing door: " + e.getMessage());
                }
            }, 3000);

            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Open gate error: " + e.getMessage());
            call.reject("Open gate error: " + e.getMessage());
        }
    }

    @PluginMethod
    public void closeGateNative(PluginCall call) {
        Log.e(TAG, "trigger here close gate");
        try {
            sdk.closeDoor();
            call.resolve();
        } catch (Exception e) {
            call.reject("Unknown error: " + e.getMessage());
        }
    }

    @PluginMethod
    public void getDatabaseStatus(PluginCall call) {
        try {
            int faceCount = FaceClient.getInstance().allFaceCount();
            long lastSync = SPUtils.getLong("last_sync_time", getContext());

            JSObject ret = new JSObject();
            ret.put("faceCount", faceCount);
            ret.put("lastSyncTime", lastSync);
            ret.put("isInited", FaceClient.getInstance().isInited());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Error getting database status: " + e.getMessage());
        }
    }

    @PluginMethod
    public void syncDatabase(PluginCall call) {
        // Since the method is void, we assume it started successfully if no exception was thrown
        // Toast.makeText(this, "Registering face for user: " + userId, Toast.LENGTH_SHORT).show();
//        while (!FaceClient.getInstance().isInited()) {
//            Thread.sleep(100);
//        }

        Log.d(TAG, "Loading local faces...");
        new Handler(Looper.getMainLooper()).post(() -> {
            FaceClient.getInstance().loadLocalFace(new InitLocalFaceCallback.InitedFaceCallback() {
                @Override
                public void onCompleteLoadFace(int errorCode) {
                    Log.d(TAG, "onCompleteLoadFace called with errorCode: " + errorCode);
                }
            });
        });
        Log.d(TAG, "Loading local faces againnnnnn...");

        // Log.e(TAG, "trigger here fetch data");
        // try {
        //     // Ambil id dari Ionic
        //     String intercomId = call.getString("id");
        //     if (intercomId == null) {
        //         call.reject("Parameter 'id' is required");
        //         return;
        //     }

        //     Log.e(TAG, "Received intercomId: " + intercomId);

        //     // Panggil method dengan id
        //     ias.setIntercomId(intercomId); // atau kalau ias tidak ada setter, bisa bikin method getImageInfoAndDownload(userId)
        //     ias.getImageInfoAndDownload();

        //     call.resolve();
        // } catch (Exception e) {
        //     call.reject("Unknown error: " + e.getMessage());
        // }
    }

    @PluginMethod
    public void startScan(PluginCall call) {
        internalStartScan(call, false);
    }

    private void internalStartScan(PluginCall call, boolean isAuto) {
        Log.d(TAG, "internalStartScan() called. isAuto=" + isAuto);

        synchronized (scanLock) {
            if (isAuto) {
                if (isAutoScanning || fdh != null) {
                    Log.d(TAG, "Auto-scan already in progress or camera already started. Skipping auto-trigger.");
                    return;
                }
                isAutoScanning = true;
            } else {
                if (fdh != null) {
                    Log.d(TAG, "Manual scan requested but camera already started. Stopping previous first.");
                    // Fallthrough to allow manual restart if desired, but usually we should just skip
                    // return;
                }
            }
        }

        // Camera permission
        if (getPermissionState("camera") != PermissionState.GRANTED) {
            if (call != null) call.reject("Camera permission not granted");
            return;
        }

        // Phone permission
        if (getPermissionState("phone") != PermissionState.GRANTED) {
            if (call != null) {
                requestPermissionForAlias("phone", call, "phonePermissionsCallback");
            } else {
                Log.e(TAG, "Phone permission not granted and no PluginCall to request it.");
            }
            return;
        }

        new Thread(() -> {
            try {
                if (!FaceClient.getInstance().isInited()) {
                    Log.d(TAG, "FaceSDK not initialized, calling activitySDK()...");
                    activitySDK();
                }

                int retries = 0;
                while (!FaceClient.getInstance().isInited() && retries < 20) {
                    Log.d(TAG, "Waiting for FaceClient to initialize... " + retries);
                    Thread.sleep(500);
                    retries++;
                }

                if (!FaceClient.getInstance().isInited()) {
                    Log.e(TAG, "FaceClient failed to initialize after waiting.");
                }

                getActivity().runOnUiThread(() -> {
                    try {
                        if (rgbSurfaceView == null) {
                            Log.d(TAG, "Creating SurfaceView for RGB Camera Preview");
                            SurfaceView sv = new SurfaceView(getContext());
                            
                            // Size of camera preview reflection
                            int previewWidth = 400;
                            int previewHeight = 300;
                            
                            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(previewWidth, previewHeight);
                            params.gravity = android.view.Gravity.CENTER;
                            sv.setLayoutParams(params);
                            sv.setZOrderOnTop(true);
                            sv.setZOrderMediaOverlay(true);

                            getActivity().addContentView(sv, params);
                            rgbSurfaceView = sv;
                        } else {
                            rgbSurfaceView.setVisibility(android.view.View.VISIBLE);
                        }

                        FaceDetectHelper.getInstance().clearCache();
                        FaceDetectHelper.getInstance().setPlugin(this);
                        FaceDetectHelper.getInstance().isForegroundScanning = true;

                        if (rgbCamera != null) {
                            rgbCamera.destroy();
                            rgbCamera = null;
                        }

                        Log.d(TAG, "Starting DMFaceCameraUtil with SurfaceView");
                        rgbCamera = new DMFaceCameraUtil(getActivity(), rgbSurfaceView);
                        rgbCamera.show();

                        // Turn on White LED assist
                        DMAccessUtil.getInstance().openWhiteLed();

                        if (call != null) call.resolve();
                        synchronized (scanLock) {
                            if (isAuto) {
                                isAutoScanning = false;
                            }
                            JSObject ret = new JSObject();
                            ret.put("status", "started");
                            notifyListeners("scanStarted", ret);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error starting rgbCamera in startScan", e);
                        if (isAuto) isAutoScanning = false;
                        if (call != null) call.reject("Error starting camera: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error waiting for FaceClient internalStartScan", e);
                if (isAuto) isAutoScanning = false;
                if (call != null) call.reject("Error starting scan");
            }
        }).start();
    }

    @PluginMethod
    public void stopScan(PluginCall call) {
        Log.d(TAG, "stopScan() called from JS");
        getActivity().runOnUiThread(() -> {
            try {
                if (rgbCamera != null) {
                    Log.d(TAG, "Stopping rgbCamera");
                    rgbCamera.destroy();
                    rgbCamera = null;
                }
                if (rgbSurfaceView != null) {
                    android.view.ViewParent parent = rgbSurfaceView.getParent();
                    if (parent instanceof android.view.ViewGroup) {
                        ((android.view.ViewGroup) parent).removeView(rgbSurfaceView);
                    }
                    rgbSurfaceView = null;
                }
                if (fdh != null) {
                    fdh.stop();
                    fdh = null;
                }
                FaceDetectHelper.getInstance().isForegroundScanning = false;
                FaceDetectHelper.getInstance().clearCache();
                closeLedInternal();

                if (call != null) call.resolve();
            } catch (Exception e) {
                Log.e(TAG, "Error in stopScan UI thread", e);
                if (call != null) call.reject(e.getMessage());
            }
        });
    }

    public void emitFace(String userId, int score) {
        Log.d(TAG, "emitFace(): userId=" + userId + ", score=" + score);
        JSObject data = new JSObject();
        data.put("userId", userId);
        data.put("score", score);
        data.put("recognized", true);
        notifyListeners("faceRecognized", data);
    }

    public void sendToastMessage(String message, boolean is_success) {
        Log.d(TAG, "sendToastMessage(): message=" + message + ", is_success=" + is_success);
        JSObject data = new JSObject();
        data.put("message", message);
        data.put("is_success", is_success);
        notifyListeners("sendToastMessage", data);
    }

    @PluginMethod
    public void TestScan(PluginCall call) {
        Log.e(TAG, "SUPER LOG: TestScan() called from JS");

        // Phone permission
        if (getPermissionState("phone") != PermissionState.GRANTED) {
            requestPermissionForAlias("phone", call, "phonePermissionsCallback");
            return;
        }

        new Thread(() -> {
            try {
                if (!FaceClient.getInstance().isInited()) {
                    Log.d(TAG, "FaceSDK not initialized, calling activitySDK()...");
                    activitySDK();
                }

                int retries = 0;
                while (!FaceClient.getInstance().isInited() && retries < 20) {
                    Log.d(TAG, "Waiting for FaceClient to initialize in TestScan... " + retries);
                    Thread.sleep(500);
                    retries++;
                }

                String url_download_test_image = "url_download_test_image";
                URL url = new URL(url_download_test_image);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true);
                conn.connect();

                Bitmap bitmap = BitmapFactory.decodeStream(conn.getInputStream());

                if (bitmap == null) {
                    Log.e(TAG, "Bitmap is null");
                    return;
                }

                if (fdh == null) {
                    fdh = new CameraController(getContext(), this);
                }

                byte[] nv21 = fdh.bitmapToNV21(bitmap);
                FaceDetectHelper.getInstance().setCacheMulticolor(nv21);
                Log.d(TAG, "TestScan: sent nv21 to FaceDetectHelper");

            } catch (Exception e) {
                Log.e(TAG, "TestScan failed", e);
            }
        }).start();

        call.resolve();
    }

    private void fetchSDKKeyFromAPI() {
        Log.d(TAG, "Fetching SDK Key from server");
        ias.getSDKKey();
    }

    private void activitySDK(){
        Log.e(TAG, "SUPER LOG: activitySDK() starting...");
        try {
            // Overriding SDK Key fetched from API for debugging

            if (sdkKey != null && !sdkKey.isEmpty()) {
                SPUtils.put("cf_dbf_key", sdkKey, DmApplication.getInstance());
                Log.d(TAG, "SDK activated with key from API: " + sdkKey);

                // Now initialize face detection after we have the key
                FaceDetectHelper.getInstance().initFaceDetect();
                FaceDetectHelper.getInstance().enableLivenessDetect(DmApplication.getInstance());

                // Auto-start approach detection disabled per client requirement (using on-demand button instead)
                Log.d(TAG, "SDK activated with key from API: " + sdkKey);
            } else {
                Log.e(TAG, "SDK key is null or empty");
            }
        } catch (Throwable t) {
            Log.e(TAG, "CRITICAL ERROR in activitySDK: " + t.getMessage(), t);
        }
    }

    // Implement the new callback method
    @Override
    public void onSDKKeyReceived(String key) {
        getActivity().runOnUiThread(() -> {
            Log.d(TAG, "SDK key received from server: " + key);
            this.sdkKey = key;

            // Now activate the SDK with the received key
            activitySDK();

        });
    }

    @PluginMethod
    public void startRecognition(PluginCall call) {
      Context context = getContext();
      Log.d(TAG, "STARTING FACE DETECT HELPER");

      // Camera permission
      if (getPermissionState("camera") != PermissionState.GRANTED) {
        call.reject("Camera permission not granted");
        return;
      }

      // Phone permission (required for Baidu Face SDK Device ID)
      if (getPermissionState("phone") != PermissionState.GRANTED) {
        requestPermissionForAlias("phone", call, "phonePermissionsCallback");
        return;
      }

      // Overlay permission
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (!Settings.canDrawOverlays(context)) {
          call.reject("Overlay permission not granted");
          return;
        }
      }

      try {
        // 1️⃣ Get FaceDetectHelper instance
        FaceDetectHelper faceDetect = FaceDetectHelper.getInstance();
//        fdh.init(context);

        // 2️⃣ Attach required SurfaceView (can be hidden)
//        SurfaceView surfaceView = new SurfaceView(context);
//        fdh.setMulticolorSurfaceView(surfaceView);

        // 3️⃣ Start face detection (opens Camera1 internally)
//        fdh.initFaceDetect();

        // 4️⃣ Start overlay AFTER camera starts
        FloatingCameraOverlay.start(context);

        // 5️⃣ Poll detection result (SDK-style)
          Handler handler = new Handler(Looper.getMainLooper());
//        handler.postDelayed(() -> {
//          FaceDetectHelper.getInstance().initFaceDetect();
//        }, 500);

        Runnable facePoller = new Runnable() {
          @Override
          public void run() {
            try {
              // 🔍 THIS is the key line — adapt name if needed
              int faceCount = faceDetect.GetFaceNum();
              // or fdh.faceNum if it's a public field

              if (faceCount > 0) {
                // Log.d(TAG, "FACE DETECTED: " + faceCount);
                JSObject ret = new JSObject();
                ret.put("faceCount", faceCount);
                notifyListeners("faceDetected", ret);

                // Auto-trigger startScan if not already running
                if (fdh == null) {
                    Log.d(TAG, "Auto-triggering startScan due to face detection");
                    internalStartScan(null, true);
                }
              } else {
                // Log.d(TAG, "NO FACE DETECTED");
                JSObject ret = new JSObject();
                ret.put("status", "no_face");
                notifyListeners("noFace", ret);
              }

            } catch (Exception e) {
              Log.e(TAG, "Face polling error", e);
            }

            // poll every 300ms
            handler.postDelayed(this, 300);
          }
        };

        handler.post(facePoller);

        call.resolve();

      } catch (Exception e) {
        Log.e(TAG, "Error starting FaceDetectHelper", e);
        call.reject(e.getMessage());
      }
    }

    @PluginMethod
    public void startApproachDetection(PluginCall call) {
        Log.e(TAG, "SUPER LOG: startApproachDetection() entered. call=" + (call == null ? "null" : "not null"));
        getActivity().runOnUiThread(() -> {
            try {
                /*if (irCamera == null) {
                    Log.e(TAG, "SUPER LOG: Creating SurfaceView programmatically for IR");
                    SurfaceView sv = new SurfaceView(getContext());
                    // Hidden 100x100 IR preview for background detection
                    android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(100, 100);
                    params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
                    sv.setLayoutParams(params);
                    sv.setZOrderOnTop(true);
                    sv.setAlpha(0f);
                    sv.setTranslationX(0f); // Geser ke luar layar

                    // Add it directly to the Activity Window instead of the rootView layout
                    getActivity().addContentView(sv, params);

                    irSurfaceView = sv;
                    // Clear cache before starting background detection
                    FaceDetectHelper.getInstance().clearCache();
                    irCamera = new DMFaceIRCameraUtil(getActivity(), irSurfaceView);
                    irCamera.setBackgroundMode(true);
                }*/
                if (rgbCamera == null) {
                    Log.e(TAG, "SUPER LOG: Creating SurfaceView programmatically for RGB");
                    SurfaceView sv2 = new SurfaceView(getContext());
                    // Floating 100x100 RGB preview (initially hidden)
                    android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(200, 150);
                    params.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
                    sv2.setLayoutParams(params);
                    sv2.setZOrderOnTop(true);
                    sv2.setAlpha(0f);
                    sv2.setTranslationX(0f); // Geser ke Dalam layar

                    // Add it directly to the Activity Window instead of the rootView layout
                    getActivity().addContentView(sv2, params);

                    rgbSurfaceView = sv2;
                     FaceDetectHelper.getInstance().clearCache();
                    FaceDetectHelper.getInstance().setPlugin(this);
                    rgbCamera = new DMFaceCameraUtil(getActivity(), rgbSurfaceView);
                    rgbCamera.setBackgroundMode(true);
                }
                // OPTION B: Only start IR Camera initially.
                isRgbPreviewActive = false;
                /*if (irCamera != null) {
                    irCamera.show();
                }*/
                // DO NOT start rgbCamera here to prevent hardware crash
                rgbCamera.show();

                if (!isApproachDetectionRunning) {
                    isApproachDetectionRunning = true;
                    startBackgroundFacePoller();
                }

                if (call != null) call.resolve();
            } catch (Exception e) {
                Log.e(TAG, "Error starting approach detection", e);
                if (call != null) call.reject("Error starting approach detection: " + e.getMessage());
            }
        });
    }

    private boolean isRgbPreviewActive = false;

    private void startBackgroundFacePoller() {
        // Log.e(TAG, "SUPER LOG: Starting background face poller");
        final Handler handler = new Handler(Looper.getMainLooper());
        Runnable facePoller = new Runnable() {
            @Override
            public void run() {
                if (!isApproachDetectionRunning) return;

                try {
                    int faceCount = FaceDetectHelper.getInstance().GetFaceNum();
                    // Simple log for every poll for now to see if it's alive
                    // Log.e(TAG, "SUPER LOG: Background Polling... faceCount=" + faceCount);
                    // io.ionic.starter.DMAccessUtil.getInstance().closeWhiteLed();

                    if (faceCount > 0) {
                        // Throttled Toast Notification
                        io.ionic.starter.DMAccessUtil.getInstance().openWhiteLed();

                        // OPTION B: Switch to RGB Camera
                        if (!isRgbPreviewActive) {
                            isRgbPreviewActive = true;
                            if (irCamera != null) {
                                irCamera.destroy(); // Release IR Camera
                            }

                            // Hardware delay to allow Camera 1 to fully release before grabbing Camera 0
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                if (rgbCamera != null) {
                                    //rgbCamera.show();   // Acquire RGB Camera
                                    getActivity().runOnUiThread(() -> {
                                        if (rgbSurfaceView != null) {
                                            rgbSurfaceView.setTranslationX(0f);
                                            rgbSurfaceView.setAlpha(1f);
                                        }
                                    });
                                }
                            }, 100);
                        }
                        // new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        //     // Back to white light after detect
                        //     io.ionic.starter.DMAccessUtil.getInstance().openWhiteLed();
                        // }, 3000); // 30

                        long now = System.currentTimeMillis();
                        if (lastReportedFaceCount == 0 && (now - lastNotificationTime > 10000)) {
                            getActivity().runOnUiThread(() -> {
                                // Toast.makeText(getContext(), "Face Detected in Background!", Toast.LENGTH_SHORT).show();
                            });
                            lastNotificationTime = now;
                        }

                        lastReportedFaceCount = faceCount;

                        // Log.d(TAG, "BACKGROUND FACE DETECTED: " + faceCount);
                        // Emit event for JS side to know a face is near
                        JSObject data = new JSObject();
                        data.put("faceCount", faceCount);
                        notifyListeners("faceNearby", data);

                        // Promote background detection to foreground recognition
                        FaceDetectHelper.getInstance().isForegroundScanning = true;

                        // Auto-start scan if fdh is null (means main scanner not running)
                        /*if (fdh == null && !isAutoScanning) {
                            Log.i(TAG, "Auto-triggering scan due to background face detection");
                            internalStartScan(null, true);
                        }*/
                    } else {
                        lastReportedFaceCount = 0;
                        io.ionic.starter.DMAccessUtil.getInstance().closeWhiteLed();

                        // Return back to background mode
                        FaceDetectHelper.getInstance().isForegroundScanning = false;

                        // OPTION B: Switch back to IR Camera
                        if (isRgbPreviewActive) {
                            isRgbPreviewActive = false;
                            if (rgbCamera != null) {
                                getActivity().runOnUiThread(() -> {
                                    if (rgbSurfaceView != null) {
                                        rgbSurfaceView.setTranslationX(-5000f);
                                        rgbSurfaceView.setAlpha(0f);
                                    }
                                });
                                //rgbCamera.destroy(); // Release RGB Camera
                            }

                            // Clear cache so IR camera doesn't wait 2 seconds blindly
                            FaceDetectHelper.getInstance().clearCache();

                            // Hardware delay to allow Camera 0 to fully release before grabbing Camera 1
                            /*new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                if (irCamera != null) {
                                    irCamera.show();     // Acquire IR Camera
                                }
                            }, 1500);*/
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Background polling error", e);
                }

                // Poll every 500ms for background detection
                handler.postDelayed(this, 500);
            }
        };
        handler.post(facePoller);
    }

    @PluginMethod
    public void stopApproachDetection(PluginCall call) {
        Log.d(TAG, "stopApproachDetection() called");
        // -------------------------- TEMPORARY COMMENTED
        isApproachDetectionRunning = false;
        // if (irCamera != null) {
        //     irCamera.destroy();
        //     irCamera = null;
        // }
        // --------------------------- NEW INTERCOM
        // if (rgbCamera != null) {
        //     rgbCamera.destroy();
        //     rgbCamera = null;
        // }
        // --------------------------- END LINE
        // if (call != null) call.resolve();

        // --------------------------- FOR NEW INTERCOM USE THIS ONE TEMPORARY
        getActivity().runOnUiThread(() -> {
            try {
                if (irCamera != null) {
                    irCamera.destroy();
                    irCamera = null;
                }
                if (irSurfaceView != null) {
                    android.view.ViewParent parent = irSurfaceView.getParent();
                    if (parent instanceof android.view.ViewGroup) {
                        ((android.view.ViewGroup) parent).removeView(irSurfaceView);
                        closeLedInternal();

                    }
                    irSurfaceView = null;
                }
                // --------------------------- NEW INTERCOM
                if (rgbCamera != null) {
                    rgbCamera.destroy();
                    rgbCamera = null;
                }
                if (rgbSurfaceView != null) {
                    android.view.ViewParent parent = rgbSurfaceView.getParent();
                    if (parent instanceof android.view.ViewGroup) {
                        ((android.view.ViewGroup) parent).removeView(rgbSurfaceView);
                        closeLedInternal();
                    }
                    rgbSurfaceView = null;
                }
                // --------------------------- END LINE
                if (call != null) call.resolve();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping approach detection on UI thread", e);
                if (call != null) call.reject(e.getMessage());
            }
        });

    }

    @PluginMethod
    public void refreshFaceCamera(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            try {
                Log.d(TAG, "Refreshing face camera / approach detection...");

                // Stop approach detection if running

                isApproachDetectionRunning = false;
                if (irCamera != null) {
                    irCamera.destroy();
                    irCamera = null;
                }
                if (irSurfaceView != null) {
                    android.view.ViewParent parent = irSurfaceView.getParent();
                    if (parent instanceof android.view.ViewGroup) {
                        ((android.view.ViewGroup) parent).removeView(irSurfaceView);
                    }
                    irSurfaceView = null;
                }
                if (rgbCamera != null) {
                    rgbCamera.destroy();
                    rgbCamera = null;
                }
                if (rgbSurfaceView != null) {
                    android.view.ViewParent parent = rgbSurfaceView.getParent();
                    if (parent instanceof android.view.ViewGroup) {
                        ((android.view.ViewGroup) parent).removeView(rgbSurfaceView);
                    }
                    rgbSurfaceView = null;
                }

                // Also stop the FloatingCameraOverlay service
                Context context = getContext();
                FloatingCameraOverlay.stop(context);
                DMAccessUtil.getInstance().closeWhiteLed();
                DMAccessUtil.getInstance().closeGreenLed();
                DMAccessUtil.getInstance().closeRedLed();

                // Wait 1.5 seconds for the camera hardware driver to completely release
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        // Restart approach detection
                        startApproachDetection(null);

                        Log.d(TAG, "Face camera / approach detection refreshed successfully!");
                        if (call != null) call.resolve();
                    } catch (Exception e) {
                        Log.e(TAG, "Error restarting approach detection", e);
                        if (call != null) call.reject("Error restarting: " + e.getMessage());
                    }
                }, 1500);

            } catch (Exception e) {
                Log.e(TAG, "Error stopping approach detection during refresh", e);
                if (call != null) call.reject("Error stopping: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void stopRecognition(PluginCall call) {
        if (frh != null) {
            frh.stopRecognition();
        }

        // Stop the overlay
        FloatingCameraOverlay.stop(getContext());
        Log.d(TAG, "OVERLAY STOPPED");

        call.resolve();
    }

    @PluginMethod
    public void showOverlay(PluginCall call) {
        FloatingCameraOverlay.start(getContext());
        Log.e(TAG, "OVERLAY STARTED");
        call.resolve();
    }

    @PluginMethod
    public void checkOverlayPermission(PluginCall call) {
        JSObject ret = new JSObject();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ret.put("granted", Settings.canDrawOverlays(getContext()));
        } else {
            ret.put("granted", true);
        }
        call.resolve(ret);
    }

    @PluginMethod
    public void requestOverlayPermission(PluginCall call) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(getContext())) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getContext().getPackageName()));
                getActivity().startActivity(intent);
                call.resolve();
            } else {
                call.resolve();
            }
        } else {
            call.resolve();
        }
    }

    @PluginMethod
    public void hideOverlay(PluginCall call) {
//        FloatingCameraOverlay.hideImmediately(getContext());
        Log.e(TAG, "FYM HIDE?");
        call.resolve();
    }

    // @PluginMethod
    // public void rebootDevice(PluginCall call) {
    //     Context context = getContext();
    //     try {
    //         Log.d(TAG, "Attempting reboot via PowerManager...");
    //         android.os.PowerManager pm = (android.os.PowerManager) context.getSystemService(Context.POWER_SERVICE);
    //         if (pm != null) {
    //             pm.reboot(null);
    //         }
    //         call.resolve();
    //     } catch (Exception e) {
    //         Log.e(TAG, "PowerManager reboot failed, trying broadcast...", e);
    //         try {
    //             Intent intent = new Intent(Intent.ACTION_REBOOT);
    //             intent.putExtra("nowait", 1);
    //             intent.putExtra("interval", 1);
    //             intent.putExtra("window", 0);
    //             context.sendBroadcast(intent);
    //             call.resolve();
    //         } catch (Exception ex) {
    //             Log.e(TAG, "Broadcast reboot failed, trying shell reboot...", ex);
    //             try {
    //                 Runtime.getRuntime().exec("reboot");
    //                 call.resolve();
    //             } catch (Exception ex2) {
    //                 Log.e(TAG, "Shell reboot failed, trying su reboot...", ex2);
    //                 try {
    //                     Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot"});
    //                     call.resolve();
    //                 } catch (Exception ex3) {
    //                     Log.e(TAG, "All reboot methods failed", ex3);
    //                     call.reject("All reboot methods failed: " + ex3.getMessage());
    //                 }
    //             }
    //         }
    //     }
    // }

    @PluginMethod
    public void restartApp(PluginCall call) {
        try {
            Log.d(TAG, "restartApp called -> Triggering MainActivity app self-restart");
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.performSelfRestart();
                call.resolve();
            } else {
                call.reject("MainActivity instance is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error restarting app via restartApp: " + e.getMessage(), e);
            call.reject("Error restarting app: " + e.getMessage());
        }
    }

    @PermissionCallback
    private void phonePermissionsCallback(PluginCall call) {
        if (getPermissionState("phone") == PermissionState.GRANTED) {
            Log.d(TAG, "Phone permission granted, continuing action.");
            // Determine which method was called and continue
            String method = call.getMethodName();
            if ("startRecognition".equals(method)) {
                startRecognition(call);
            } else if ("startScan".equals(method)) {
                startScan(call);
            } else if ("TestScan".equals(method)) {
                TestScan(call);
            }
        } else {
            Log.e(TAG, "Phone permission DENIED");
            call.reject("Phone permission is required for face detection activation");
        }
    }
}
