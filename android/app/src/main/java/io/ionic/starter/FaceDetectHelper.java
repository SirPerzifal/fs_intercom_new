package io.ionic.starter;

import android.media.MediaPlayer;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.Toast;

import com.arcsoft.face.LivenessInfo;
import com.thinmoo.facerecognition.FaceClient;
import com.thinmoo.facerecognition.FaceParam;
import com.thinmoo.facerecognition.FaceRecognizeType;
import com.thinmoo.facerecognition.FaceRect;
import com.thinmoo.facerecognition.InitLocalFaceCallback;
import com.thinmoo.facerecognition.ParameterHelper;
import com.thinmoo.facerecognition.bdface.BdFaceManager;
import com.thinmoo.facerecognition.facedb.FaceTemplateDao;
import com.thinmoo.facerecognition.facedb.FaceTemplateDom;
import com.thinmoo.facerecognition.utils.DMAppUtils;
import com.thinmoo.facerecognition.utils.SPUtils;
import com.thinmoo.facerecognition.utils.TextUtil;

import com.thinmoo.serial.AccessControlModel;
import io.ionic.starter.DMAccessUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import org.json.JSONException;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;

import io.ionic.starter.handler.runable.Action;
import io.ionic.starter.handler.Run;
import io.ionic.starter.handler.Result;

import io.ionic.starter.LimitQueue;
import io.ionic.starter.DmApplication;
import io.ionic.starter.FaceUtil;
import io.ionic.starter.RecognitionTask;
import io.ionic.starter.LiveDetectTask;


public class FaceDetectHelper {

    private static final int MSG_OPERATING = 100;
    private static final int MSG_RESETCAMERATHREAD = 101;
    private static final int MSG_REGIST_SUCCESS = 102; //注册人脸成功
    private static final int MSG_REGIST_FAIL = 103; //注册人脸失败

    private static final String TAG = "FaceDetectHelper";


    /**
     * is start Face detect
     */
    public static boolean startFaceDetect = false;
    public static boolean isInitializing = false;
    public static int lastInitCode = -999;
    public static String lastInitMsg = "Belum inisialisasi";

    public static boolean operating = false;

    /**
     * excute face methods in one thread
     */
    public ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();
    public ExecutorService recognitionThread = Executors.newSingleThreadExecutor();
    public ExecutorService gendarThread = Executors.newSingleThreadExecutor();
    public ExecutorService liveDetectThread = Executors.newSingleThreadExecutor();
    public ExecutorService registerThread = null;
    public ExecutorService initAndDetectExecutor = Executors.newSingleThreadExecutor();

    /**
     * Register FaceTemplate Queue
     */
    private io.ionic.starter.LimitQueue<FaceTemplateDom> limitQueue = new io.ionic.starter.LimitQueue<FaceTemplateDom>(30001);
    /**
     * user to face FaceTemplateDom
     */
    private FaceTemplateDao faceTemplateDao = new FaceTemplateDao(io.ionic.starter.DmApplication.getInstance());
    private io.ionic.starter.plugin.IntercomPlugin plugin;

    public void setPlugin(io.ionic.starter.plugin.IntercomPlugin plugin) {
        this.plugin = plugin;
    }

    List<FaceRect> mFaceList;
    public volatile int currentFaceCount = 0;

    /**
     * Multicolor camera cache data
     */
    private byte[] cacheMulticolor;
    private long lastRgbUpdateTime = 0;
    private int consecutiveDetectedFrames = 0;
    /**
     * Current Multicolor camera data
     */
    private byte[] curDataMulticolor;

    /**
     * Caching data from infrared camera
     */
    private byte[] cacheDataBlackWhite;
    /**
     * Current data from infrared camera
     */
    private byte[] curDataBlackWhite;

    /**
     * Above the Multicolor camera interface's SurfaceView
     */
    public SurfaceView multicolorSurfaceView;

    /**
     * Above the infrared camera interface's SurfaceView
     */
    private SurfaceView blackSurfaceView;

    /**
     * option
     */
    private boolean threadCameraOption = false;
    /**
     * option for register
     */
    private boolean threadRegisterOption = false;//Registing
    /**
     * option
     */
    private boolean threadIDCardOption = false;
    /**
     * detect
     */
    private boolean threadDetectOption = false;

