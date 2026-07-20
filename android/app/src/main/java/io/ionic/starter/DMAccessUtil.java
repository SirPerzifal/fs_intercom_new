package io.ionic.starter;

import android.app.Instrumentation;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.thinmoo.serial.AccessControlModel;

/**
 * Created by benson on 2022/6/1.
 *
 */

public class DMAccessUtil {

    private static final String TAG = "DMAccessUtil";

    private Class spiritWgManagerClass;
    private Class spiritWgOutManagerClass;
    private Object mSpiritWgManager;
    private Object mSpiritWgOutManager;

    private ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private boolean isNC = false;

    private static final String SYS_FILE =
            "/sys/devices/platform/fe8a0000.usb2-phy/otg_mode";
    private static final String OTG_MODE_STR = "otg";
    private static final String HOST_MODE_STR = "host";
    private static final String DEVICE_MODE_STR = "peripheral";
    public static final String usbPwrPath =
            "/sys/devices/platform/external_interface/usbPwr";

    private final int SUCCESS = 0;

    public void setIsNC(boolean isNC){
        this.isNC = isNC;
    }

    private static volatile DMAccessUtil instance;
    private DMAccessUtil(){};
    public static DMAccessUtil getInstance(){
        if (instance == null){
            synchronized (DMAccessUtil.class){
                if (instance == null){
                    instance = new DMAccessUtil();
                    instance.isNC = false;
                }
            }
        }
        return instance;
    }

    public static final String BREATHING_LIGHT_MODE = "/dev/interface";
    public static final String WATCHDOG_PATH = "/dev/watchdog";

    private boolean isGreenLedActive = false;

    //open white led light
    public void openWhiteLed(){
        if (isGreenLedActive) return;
        // writeInterface(11);
        AccessControlModel.openWhiteLed();
    }
    //close white led light
    public void closeWhiteLed(){
        if (isGreenLedActive) return;
        // writeInterface(10);
        AccessControlModel.closeWhiteLed();
    }

    //open red led light
    public void openRedLed(){
        // writeInterface(21);
        AccessControlModel.openRedLed();
    }
    //close red led light
    public void closeRedLed(){
        // writeInterface(20);
        AccessControlModel.closeRedLed();
    }

    //open green led light
    public void openGreenLed(){
        isGreenLedActive = true;
        // writeInterface(31);
        AccessControlModel.openGreenLed();
    }
    //close green led light
    public void closeGreenLed(){
        isGreenLedActive = false;
        // writeInterface(30);
        AccessControlModel.closeGreenLed();
    }

    //opendoor
    public void openDoor(){
        // writeInterface(isNC ? 40 : 41);
        AccessControlModel.keepOpenDoor();
    }
    //closedoor
    public void closeDoor(){
        // writeInterface(isNC ? 41 : 40);
        AccessControlModel.closeDoor();
    }

    //open Camera IR light
    public void openCameraLed(){
        writeInterface(81);
    }
    //close Camera IR light
    public void closeCameraLed(){
        writeInterface(80);
    }

    //open IR light
    public void openIrLed(){
        writeInterface(61);
    }
    //close IR light
    public void closeIrLed(){
        writeInterface(60);
    }

    /**
     * Watchdog description: After calling openWatchdog, if openWatchdog is not called again within 45 seconds, the system will automatically restart.
     * The watchdog is turned off by default after restarting
     */
    public void openWatchdog() {
        writeWatchdog(1);
    }
    //
    public void closeWatchdog() {
        writeWatchdog("V");
    }

    //USB power
    public void usbPwrOn() {
        writeInterface(usbPwrPath, 1);
    }
    public void usbPwrOff() {
        writeInterface(usbPwrPath, 0);
    }
    public String getUsbPwr(){
        return readInterface(usbPwrPath);
    }

