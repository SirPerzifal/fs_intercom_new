package io.ionic.starter;

import android.media.MediaPlayer;

import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;
import android.util.Log;
import com.getcapacitor.Plugin;
import android.content.Context;
import io.ionic.starter.plugin.IntercomPlugin;
import io.ionic.starter.plugin.RingtonePlugin;
import io.ionic.starter.serialportdemo.DMByteUtils;
import io.ionic.starter.serialportdemo.SerialPortBaseActivity;
import io.ionic.starter.DMAccessUtil;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.Message;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;
import org.json.JSONException;
import com.thinmoo.serial.AccessControlModel;
import com.thinmoo.serial.VerifyCardState;
import com.thinmoo.serial.ACCallBack;
import com.thinmoo.serial.ACCallBack.ACCardMsgListener;
import com.thinmoo.serial.ACCallBack.ACStateListener;
import com.thinmoo.serial.SensorState;
import com.thinmoo.serial.LockState;
import com.thinmoo.serial.SerialPortEntity;
import java.util.Timer;
import java.util.TimerTask;

// Add missing imports:
import java.io.InputStream;
import android.widget.Switch;
import android.widget.ScrollView;
import android.app.PendingIntent;
import android.app.AlarmManager;
import android.widget.Button;
import android.graphics.Bitmap;
import java.util.List;

import io.ionic.starter.serialportdemo.ImageApiService;




// public class MainActivity extends BridgeActivity implements ImageApiService.ImageApiListener {
public class MainActivity extends BridgeActivity{
    private static final String TAG = "MainActivity";
    private String sdkKey = null; // Store the key
    private final int MSG_RQCODE = 2;
    private final int MSG_CLOSE_DOOR = 3;
    private final int MSG_WATCHDOG = 4;
    private boolean needAuto2Bottom = true;
    private Handler uiHandler;

    private Switch switch_watchDog;
    private Button temperatureBtn;

    protected String qrcodeCache = "";
    private static final int WHAT_NEED_AUTO_2_BOTTOM = 1;

    // API service
    private ImageApiService apiService;


    private ACCardMsgListener msgListener = new ACCardMsgListener() {
        @Override
        public void onACCardNumMsgReceived(String cardNum, String s1, VerifyCardState verifyState) {
            String originalCard = cardNum;
            String swappedCard = reverseHex(cardNum);
            Log.d(TAG, "cardNum Original=" + originalCard + " Swapped=" + swappedCard);

            // Visual feedback
            new Handler(Looper.getMainLooper()).post(() -> {
                // android.widget.Toast.makeText(MainActivity.this, "Card: " + swappedCard, android.widget.Toast.LENGTH_SHORT).show();
            });
            sendCardToBackend(swappedCard);
        }

        @Override
        public void onACCardNumMsgAndSectorInfoReceived(String cardNum, String s1, VerifyCardState verifyState, String s2) {
            String swappedCard = reverseHex(cardNum);

            Log.d(TAG, "cardNum (S) Original=" + cardNum);
            Log.d(TAG, "cardNum (S) Swapped=" + swappedCard);
            new Handler(Looper.getMainLooper()).post(() -> {
                // android.widget.Toast.makeText(MainActivity.this, "Card (S): " + swappedCard, android.widget.Toast.LENGTH_SHORT).show();
            });
            sendCardToBackend(swappedCard);
        }
    };