    private long lastThreadIDCardTime = 0;
    private long lastThreadDetectTime = 0;
    public volatile boolean isForegroundScanning = false;


//    private static class FaceDetectInstanceHolder {
//        private static final FaceDetectHelper INSTANCE = new FaceDetectHelper();
//    }
//    private FaceDetectHelper(){
//
//    }
//
    //    public static FaceDetectHelper getInstance() {
//        return FaceDetectHelper.FaceDetectInstanceHolder.INSTANCE;
//    }

    private static volatile FaceDetectHelper instance;
    private FaceDetectHelper(){};
    public static FaceDetectHelper getInstance(){
        if (instance == null){
            synchronized (FaceDetectHelper.class){
                if (instance == null){
                    instance = new FaceDetectHelper();
                }
            }
        }
        return instance;
    }


    /**
     * Witness identification
     */
    public boolean analysisPicInIDCardInThread(final String identity, final byte[] idPicBuf,final Bitmap bmp) {
        if (!startFaceDetect){
            return false;
        }
        if (operating) {
            Log.e(TAG, "operating...");
            return false;
        }
        if (threadRegisterOption) {
            return false;
        }

        if ((System.currentTimeMillis() - lastThreadIDCardTime) > 5000) {  //超过5秒的，直接强制将threadIDCardOption置为false
            threadIDCardOption = false;
        }

        if (threadIDCardOption) {
            return false;
        }
        threadIDCardOption = true;

        int count = 0;
        while (threadCameraOption){
            try{
                Log.e(TAG, "threadCameraOption:" + threadCameraOption);
                count++;
                Thread.sleep(100);
                if (count > 30){
                    threadCameraOption = false;
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        lastThreadIDCardTime = System.currentTimeMillis();

        singleThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    curDataMulticolor = cacheMulticolor;
                    curDataBlackWhite = cacheDataBlackWhite;
                    if (curDataMulticolor == null) {
                        Log.e(TAG, "No camera data");
                        return;
                    }

                    boolean ret = analysis11Result(identity, idPicBuf, bmp);
                    if (ret) {
                        threadIDCardOption = false;
                        //Success
                        break;
                    } else {
                        threadIDCardOption = false;
                        //Fail
                        Log.e(TAG, "Fece recognize fail...");
                        break;
                    }
                }

            }
        });
        return true;
    }

    boolean option11 = false;

    public boolean analysis11Result(String identity, byte[] idPicBuf,Bitmap bmp) {

        Resources res = io.ionic.starter.DmApplication.getInstance().getResources();

        Log.e(TAG, "template extract start ====>>>");
        if (option11) {
            return false;
        }
        option11 = true;
        Map<String, Object> result = FaceClient.getInstance().verify(identity, idPicBuf,bmp, 100,126,curDataMulticolor, 640, 480);
        int score = 0;
        byte [] imageNV21 = null;
        if (result != null && result.get("score") != null && result.get("imageNV21") != null){
            score = (Integer) result.get("score");
            imageNV21 = (byte[]) result.get("imageNV21");
        }

        Log.e(TAG, "score：" + score + "  getVerity11ScoreLimit:" + ParameterHelper.getVerity11ScoreLimit());
        if (score < ParameterHelper.getVerity11ScoreLimit()) {
            option11 = false;

            return false;
        }
        Log.e(TAG,"analysis Result Certification success");

        //Success
        option11 = false;
        return true;
    }

    public byte[] getCacheMulticolor(){
        return cacheMulticolor;
    }

    public int GetFaceNum(){
      return currentFaceCount;
    }

    int score = 0;
    int faceID = 0;
    /**
     * @param data
     */
    public void setCacheMulticolor(final byte[] data) {
        // Log.d("FDH", "YO CHAT AM I MUTED?");
        cacheMulticolor = data;
        lastRgbUpdateTime = System.currentTimeMillis();

        // logic for background detection using RGB
        if (!operating && !isForegroundScanning) {
            // If we are in background, use lightweight detection instead of full recognition
            triggerBackgroundDetection(data);
            return;
        }

        if (System.currentTimeMillis() - lastThreadDetectTime > 8000 || System.currentTimeMillis() < lastThreadDetectTime){
            threadDetectOption = false;
        }

        if (threadDetectOption){
            return;
        }

        lastThreadDetectTime = System.currentTimeMillis();
        threadDetectOption = true;
        initAndDetectExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (operating) {
                    threadDetectOption = false;
                    return;
                }
                Log.d("FDH", "Mencoba deteksi... isInited: " + FaceClient.getInstance().isInited() + " | Last Init Code: " + lastInitCode + " | Status: " + lastInitMsg);
                if (lastInitCode != 0 && !FaceClient.getInstance().isInited()) {
                    // Log.e("FDH", "FaceClient not initialized properly, aborting detection.");
                    return;
                }
                try{
                    // Frame sudah di-rotate 270 (portrait), maka width=480, height=640
                    // ------------ OLD CODE
                    final List<FaceRect> faceList = FaceClient.getInstance().detect(data, 480, 640);
                    // ------------
                    // final List<FaceRect> faceList = FaceClient.getInstance().detect(data, CameraConfig.width, CameraConfig.height);
                    // ------------
                    currentFaceCount = (faceList != null) ? faceList.size() : 0;
                    // Log.d("FDH", "FACELIST result: " + (faceList != null ? "size=" + faceList.size() : "null"));

                    // if (faceList != null && faceList.size() > 0) {
                    //     Log.e(TAG, "SUPER LOG: Face detected in frame! Count: " + faceList.size());
                    // }

                    if (faceList != null && mFaceList == faceList) {
                        return;
                    }
                    mFaceList = faceList;
                    if (FaceDetectHelper.getInstance().multicolorSurfaceView != null) {
                        io.ionic.starter.handler.Run.onUiSync(new Action() {
                            @Override
                            public void call() {
                                // ------------ OLD CODE
                                io.ionic.starter.FaceUtil.parseFacesToCanvas(FaceDetectHelper.getInstance().multicolorSurfaceView, faceList, 480, 640, "");
                                // ------------
                                // io.ionic.starter.FaceUtil.parseFacesToCanvas(FaceDetectHelper.getInstance().multicolorSurfaceView, faceList, CameraConfig.width, CameraConfig.height, "");
                                // -------------
                            }
                        });
                    }
                    if (faceList != null && faceList.size() > 0){
                        Log.d("FDH", "INSIDE THE FIFTH IF?");
                        //FutureTask<Map<String, Object>> recognitionTask = new FutureTask<>(new io.ionic.starter.RecognitionTask(data, cacheDataBlackWhite, 480, 640));
                        //recognitionThread.execute(recognitionTask);
                        // ------------ OLD CODE
                        RecognitionTask recTask = new RecognitionTask(data, cacheDataBlackWhite, 480, 640);
                        // ------------
                        // RecognitionTask recTask = new RecognitionTask(data, cacheDataBlackWhite, CameraConfig.width, CameraConfig.height);
                        // ------------
                        Map<String, Object> result = recTask.call(); // Langsung dipanggil

                        Log.e(TAG, "SUPER LOG: Checked with local face database. Result: " + (result != null ? result.toString() : "null"));
                        
                        if (result != null && result.get("userID") != null) {
                            score = 0;
                            faceID = 0;
                            score = (int) result.get("score");
                            faceID = TextUtil.getIntFromString((String) result.get("index"), 0);
                            String userID = (String) result.get("userID");
                            
                            Log.e(TAG, "SUPER LOG: Face recognized locally! User ID: " + userID + " | Face ID (Index): " + faceID + " | Score: " + score);
                            
                            // ... kode emitFace dan sendToServer Anda di sini ...
                            if (plugin != null) {
                                plugin.emitFace(userID, (int)result.get("score"));
                            }
                            if (score > 85 && !operating) {
                                sendFaceRecognitionToServer(faceID);
                            }
                        }
                        /*Map<String, Object> result = null;
                        try {
                            // Wait for RecognitionTask to finish calling process() and recognize().
                            // This populates BdFaceManager.liveness and safely generates the result.
                            result = recognitionTask.get();
                        } catch(Exception e) {
                            Log.e("FDH", "Recognition task failed", e);
                        } 

                        if (!livingVerification(data)){
                            Log.d("FDH", "INSIDE THE SIXTH IF MENAING IM CANCELING OR FAILED LIVENESS?");
                            // Already completed, but liveness failed
                        }else {
                            Log.d("FDH", "INSIDE THE FIRST ELSE, MEANING IM RECOGNIZING");
                            if (result != null) {
                                Log.d("FDH", result.toString());
                            } else {
                                Log.e("FDH", "RECOGNITION RESULT IS NULL!");
                            }                           

                            if (result != null && result.get("userID") != null) {
                                // Log.d("FDH", "THE FUCK IM IN RESULTS?");
                                String userID = (String) result.get("userID");
                                byte[] imageIRNV21 = (byte[]) result.get("imageIRNV21");
                                score = 0;
                                faceID = 0;
                                // Log.d("FDH", "what is the score??" + score);
                                // Log.d("FDH", "what is the faceID??" + faceID);
                                if (result.get("score") != null) {
                                    score = (int) result.get("score");
                                }
                                if (result.get("index") != null) {
                                    faceID = TextUtil.getIntFromString((String) result.get("index"), 0);
                                }
                                if (score > 50) {
                                    // Log.e(TAG, "getingg here IF TRUE ---> Face ID" + faceID + " Confidence Sc：" + score);
                                    // handle.post(new Runnable() {
                                    //     @Override
                                    //     public void run() {
                                    //         Toast.makeText(io.ionic.starter.DmApplication.getInstance(), "Face ID：" + faceID + " Confidence Sc：" + score, Toast.LENGTH_SHORT).show();
                                    //     }
                                    // });

                                    sendFaceRecognitionToServer(faceID);
                                    if (plugin != null) {
                                        plugin.emitFace(String.valueOf(faceID), score);
                                    }
                                } else {
                                    Toast.makeText(io.ionic.starter.DmApplication.getInstance(), "Face ID：" + faceID + " Failed Scan With Confidence Score：" + score, Toast.LENGTH_SHORT).show();
                                }
                            }
                        }*/
                    }
                }catch (Exception e){
                    // Log.e("FDH", "Error saat detect wajah: " + e.getMessage(), e);
                    e.printStackTrace();
                }finally {
                    threadDetectOption = false;
                }
            }
        });
    }

    private String getDeviceId() {
        String serial;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                serial = Build.getSerial();
            } catch (SecurityException e) {
                serial = "Permission denied";
            }
        } else {
            serial = Build.SERIAL;
        }
        return serial;
    }

    // Add this method to send face recognition data to server
    private void sendFaceRecognitionToServer(final int faceID) {
        operating = true; // Lock face detection immediately
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    // Your server URL
                    String url_face_recog = "https://ifs360-sg.com/api/face_recog";
                    URL url = new URL(url_face_recog);
                    Log.e(TAG, "SUPER LOG: Preparing to send Face ID: " + faceID + " to API: " + url_face_recog);
                    
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Accept", "application/json");
                    connection.setDoOutput(true);
                    connection.setDoInput(true);
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(10000);

                    // Create JSON payload
//                    JSONObject jsonPayload = new JSONObject();

//                    jsonPayload.put("faceId", faceID);
//                    jsonPayload.put("serial_number", getDeviceId());
//                    jsonPayload.put("timestamp", System.currentTimeMillis());
//                    jsonPayload.put("status", "recognized");

                    String jsonInput = "{"
                        + "\"jsonrpc\": \"2.0\","
                        + "\"params\": {"
                        + "\"faceId\": \"" + faceID + "\","
                        + "\"serial_number\": \"" + getDeviceId() + "\""
                        + "}"
                        + "}";

                    // Send JSON data
                    java.io.OutputStream os = connection.getOutputStream();
                    byte[] input = jsonInput.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                    os.flush();
                    os.close();

                    // Get response
                    int responseCode = connection.getResponseCode();
                    Log.e(TAG, "SUPER LOG: API Response Code for Face ID " + faceID + ": " + responseCode);

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        // Read response
                        java.io.BufferedReader br = new java.io.BufferedReader(
                                new java.io.InputStreamReader(connection.getInputStream(), "utf-8"));
                        StringBuilder response = new StringBuilder();
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                        String responseString = response.toString();
                        Log.e(TAG, "SUPER LOG: API Response Body for Face ID " + faceID + ": " + responseString);
                        try {
                            JSONObject jsonResponse = new JSONObject(responseString);
                            JSONObject root = new JSONObject(responseString);
                            JSONObject result = root.optJSONObject("result");
                            int apiResponseCode = result.optInt("response_code", 0);
                            int SecondsClosingDoor = result.optInt("seconds_closing_door", 0);
                            String errorMessage = result.optString("message", "");
                            long delay = SecondsClosingDoor > 0 ? (long) SecondsClosingDoor : 10000L;
                            if (delay < 8000L) {
                                delay = 8000L; // ensure at least 8 seconds for the green light
                            }
                            final long finalDelay = delay;
                            boolean openDoor = result.optBoolean("open_door", false);
                            new Handler(Looper.getMainLooper()).post(() -> {
                                // Show raw response in logs
                                Log.d(TAG, "SUPER LOG: Value: " + apiResponseCode + ", " + openDoor);

                                // Handle logic based on API response
                                if (apiResponseCode == 200 && openDoor) {
                                    plugin.sendToastMessage("Successfully open the door", true);
                                    Log.d(TAG, "SUPER LOG: Door open");
                                    // Play open door sound
                                    // MediaPlayer openDoorSound = MediaPlayer.create(DmApplication.getInstance(), R.raw.door_open);
                                    // openDoorSound.start();
                                    // openDoorSound.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                    //     @Override
                                    //     public void onCompletion(MediaPlayer mp) {
                                    //         mp.release();
                                    //     }
                                    // });

                                    DMAccessUtil.getInstance().openDoor();

                                    // Light Up Green Light When Opening Door
                                    DMAccessUtil.getInstance().closeRedLed();
                                    DMAccessUtil.getInstance().closeWhiteLed();
                                    AccessControlModel.closeRedLed();
                                    AccessControlModel.closeWhiteLed();
                                    AccessControlModel.openGreenLed();
                                    DMAccessUtil.getInstance().openGreenLed();

                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        Log.d(TAG, "SUPER LOG: Close door");
                                        // MediaPlayer closeDoorSound = MediaPlayer.create(DmApplication.getInstance(), R.raw.);
                                        // closeDoorSound.start();
                                        // closeDoorSound.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                        //     @Override
                                        //     public void onCompletion(MediaPlayer mp) {
                                        //         mp.release();
                                        //     }
                                        // });

                                        DMAccessUtil.getInstance().closeDoor();

                                        // Back to white light when door is closing
                                        DMAccessUtil.getInstance().closeGreenLed();
                                        AccessControlModel.closeGreenLed();
                                        AccessControlModel.closeRedLed();
                                        AccessControlModel.closeWhiteLed();
                                        DMAccessUtil.getInstance().closeRedLed();
                                        DMAccessUtil.getInstance().closeWhiteLed();
                                        
                                        operating = false; // Reset lock when door is closed
                                    }, finalDelay);
                                } else {
                                    if (errorMessage != "") {
                                        plugin.sendToastMessage(errorMessage, false);
                                    } else {
                                        plugin.sendToastMessage("Failed to open the door", false);
                                    }
                                    operating = false; // Reset lock if server decides not to open door
                                }
                            });
                        } catch (JSONException e) {
                            e.printStackTrace();
                            operating = false; // Reset lock on JSON parse error
                        }
                        br.close();
                        Log.d(TAG, "API Response: " + response.toString());

                        // Optional: Show success message
                        // handle.post(new Runnable() {
                        //     @Override
                        //     public void run() {
                        //         Toast.makeText(io.ionic.starter.DmApplication.getInstance(),
                        //                 "Face data sent to server", Toast.LENGTH_SHORT).show();
                        //     }
                        // });
                    } else {
                        Log.e(TAG, "API call failed with code: " + responseCode);
                        operating = false; // Reset lock on API HTTP error
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error sending face data to server", e);
                    operating = false; // Reset lock on generic network error
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        }).start();
    }

    public void setCacheDataBlackWhite(byte[] data) {
        cacheDataBlackWhite = data;

        // If RGB data is active and fresh, we skip IR detection to save ISP resources
        boolean isRgbActive = (System.currentTimeMillis() - lastRgbUpdateTime < 2000);

        // Backup plan: only use IR if RGB is not active/stale
        if (!operating && !isRgbActive) {
            triggerBackgroundDetection(data);
        } else if (operating) {
            // Reset count if we are in operating mode to avoid stale "Approach" triggers
            currentFaceCount = 0;
            consecutiveDetectedFrames = 0;
        }
    }

    public void clearCache() {
        cacheMulticolor = null;
        cacheDataBlackWhite = null;
        curDataMulticolor = null;
        curDataBlackWhite = null;
        mFaceList = null;
        currentFaceCount = 0;
        consecutiveDetectedFrames = 0;
        lastRgbUpdateTime = 0;
    }

    private long lastAutoInitTime = 0;

    private void triggerBackgroundDetection(final byte[] data) {
        if (isForegroundScanning) {
            return; // Skip background tasks if we are actively scanning in foreground
        }
        if (System.currentTimeMillis() - lastThreadDetectTime < 500) {
            return;
        }
        lastThreadDetectTime = System.currentTimeMillis();

        initAndDetectExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (!FaceClient.getInstance().isInited()) {
                    Log.e(TAG, "SUPER LOG: triggerBackgroundDetection aborted - FaceClient NOT inited");
                    
                    // Attempt auto-init if not inited, with a 10s throttle
                    if (System.currentTimeMillis() - lastAutoInitTime > 10000) {
                        lastAutoInitTime = System.currentTimeMillis();
                        Log.e(TAG, "SUPER LOG: Attempting auto-initialization in background thread...");
                        initFaceDetect();
                    }
                    return;
                }
                try {
                    // Using 480x640 for IR as it's now rotated 270 degrees
                    final List<FaceRect> faceList = FaceClient.getInstance().detect(data, 480, 640);
                    int count = (faceList != null) ? faceList.size() : 0;
                    
                    if (count > 0) {
                         consecutiveDetectedFrames++;
                         // Only report face if detected for 2+ consecutive frames to avoid noise/false positives
                         if (consecutiveDetectedFrames >= 2) {
                             currentFaceCount = count;
                             Log.e(TAG, "SUPER LOG: IR Face CONFIRMED! count=" + count);
                               Log.d(TAG, "SUPER LOG: [DETEKSI BACKGROUND] Wajah terkonfirmasi di background! Memulai proses pencocokan. Jumlah wajah terdaftar di memori SDK: " + FaceClient.getInstance().allFaceCount());
                         } else {
                             Log.d(TAG, "SUPER LOG: IR Face detected once, waiting for confirmation...");
                         }
                    } else {
                         consecutiveDetectedFrames = 0;
                         currentFaceCount = 0;
                         // Log.d(TAG, "SUPER LOG: IR No Face");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "SUPER LOG: IR detection error: " + e.getMessage(), e);
                }
            }
        });
    }

    public void setMulticolorSurfaceView(SurfaceView multicolorSurfaceView) {
        this.multicolorSurfaceView = multicolorSurfaceView;
    }

    /**
     * Pause face recognize
     */
    public void sendOperatingMsg(int time){
        operating = true;
        Message msg = handle.obtainMessage(MSG_OPERATING);
        handle.removeMessages(MSG_OPERATING);
        handle.sendMessageDelayed(msg, time*1000);
    }

    /**
     * Pause face recognize
     */
    public void sendOperatingMsg(){
        sendOperatingMsg(6);
    }

    private Handler handle = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case -1:
                    break;
                case MSG_OPERATING:
                    operating = false;
                    break;
                case MSG_RESETCAMERATHREAD:
                    threadCameraOption = false;
                    break;
                case MSG_REGIST_SUCCESS:
                    Toast.makeText(io.ionic.starter.DmApplication.getInstance(), "Register Success", Toast.LENGTH_SHORT).show();
                    break;
                case MSG_REGIST_FAIL:
                    Toast.makeText(io.ionic.starter.DmApplication.getInstance(), "Register Failed", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        }
    };

    public void enableLivenessDetect(Context context){
        Log.e(TAG, "SUPER LOG: Re-enabling liveness detection to ensure SDK caches are populated.");
        SPUtils.put("config_face_support_live_detect", "true", context);
        FaceParam param = new FaceParam();
        param.faceLivenessDetectMode = 2;   // 2 = Enable RGB+IR liveness
        
        // Relaxing quality checks even further
        param.blurLimit = 0.9f;     // Very lenient blur limit
        param.ligthLimit = 0.05f;    // Extremely lenient illumination limit (down from 0.1)
        param.faceMaskDetect = 0;   // Disable mask check
        param.faceThreshold1N = 40; // Lowered from 50 to see if it allows recognition
        
        // Try additional relaxation fields common in this SDK
        // param.occluLimit = 0.95f; 
        // param.yawLimit = 45;
        // param.pitchLimit = 45;
        // param.rollLimit = 45;
        
        Log.e(TAG, "SUPER LOG: Applying FaceParam: liveness=2, blur=0.9, light=0.05, threshold=40, pose=45, occlu=0.95");
        FaceClient.getInstance().setFaceParam(param);
    }

    public boolean initFaceDetect() {
        Log.e(TAG, "SUPER LOG: initFaceDetect() CALLED.");
        
        if (FaceClient.getInstance().isInited()) {
            Log.e(TAG, "SUPER LOG: FaceClient is ALREADY initialized. Skipping.");
            startFaceDetect = true;
            return true;
        }

        if (isInitializing) {
            Log.e(TAG, "SUPER LOG: FaceClient initialization already in progress... waiting.");
            return true;
        }

        isInitializing = true;
        
        try {
            // Log.e(TAG, "SUPER LOG: Main thread initialization starting...");
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(io.ionic.starter.DmApplication.getInstance(), "Face SDK Initializing...", Toast.LENGTH_SHORT).show();
            });
            
            String key = SPUtils.getString("cf_dbf_key", io.ionic.starter.DmApplication.getInstance());
            // Log.e(TAG, "SUPER LOG: Initializing FaceClient with key: " + key);

            int initResult = FaceClient.getInstance().init(io.ionic.starter.DmApplication.getInstance(), null, FaceRecognizeType.BD);
            lastInitCode = initResult;
            Log.e(TAG, "SUPER LOG: FaceClient.init() returned: " + initResult);

            if (initResult == 0) {
                lastInitMsg = "SUCCESS";
                startFaceDetect = true;
                // Log.e(TAG,"SUPER LOG: init success (code 0)");
                
                // Enable liveness detect automatically after success
                enableLivenessDetect(io.ionic.starter.DmApplication.getInstance());
                
                // Load local face templates - this is the heavy part, MUST run in background
                // Log.e(TAG, "SUPER LOG: Loading local face database in background thread...");
                initAndDetectExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        FaceClient.getInstance().loadLocalFace(new InitLocalFaceCallback.InitedFaceCallback() {
                            @Override
                            public void onCompleteLoadFace(int errorCode) {
                                Log.e(TAG, "SUPER LOG: loadLocalFace complete with code: " + errorCode);
                                isInitializing = false;
                                new Handler(Looper.getMainLooper()).post(() -> {
                                     Toast.makeText(io.ionic.starter.DmApplication.getInstance(), "Face SDK Ready", Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    }
                });
            } else {
                lastInitMsg = "FAILED with code: " + initResult;
                Log.e(TAG, "SUPER LOG: init fail with code: " + initResult);
                isInitializing = false;
            }
        } catch (Exception e) {
            lastInitMsg = "EXCEPTION: " + e.getMessage();
            Log.e(TAG, "SUPER LOG: Exception during FaceClient.init(): " + e.getMessage(), e);
            isInitializing = false;
        }
        
        return true;
    }

    /**
     * regist face
     * @param templateDom
     */
    public void registerFromBitmapInThread(FaceTemplateDom templateDom) {
        sendOperatingMsg();

        Log.d(TAG, "Im Runned here");
        if (registerThread == null) {
            Log.d(TAG, "Is it Executing here?");
            registerThread = Executors.newSingleThreadExecutor();
        }
        limitQueue.offer(templateDom);
        if (threadRegisterOption) {
            return;
        }
        threadRegisterOption = true;
        registerThread.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    Log.d(TAG, "Im Executed");
                    FaceTemplateDom curTemplate = limitQueue.poll();
                    try {
                        Log.d(TAG, "cur template?what is that" + curTemplate);
                        if (curTemplate == null) {
                            break;
                        }
                        Bitmap bmp = curTemplate.getBm();
                        Log.d(TAG, "bmp the fuck is that" + bmp );
                        if (bmp == null) {
                            setRegistResult(curTemplate, 0);
                            Thread.sleep(10);
                            continue;
                        }
                        curTemplate.setBm(bmp);

                        while ((lastInitCode != 0 && !FaceClient.getInstance().isInited()) || !FaceClient.getInstance().isLocalFaceLoaded()){
                            try {
                                Log.d(TAG, "im inside here and the thread is sleep");
                                Thread.sleep(5000);
                            }catch (Exception e){
                                Log.d(TAG, "im inside here and the thread is on execption" + e);
                                e.printStackTrace();
                            }
                        }
                        Log.d(TAG, "This is before registartion");
                        FaceClient.getInstance().getFaceRegistResult(curTemplate.getTemplateUrl());

                        boolean ret = FaceClient.getInstance().register(curTemplate);
                        Log.d(TAG, "Registration Result: " + ret);
                        new Handler(Looper.getMainLooper()).post(() -> {
                            Toast.makeText(io.ionic.starter.DmApplication.getInstance(), "Registration Result: " + ret, Toast.LENGTH_SHORT).show();
                        });

                        
                        if (ret) {
                            //success
                            setRegistResult(curTemplate, 1);
                            handle.sendEmptyMessage(MSG_REGIST_SUCCESS);
                        }else{
                            //fail
                            handle.sendEmptyMessage(MSG_REGIST_FAIL);
                        }
                        Thread.sleep(10);
                    } catch (Exception e) {
                        Log.d(TAG, "Error" + e);
                        Log.e(TAG,"exception:" + e.getLocalizedMessage());
                        setRegistResult(curTemplate, 2);
                    }
                }
                threadRegisterOption = false;

                FaceClient.getInstance().loadLocalFace(callback);
            }
        });
    }

    private InitLocalFaceCallback.InitedFaceCallback callback = new InitLocalFaceCallback.InitedFaceCallback() {
        @Override
        public void onCompleteLoadFace(int i) {
            Log.e(TAG, "onCompleteLoadFace " + i);
            sendOperatingMsg(0);
        }
    };

    private int frequencyControl;
    String lastLivenessErrorMsg = null;
    boolean livingVerification(byte[]data){
        long startTime = System.currentTimeMillis();
        Log.e(TAG, "开始活体检测：" + (System.currentTimeMillis() - startTime));
        boolean live = false;
        String liveDetect = SPUtils.getString("config_face_support_live_detect"); //是否需要支持活体检测
        if (liveDetect != null &&"true".equals(liveDetect)) {
            // WE NOW RELY ON RecognitionTask TO HAVE ALREADY CALLED FaceClient.getInstance().process()
            // WHICH SYNCHRONOUSLY POPULATES BdFaceManager.liveness. No need to run a duplicate background task!
            live = BdFaceManager.liveness == 1;
            Log.d(TAG, "singleThreadExecutor ,liveness = " + live);
            if (!live) {
                if (frequencyControl>=3) {//控制一下活体检测失败提示的频率
//                    handle.post(new Runnable() {
//                        @Override
//                        public void run() {
//                            Toast.makeText(DmApplication.getInstance(), "活体检测失败", Toast.LENGTH_SHORT).show();
//                        }
//                    });
                    Log.d(TAG, "活体失败");
                    frequencyControl = 0;
                }
                frequencyControl++;
                threadCameraOption = false;
                ArrayList<FaceClient.RecognitionCallback> callbacks = FaceClient.getInstance().getRecognitionCallbacks();
                if (callbacks != null && callbacks.size() > 0){
                    for (FaceClient.RecognitionCallback callback : callbacks){
                        callback.imageInRecognition(cacheMulticolor, 0, "", "", "非活体");
                    }
                }
                return false;
            }
        }
        frequencyControl = 0;
        Log.e(TAG, "结束活体检测：" + (System.currentTimeMillis() - startTime));
        return true;
    }

    private void setRegistResult(FaceTemplateDom dom, int result){
        if (dom != null){
            dom.setFaceRegistArcSoft_2_0(result);
            faceTemplateDao.saveTemplateUrl(dom);
        }
    }

}
