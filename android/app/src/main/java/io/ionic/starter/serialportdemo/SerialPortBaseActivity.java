package io.ionic.starter.serialportdemo;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;

import com.thinmoo.serial.SerialPort;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidParameterException;

public abstract class SerialPortBaseActivity extends AppCompatActivity {

    private static final String TAG = "SerialPortBase";
    static public final String DEVNAME = "/dev/ttyS0";
    static public final int BAUDRATE = 115200;

    protected SerialPort mSerialPort;
    protected OutputStream mOutputStream;
    protected InputStream mInputStream;
    private ReadThread mReadThread;
    private class ReadThread extends Thread {
        @Override
        public void run() {
            Log.i(TAG, "thread start reading from serial...");
            super.run();
            byte[] buffer = new byte[32];
            while (!isInterrupted()) {
                try {
                    if (mInputStream != null) {
                        int size = 0;

                        size = mInputStream.read(buffer);
                        if (size<=0){
                            try {
                                sleep(10);
                                continue;
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        if (size > 0) {
                            onDataReceived(buffer, size);
                        }
                    } else {
                        Log.i(TAG, "mInputStream:null");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.i(TAG, "IOException:" + e.toString());
                }
            }
            Log.i(TAG, "return from Com ReadThread");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public abstract void onDataReceived(byte[] buf,int size) ;
}