    private ACStateListener stateListener = new ACStateListener() {
        @Override
        public void onACSensorChanged(SensorState sensorState) {
            Log.d(TAG, "Sensor changed: " + sensorState);
        }

        @Override
        public void onACLockChanged(LockState lockState) {
            Log.d(TAG, "Lock changed: " + lockState);
        }

        @Override
        public void onACLockOpenTimeOut(String s) {
            Log.d(TAG, "Lock open timeout: " + s);
        }

        @Override
        public void onDoorSwitchOpen() {
            Log.d(TAG, "onDoorSwitchOpen: BUT and GND short circuit detected! Opening door.");

            // Give green LED feedback
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    DMAccessUtil.getInstance().openDoor();
                    AccessControlModel.closeRedLed();
                    AccessControlModel.closeWhiteLed();
                    AccessControlModel.openGreenLed();
                    DMAccessUtil.getInstance().closeRedLed();
                    DMAccessUtil.getInstance().closeWhiteLed();
                    DMAccessUtil.getInstance().openGreenLed();
                } catch (Exception e) {
                    Log.w(TAG, "Failed to set LEDs on door switch open: " + e.getMessage());
                }

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        DMAccessUtil.getInstance().closeDoor();
                        AccessControlModel.closeGreenLed();
                        AccessControlModel.closeRedLed();
                        AccessControlModel.closeWhiteLed();
                        DMAccessUtil.getInstance().closeGreenLed();
                        DMAccessUtil.getInstance().closeRedLed();
                        DMAccessUtil.getInstance().closeWhiteLed();
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to close door after delay: " + e.getMessage());
                    }
                }, 5000);
            });
        }
    };

    public String reverseHex(String cardNum) {
        try {
           // 1. Convert decimal string to long
            long value = Long.parseLong(cardNum);

            // 2. Convert to Hex string (8 chars for 4 bytes)
            String hex = String.format("%08X", value);

            // 3. Reverse Hex per 2 characters (per byte)
            // From: [B1][B2][B3][B4] -> To: [B4][B3][B2][B1]
            StringBuilder sbReverse = new StringBuilder();
            for (int i = hex.length() - 2; i >= 0; i -= 2) {
                sbReverse.append(hex.substring(i, i + 2));
            }

            // 4. Convert reversed hex back to decimal long
            long reversedValue = Long.parseLong(sbReverse.toString(), 16);

            // 5. Return as decimal string
            return String.valueOf(reversedValue);

        } catch (Exception e) {
            return cardNum; // Kembalikan input asli jika gagal
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        Log.d(TAG, "Key pressed: " + keyCode + " (device: " + event.getDevice().getName() + ")");
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // --------------------------- NEW INTERCOM
        try {
            Runtime.getRuntime().exec("svc nfc disable");
            Runtime.getRuntime().exec("pm disable com.android.nfc");
            Runtime.getRuntime().exec("logcat -c");
            Runtime.getRuntime().exec("logcat -v time -f /sdcard/log_intercom.txt -r 51200 -n 3");
        } catch (Exception e) {
            e.printStackTrace();
        }
        // --------------------------- END LINE

        // Log.e(TAG, "SUPER LOG: onCreate started");
        // Log.e(TAG, "SUPER LOG: Registering IntercomPlugin...");
        registerPlugin(IntercomPlugin.class);
        // Log.e(TAG, "SUPER LOG: IntercomPlugin registered.");
        registerPlugin(RingtonePlugin.class);

        // --------------------------- Request Camera and Microphone native Android permissions on startup
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                    android.Manifest.permission.CAMERA,
                    android.Manifest.permission.RECORD_AUDIO
                }, 101);
            }
        }

        uiHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if (msg.what == WHAT_NEED_AUTO_2_BOTTOM) {
                    needAuto2Bottom = true;
                }
                return false;
            }
        });

        // Initialize API Service (Base URL should match your server)
        // apiService.setListener(this);

        // fetchSDKKeyFromAPI();

        try {
            Log.d(TAG, "Calling super.onCreate");
            // Workaround for WebView with system UID (Restored per revert request)
            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            //     try {
            //         WebView.setDataDirectorySuffix("fs_intercom");
            //     } catch (Exception e) {
            //         Log.w(TAG, "WebView suffix already set or failed: " + e.getMessage());
            //     }
            // }
            super.onCreate(savedInstanceState);
            Log.d(TAG, "Intercom APK [04]");
        } catch (Throwable e) {
            Log.e(TAG, "Crash in super.onCreate", e);
            throw e;
        }

        // Initialize FaceDetectHelper - Now handles background initialization and liveness setup
        // We will now wait for the SDK key from the API before initializing.
         
        try { //2026 david
            // Ensure SDK key is set before first init attempt
            if (sdkKey != null && !sdkKey.isEmpty()) {
                com.thinmoo.facerecognition.utils.SPUtils.put("cf_dbf_key", sdkKey, DmApplication.getInstance());
                FaceDetectHelper.getInstance().initFaceDetect();
                Log.d(TAG, "FaceDetectHelper initialized successfully in onCreate");
                android.widget.Toast.makeText(MainActivity.this, " FaceDetectHelper initialized successfully", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                Log.e(TAG, "SUPER LOG: SDK key is null on startup and no cached key found, skipping onCreate initialization");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to trigger FaceDetectHelper initialization", e);
        }
        

        // Initialize Access Control SDK for Card Reading with a 5-second delay
        // Moving to UI thread via Handler to ensure compatibility with legacy SDK
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Log.d(TAG, "Initializing AccessControl SDK after 5s delay...");
                // Using ttyS3 as manually set by user

                // ----------------------------- TEMPORARY ON COMMENT
                // String cardPort = "ttyS3";

                setupQRcodeReader();

                new Thread(() -> {
                    try {
                        String deviceId = null;
                        try {
                            deviceId = Build.getSerial();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        Log.d(TAG, "Initializing AccessControl SDK in background thread...");
                        if (deviceId != null && deviceId.startsWith("RY")) {
                            AccessControlModel.initAccessControlSDK(getApplicationContext(), "ttyS3", 115200, 1);
                        } else {
                            AccessControlModel.initAccessControlSDK(getApplicationContext(), "ttyS9", 115200, 2);
                        }
                        AccessControlModel.addACCardMsgListener(msgListener);
                        AccessControlModel.addACStateListener(stateListener);
                        Log.d(TAG, "AccessControl SDK initialized successfully in background");
                    } catch (Throwable t) {
                        Log.e(TAG, "CRITICAL ERROR in background AccessControl SDK init", t);
                    }
                }).start();
            } catch (Throwable t) {
                Log.e(TAG, "CRITICAL ERROR in setupQRcodeReader or thread launch", t);
            }
        }, 5000);
        
        // Start background image synchronization service
        Log.d(TAG, "Starting ScheduledImageService...");
        ScheduledImageService.scheduleEvery15MinutesImmediate(this);

        // Start 2-hour app restart timer
        startRestartTimer();
    }

    private void startRestartTimer() {
        long restartDelay = 2 * 60 * 60 * 1000;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "CRITICAL: Triggering scheduled 1 minutes app self-restart...");
            performSelfRestart();
        }, restartDelay);
    }

    public void performSelfRestart() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    9999,
                    intent,
                    PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                if (alarmManager != null) {
                    long triggerTime = System.currentTimeMillis() + 2000; // 2 seconds delay
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                    }
                }
            }
            Log.d(TAG, "Killing current process for self-restart...");
            AccessControlModel.closeAC();
            DMAccessUtil.getInstance().closeRedLed();
            DMAccessUtil.getInstance().closeWhiteLed();
            DMAccessUtil.getInstance().closeGreenLed();
        } catch (Exception e) {
            Log.e(TAG, "Error during self-restart cleanup", e);
        } finally {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
        }
    }
    private void fetchSDKKeyFromAPI() {
        Log.d(TAG, "Fetching SDK Key from server");
        if (apiService != null) {
            apiService.getSDKKey();
        }
    }

    // ImageApiListener Implementation
    /*
    @Override
    public void onSDKKeyReceived(String key) {
        Log.d(TAG, "SDK Key received: " + key);
        this.sdkKey = key;
        
        // Initialize FaceDetectHelper once we have the key
        runOnUiThread(() -> {
            try {
                com.thinmoo.facerecognition.utils.SPUtils.put("cf_dbf_key", sdkKey, DmApplication.getInstance());
                FaceDetectHelper.getInstance().initFaceDetect();
                Log.d(TAG, "FaceDetectHelper initialized with dynamic key");
                android.widget.Toast.makeText(MainActivity.this, "Face SDK Initialized", android.widget.Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize Face SDK with fetched key", e);
            }
        });
    }

    @Override public void onImageInfoReceived(ImageApiService.ImageInfo imageInfo) {}
    @Override public void onImageReceived(Bitmap image) {}
    @Override public void onFaceDataReceived(Bitmap image, String userId, String imageName) {}
    @Override public void onMultipleFacesReceived(List<ImageApiService.FaceRegistrationData> faceDataList) {}
    @Override public void onError(String error) { Log.e(TAG, "API Error: " + error); }
    @Override public void onRetryAttempt(int attemptNumber, int maxRetries) {}
    */
    private long lastCardSendTime = 0;

    private void sendCardToBackend(String cardNum) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCardSendTime < 3000) {
            Log.d(TAG, "Ignoring duplicate card scan (debounced): " + cardNum);
            return;
        }
        lastCardSendTime = currentTime;

        new Thread(() -> {
            try {
                String url_api_card = "https://ifs360-sg.com/api/card";
                URL url = new URL(url_api_card);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                String jsonInput = "{"
                        + "\"jsonrpc\": \"2.0\","
                        + "\"params\": {"
                        + "\"card_num\": \"" + cardNum + "\","
                        + "\"serial_number\": \"" + getDeviceIDSerial() + "\""
                        + "}"
                        + "}";

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInput.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                    StringBuilder responseBuilder = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        responseBuilder.append(line.trim());
                    }
                    String responseString = responseBuilder.toString();
                    Log.d(TAG, "Server response: " + responseString);

                    try {
                        JSONObject root = new JSONObject(responseString);
                        JSONObject result = root.optJSONObject("result");

                        if (result != null) {
                            int apiResponseCode = result.optInt("response_code", 0);
                            int secondsClosingDoor = result.optInt("seconds_closing_door", 0);
                            boolean openDoor = result.optBoolean("open_door", false);

                            if (apiResponseCode == 200 && openDoor) {
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    Log.d(TAG, "Opening door based on server response");
                                    DMAccessUtil.getInstance().openDoor();
                                    AccessControlModel.closeRedLed();
                                    AccessControlModel.closeWhiteLed();
                                    AccessControlModel.openGreenLed();
                                    DMAccessUtil.getInstance().closeRedLed();
                                    DMAccessUtil.getInstance().closeWhiteLed();
                                    DMAccessUtil.getInstance().openGreenLed();

                                    // Automatically close door after specified delay or 30s default
                                    long delay = secondsClosingDoor > 0 ? (long) secondsClosingDoor : 30000L;
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        Log.d(TAG, "Closing door automatically");
                                        DMAccessUtil.getInstance().closeDoor();
                                        AccessControlModel.closeGreenLed();
                                        AccessControlModel.closeRedLed();
                                        AccessControlModel.closeWhiteLed();
                                        DMAccessUtil.getInstance().closeGreenLed();
                                        DMAccessUtil.getInstance().closeRedLed();
                                        DMAccessUtil.getInstance().closeWhiteLed();
                                    }, delay);
                                });
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Failed to parse server response", e);
                    }
                } else {
                    Log.e(TAG, "Failed to send card. HTTP code: " + responseCode);
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error sending card to backend", e);
            }
        }).start();
    }

    private String getDeviceIDSerial() {
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
    private void setupQRcodeReader(){
        ACCallBack.ACSerialPortReadMsgListener listener = new ACCallBack.ACSerialPortReadMsgListener() {
            @Override
            public void onReadMsg(final byte[] data) {
                try{
                    if (data == null || data.length == 0)
                        return;
                    String msg = new String(data, "UTF-8");
                   Log.e(TAG, "qrdata:" + DMByteUtils.byte2hex(data, true));
                    if (data.length > 0 && (data[data.length - 1] & 0xff) == 0x0d){ //去掉结尾的 \r
                        if (data.length == 1){
                            msg = "";
                        }else{
                            msg = msg.substring(0, msg.length() - 1);
                        }
                    }
                    if (data.length > 0 &&((data[0] & 0xff) == 0xa5 || "5A010002900093A5".equalsIgnoreCase(DMByteUtils.byte2hex(data, true)))){
                        return;
                    }
                    qrcodeCache = msg;
                    if (mHandler.hasMessages(MSG_RQCODE)){
                        mHandler.removeMessages(MSG_RQCODE);
                    }
                    mHandler.sendEmptyMessageDelayed(MSG_RQCODE, 100);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }

            @Override
            public void onReadError(final String msg) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // tv_logs.append("QrCode Error:"+msg);
                        // android.widget.Toast.makeText(MainActivity.this, "QrCode Error:"+msg, android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            }
        };
        // SerialPortEntity entity = new SerialPortEntity("ttyS9", 9600, "777", 100, listener);
        // ----------------- new intercom
        final String deviceId = getDeviceIDSerial();
        SerialPortEntity entity;
        if (deviceId != null && deviceId.startsWith("RY")) {
            entity = new SerialPortEntity("ttyS9", 9600, "777", 100, listener);
        } else {
            entity = new SerialPortEntity("ttyS0", 9600, "777", 100, listener);
        }
        // ---------------- end line
        AccessControlModel.addSerialPortEntity(entity);
        Timer setQRCodeScannerMode = new Timer(false);
        TimerTask reStarttask = new TimerTask() {
            @Override
            public void run() {
                // AccessControlModel.writeSerialPortByte("ttyS9", DMByteUtils.hexStringToBytes("5A000008535230333033303108A5"));
                if (deviceId != null && deviceId.startsWith("RY")) {
                    AccessControlModel.writeSerialPortByte("ttyS9", DMByteUtils.hexStringToBytes("5A000008535230333033303108A5"));
                } else {
                    AccessControlModel.writeSerialPortByte("ttyS0", DMByteUtils.hexStringToBytes("5A000008535230333033303108A5"));
                }
            }
        };
        setQRCodeScannerMode.schedule(reStarttask, 5000 );
    }
    public Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_RQCODE:
                    Log.e(TAG, "Sending QR Code:" + qrcodeCache);
                    if (qrcodeCache != null && !qrcodeCache.isEmpty()) {
                        // Show in logs (optional)
                        final String qrToSend = qrcodeCache; // snapshot
                        // tv_logs.append("Sending QR Code: " + qrToSend + "\n");
                        // android.widget.Toast.makeText(MainActivity.this, "Sending QR Code: " + qrToSend, android.widget.Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Sending QR Code:" + qrToSend);
                        // Send QR code to backend
                        new Thread(() -> {
                            try {
                                // URL of your local server endpoint
                                String url_api_qr = "https://ifs360-sg.com/api/qr";
                                URL url = new URL(url_api_qr); // Replace with your local API

                                // Create connection
                                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                                conn.setRequestMethod("POST");
                                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                                conn.setDoOutput(true);
                                conn.setConnectTimeout(10000);
                                conn.setReadTimeout(10000);

                                // JSON body with QR code
                                String jsonInput = "{"
                                        + "\"jsonrpc\": \"2.0\","
                                        + "\"params\": {"
                                        + "\"qr_code\": \"" + qrToSend + "\","
                                        + "\"serial_number\": \"" + getDeviceIDSerial() + "\""
                                        + "}"
                                        + "}";

                                // Send body
                                try (OutputStream os = conn.getOutputStream()) {
                                    byte[] input = jsonInput.getBytes("utf-8");
                                    os.write(input, 0, input.length);
                                }

                                // Read response
                                int responseCode = conn.getResponseCode();
                                if (responseCode == HttpURLConnection.HTTP_OK) {
                                    InputStream responseStream = conn.getInputStream();
                                    BufferedReader br = new BufferedReader(new InputStreamReader(responseStream));
                                    StringBuilder responseBuilder = new StringBuilder();
                                    String line;
                                    while ((line = br.readLine()) != null) {
                                        responseBuilder.append(line);
                                    }
                                    String responseString = responseBuilder.toString();
                                    try {
                                        JSONObject jsonResponse = new JSONObject(responseString);
                                        JSONObject root = new JSONObject(responseString);
                                        JSONObject result = root.optJSONObject("result");
                                        int apiResponseCode = result.optInt("response_code", 0);
                                        int secondsClosingDoor = result.optInt("seconds_closing_door", 0);
                                        long delay = secondsClosingDoor > 0 ? (long) secondsClosingDoor : 30000L;
                                        if (delay < 8000L) {
                                            delay = 8000L; // ensure at least 8 seconds for the green light
                                        }
                                        final long finalDelay = delay;
                                        boolean openDoor = result.optBoolean("open_door", false);
                                        new Handler(Looper.getMainLooper()).post(() -> {
                                            // Show raw response in logs
                                            // tv_logs.append("Server response: " + responseString + "\n");
                                            // android.widget.Toast.makeText(MainActivity.this, "Server response: " + responseString, android.widget.Toast.LENGTH_SHORT).show();

                                            // Handle logic based on API response
                                            if (apiResponseCode == 200 && openDoor) {
                                                // tv_logs.append("Door should open now.\n");
                                                android.widget.Toast.makeText(MainActivity.this, "Door should open now.", android.widget.Toast.LENGTH_SHORT).show();

                                                // Play open door sound
                                                MediaPlayer openDoorSound = MediaPlayer.create(getApplicationContext(), R.raw.door_open);
                                                /*openDoorSound.start();
                                                openDoorSound.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                                    @Override
                                                    public void onCompletion(MediaPlayer mp) {
                                                        mp.release();
                                                    }
                                                });*/

                                                DMAccessUtil.getInstance().openDoor();

                                                // Light Up Green Light When Opening Door
                                                AccessControlModel.closeRedLed();
                                                AccessControlModel.closeWhiteLed();
                                                AccessControlModel.openGreenLed();
                                                DMAccessUtil.getInstance().closeRedLed();
                                                DMAccessUtil.getInstance().closeWhiteLed();
                                                DMAccessUtil.getInstance().openGreenLed();

                                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                                    DMAccessUtil.getInstance().closeDoor();

                                                    MediaPlayer closeDoorSound = MediaPlayer.create(getApplicationContext(), R.raw.door_close);
                                                    /*closeDoorSound.start();
                                                    closeDoorSound.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                                        @Override
                                                        public void onCompletion(MediaPlayer mp) {
                                                            mp.release();
                                                        }
                                                    }); */

                                                    // Back to white light when door is closing
                                                    AccessControlModel.closeGreenLed();
                                                    AccessControlModel.closeRedLed();
                                                    AccessControlModel.closeWhiteLed();
                                                    DMAccessUtil.getInstance().closeGreenLed();
                                                    DMAccessUtil.getInstance().closeRedLed();
                                                    DMAccessUtil.getInstance().closeWhiteLed();
                                                    // tv_logs.append("Door automatically closed after 30 seconds.\n");
                                                    android.widget.Toast.makeText(MainActivity.this, "Door automatically closed after 30 seconds.", android.widget.Toast.LENGTH_SHORT).show();
                                                }, finalDelay); // 30 seconds = 30,000 milliseconds
                                            } else {
                                                // tv_logs.append("No action for door.\n");
                                                android.widget.Toast.makeText(MainActivity.this, "No action for door.", android.widget.Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                        new Handler(Looper.getMainLooper()).post(() ->
                                                // tv_logs.append("Failed to parse server response.\n")
                                                //  android.widget.Toast.makeText(MainActivity.this, "Failed to parse server response.", android.widget.Toast.LENGTH_SHORT).show()
                                                 Log.e(TAG, "Failed to parse server response.")
                                                );
                                    }
                                } else {
                                    new Handler(Looper.getMainLooper()).post(() ->
                                            // tv_logs.append("Failed to send QR Code. HTTP code: " + responseCode + "\n")
                                            // android.widget.Toast.makeText(MainActivity.this, "Failed to send QR Code. HTTP code: " + responseCode, android.widget.Toast.LENGTH_SHORT).show()
                                             Log.e(TAG, "Failed to send QR Code. HTTP code: " + responseCode)
                                            );
                                }
                                conn.disconnect();
                            } catch (Exception e) {
                                e.printStackTrace();
                                Log.e(TAG, "Error: " + e.getMessage());
                                new Handler(Looper.getMainLooper()).post(() ->
                                        // tv_logs.append("Error: " + e.getMessage() + "\n")
                                        // android.widget.Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show()
                                         Log.e(TAG, "Error: " + e.getMessage())
                                        );
                            }
                        }).start();
                    }

                    break;
                case MSG_CLOSE_DOOR:
                    DMAccessUtil.getInstance().closeDoor();
                    break;
                case MSG_WATCHDOG:
                    if (switch_watchDog.isChecked()){
                        sendEmptyMessageDelayed(MSG_WATCHDOG, 10000);
                    }
                    DMAccessUtil.getInstance().openWatchdog();
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        try {
            mHandler.removeCallbacksAndMessages(null);
        } catch (Exception e) {
            Log.e(TAG, "Error removing callbacks", e);
        }
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            AccessControlModel.closeAC();
        } catch (Exception e) {
            Log.e(TAG, "Error closing AccessControl SDK", e);
        }
    }

}
