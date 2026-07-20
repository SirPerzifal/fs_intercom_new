package io.ionic.starter;

import com.thinmoo.facerecognition.FaceClient;
import com.thinmoo.facerecognition.FaceRecognizeType;

import java.util.Map;
import java.util.concurrent.Callable;

public class RecognitionTask implements Callable<Map<String, Object>> {

    private byte[] curDataMulticolor;
    private byte[] curDataIR;
    private int width;
    private int height;

    public RecognitionTask(byte[] data, byte[] irData, int width, int height){
        this.curDataMulticolor = data;
        this.curDataIR = irData;
        this.width = width;
        this.height = height;
    }

    @Override
    public Map<String, Object> call() throws Exception {
        Map<String, Object> ret;
        if (FaceClient.getInstance().getType() == FaceRecognizeType.ZK) {
            ret = handleData(curDataMulticolor, curDataIR, width, height);
        } else {
            ret = handleData(curDataMulticolor, curDataIR, width, height);
        }
        return ret;
    }

    public static Map<String, Object> handleData(byte[] curDataMulticolor, byte[] curDataIR, int width, int height) {
        android.util.Log.d("RecognitionTask", "Starting sequential processing - width: " + width + ", height: " + height);
        
        int totalRegistered = com.thinmoo.facerecognition.FaceClient.getInstance().allFaceCount();
        android.util.Log.d("RecognitionTask", "SUPER LOG: [PROSES PENCOCOKAN] Memulai verifikasi wajah.");
        android.util.Log.d("RecognitionTask", "SUPER LOG: [PROSES PENCOCOKAN] Jumlah wajah yang terdaftar di SDK memory: " + totalRegistered);
        
        if (curDataIR != null) {
            android.util.Log.d("RecognitionTask", "Setting IR data for liveness check... size: " + curDataIR.length);
            FaceClient.getInstance().setIRData(curDataIR, width, height);
        } else {
            android.util.Log.e("RecognitionTask", "IR DATA IS NULL! Bypassing by using multicolor data as fallback.");
            FaceClient.getInstance().setIRData(curDataMulticolor, width, height);
        }
        
        android.util.Log.d("RecognitionTask", "Calling FaceClient.process()...");
        FaceClient.getInstance().process(curDataMulticolor, width, height);
        
        android.util.Log.d("RecognitionTask", "Process complete. BdFaceManager.liveness: " + com.thinmoo.facerecognition.bdface.BdFaceManager.liveness);
        
        android.util.Log.d("RecognitionTask", "Calling FaceClient.recognize()...");
        Map<String, Object> ret = FaceClient.getInstance().recognize(curDataMulticolor, width, height);
        
        if (ret != null && ret.get("userID") != null) {
            String userID = (String) ret.get("userID");
            int score = (ret.get("score") != null) ? (int) ret.get("score") : 0;
            android.util.Log.d("RecognitionTask", "SUPER LOG: [HASIL PENCOCOKAN] Wajah terdaftar ditemukan! UserID: " + userID + " | Score: " + score);
        } else {
            android.util.Log.e("RecognitionTask", "SUPER LOG: [HASIL PENCOCOKAN] Wajah TIDAK terdaftar / TIDAK cocok!");
        }
        return ret;
    }
}
