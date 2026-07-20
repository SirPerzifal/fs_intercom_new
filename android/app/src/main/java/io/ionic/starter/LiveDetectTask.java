package io.ionic.starter;

import com.thinmoo.facerecognition.FaceClient;

import java.util.concurrent.Callable;

public class LiveDetectTask implements Callable<Boolean> {
    private byte[] data1;
    private byte[] data2;
    private int width;
    private int height;

    public LiveDetectTask(byte[] data1, byte[] data2, int width, int height) {
        this.data1 = data1;
        this.data2 = data2;
        this.width = width;
        this.height = height;
    }

    @Override
    public Boolean call() throws Exception {
        if (data1 != null)
            FaceClient.getInstance().process(data1, width, height);
        return true;
    }
}