    private void writeInterface(int mode) {
        try {
            File myFile = new File(BREATHING_LIGHT_MODE);
            if (myFile.exists()) {
                FileWriter fileWriter = new FileWriter(myFile);
                fileWriter.write(String.valueOf(mode));
                fileWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeWatchdog(int mode) {
        try {
            File myFile = new File(WATCHDOG_PATH);
            if (myFile.exists()) {
                FileWriter fileWriter = new FileWriter(myFile);
                fileWriter.write(String.valueOf(mode));
                fileWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeWatchdog(String mode) {
        try {
            File myFile = new File(WATCHDOG_PATH);
            if (myFile.exists()) {
                FileWriter fileWriter = new FileWriter(myFile);
                fileWriter.write(mode);
                fileWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void simulateKeystroke(final int KeyCode) {
        try {
            Instrumentation inst=new Instrumentation();
            inst.sendKeyDownUpSync(KeyCode);
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    public void setHostMode() {
        String current_mode1 = ReadFile(SYS_FILE);
        if (OTG_MODE_STR.equals(current_mode1) ||
                DEVICE_MODE_STR.equals(current_mode1)) {
            WriteFile(SYS_FILE, HOST_MODE_STR);
        }
    }

    public void setDeviceMode() {
        String current_mode = ReadFile(SYS_FILE);
        if (HOST_MODE_STR.equals(current_mode)) {
            WriteFile(SYS_FILE, DEVICE_MODE_STR);
        }
    }

    public String ReadFile(String filePath) {
        File file = new File(filePath);
        if((file != null) && file.exists()) {
            try {
                FileInputStream fs= new FileInputStream(file);
                BufferedReader reader=
                        new BufferedReader(new InputStreamReader(fs));
                String current_mode = reader.readLine();
                fs.close();
                Log.d(TAG, "===== Usb mode:" + current_mode +
                        "======");
                if (HOST_MODE_STR.equals(current_mode)) {
                    current_mode = HOST_MODE_STR;
                } else if (DEVICE_MODE_STR.equals(current_mode)) {
                    current_mode = DEVICE_MODE_STR;
                } else if (OTG_MODE_STR.equals(current_mode)) {
                    current_mode = OTG_MODE_STR;
                }
                return current_mode;
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
    public int WriteFile(String filePath, String mode) {
        Log.d(TAG,"WriteFile, write mode = " + mode);
        File file = new File(filePath);
        if((file == null) || (!file.exists()) || (mode == null)) {
            Log.e(TAG, "write error: " + filePath);
            return -1;
        }
        try {
            FileOutputStream fos = new FileOutputStream(file);
            PrintWriter pWriter = new PrintWriter(fos);
            pWriter.println(mode);
            pWriter.flush();
            pWriter.close();
            fos.close();
            return 0;
        } catch(IOException ret) {
            Log.d(TAG,"write error:" + ret);
            return -1;
        }
    }

    private static void writeInterface(String path, int mode) {
        try {
            File myFile = new File(path);
            if (myFile.exists()) {
                FileWriter fileWriter = new FileWriter(myFile);
                fileWriter.write(String.valueOf(mode));
                fileWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static String readInterface(String path) {
        File file = new File(path);
        if (file == null || !file.isFile()) {
            return null;
        }
        BufferedReader reader = null;
        try {
            InputStreamReader is = new InputStreamReader(new
                    FileInputStream(file));
            reader = new BufferedReader(is);
            String line = "";
            line = reader.readLine();
            return line;
        } catch (IOException e) {
            Log.d("ry", "=readSysFile==IOException=");
            e.printStackTrace();
            return "";
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

//    //install apk
//    public void uninstallApk(String packageName){
//        Intent uninstallIntent = new Intent("unipro.uninstall.pack");
//        uninstallIntent.putExtra("packageName", packageName);
//        uninstallIntent.setComponent(new
//                ComponentName("com.android.settings",
//                "com.android.settings.PhoneReceiver"));
//        DmApplication.getInstance().sendBroadcast(uninstallIntent);
//    }
//
//    //uninstall apk
//    public void installAPK(String apkPath){
//        Intent intent = new Intent("unipro.install.pack");
//        intent.putExtra("package_path", apkPath);
//        intent.setComponent(new ComponentName("com.android.settings",
//                "com.android.settings.PhoneReceiver"));
//        DmApplication.getInstance().sendBroadcast(intent);
//    }


}
