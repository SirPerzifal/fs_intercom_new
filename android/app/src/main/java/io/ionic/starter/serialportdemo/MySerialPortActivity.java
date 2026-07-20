package io.ionic.starter.serialportdemo;

import android.os.Bundle;
import android.util.Log;

import java.util.Arrays;

public class MySerialPortActivity extends SerialPortBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDataReceived(byte[] buf, int size) {
        Log.d("MySerialPortActivity", "Data received: " + Arrays.toString(buf));
    }
}
