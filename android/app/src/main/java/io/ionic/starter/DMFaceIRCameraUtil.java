package io.ionic.starter;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
// import android.widget.Toast;

import com.thinmoo.facerecognition.FaceClient;

/**
 * Created by thinmoo_cch on 2019/5/5.
 */

public class DMFaceIRCameraUtil implements SurfaceHolder.Callback, Camera.PreviewCallback{

    /**
     * Display camera picture
     */
    private SurfaceView surfaceView;
    /**
     * the Camera object
     */
    private Camera mCamera;
    /**
     * Holding a display surface.Allows you to control the surface size and format
     */
    private SurfaceHolder surfaceHolder;
    /**
     * camera ID
     */
    private int cameraId = 1;

    /**
     * Camera Display width
     */
    // ------------ OLD CODE
    public static int width = 640;//1920*1080,1280*720,640*480,640*360,320*240
    // ------------
    // public static int width = CameraConfig.getWidth();;//1920*1080,1280*720,640*480,640*360,320*240
    // ------------

    /**
     * Camera Display height
     */
    // ------------ OLD CODE
    public static int height = 480;
    // ------------
    // public static int height = CameraConfig.getHeight();
    // ------------

    /**
     * The view is used to draw face frames
     */
    private SurfaceView sfvFaceRect;

    private static String faceCameraOrientation;

    public static int widthFaceFrame = 400;
    public static int heightFaceFrame = 300;
    private Activity activity;
    private boolean isBackgroundMode = false;
    public static int orientation = CameraConfig.getOrientation();


    public DMFaceIRCameraUtil(Activity activity, int surfaceId) {
        this(activity, (SurfaceView) activity.findViewById(surfaceId));
    }

    public DMFaceIRCameraUtil(Activity activity, SurfaceView surfaceView) {
        this.activity = activity;
        this.surfaceView = surfaceView;
        if (this.surfaceView != null) {
            this.surfaceView.setZOrderOnTop(true);
            this.surfaceView.setZOrderMediaOverlay(true);
            surfaceHolder = this.surfaceView.getHolder();
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

            int width = this.surfaceView.getWidth();
            int height = this.surfaceView.getHeight();
            if (width > 0 && height > 0){
                widthFaceFrame = width;
                heightFaceFrame = height;
            }
        } else {
            Log.e("DMFaceIRCameraUtil", "SurfaceView is null!");
        }
    }

    private boolean is5(){
        int screenWidth = DisplayUtil.getScreenWidth(DmApplication.getInstance()); //屏幕宽
        int screenHeight = DisplayUtil.getScreenHeight(DmApplication.getInstance()); //屏幕高
        if (screenHeight == 854 && screenWidth == 480){ //5寸
            return true;
        }
        return false;
    }

    public DMFaceIRCameraUtil show() {
        Log.e("DMFaceIRCameraUtil", "SUPER LOG: show() called");
        OpenCameraAndSetSurfaceviewSize(cameraId);
        //打开红外灯
        openIrLed(true);
        if(mCamera!=null && surfaceHolder != null && surfaceHolder.getSurface().isValid()){
            Log.e("DMFaceIRCameraUtil", "SUPER LOG: Surface is already valid in show(), starting preview");
            SetAndStartPreview(surfaceHolder);
        } else {
            Log.e("DMFaceIRCameraUtil", "SUPER LOG: show() called but surface not ready yet. Waiting for surfaceCreated...");
        }
        return this;
    }

    /**
     * 控制红外灯
     * @param open  是否打开
     */
    private void openIrLed(boolean open){
        if (open){
            if (is5()){
                DMAccessUtil.getInstance().openIrLed();
            }else{
                DMAccessUtil.getInstance().openCameraLed();
            }
        }else{
            if (is5()){
                DMAccessUtil.getInstance().closeIrLed();
            }else{
                DMAccessUtil.getInstance().closeCameraLed();
            }
        }
    }

    public void destroy(){
        //关闭红外灯
        openIrLed(false);
        kill_camera();
    }

    private void kill_camera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    // Add these methods:
    public void setBackgroundMode(boolean backgroundMode) {
        this.isBackgroundMode = backgroundMode;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e("DMFaceIRCameraUtil", "SUPER LOG: surfaceCreated() callback received");
        surfaceHolder = holder;
        if (mCamera != null) {
            Log.e("DMFaceIRCameraUtil", "SUPER LOG: Camera exists, starting preview in surfaceCreated");
            SetAndStartPreview(holder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (!isBackgroundMode) {
            kill_camera();
        }
    }

    private Void SetAndStartPreview(SurfaceHolder holder) {
        Log.e("DMFaceIRCameraUtil", "SUPER LOG: SetAndStartPreview() started");
        try {
            mCamera.setPreviewDisplay(holder);
//            mCamera.setDisplayOrientation(90);

            Camera.Parameters parameters = mCamera.getParameters();
//            parameters.setPictureFormat(PixelFormat.JPEG);
            if (parameters.getSupportedPreviewFormats().contains(ImageFormat.NV21)) {
                parameters.setPreviewFormat(ImageFormat.NV21);
            }

            int dataBufferSize = (int) (height * width *
                    (ImageFormat.getBitsPerPixel(mCamera.getParameters().getPreviewFormat()) / 8.0));
            mCamera.setParameters(parameters);
            mCamera.addCallbackBuffer(new byte[dataBufferSize]);
            mCamera.addCallbackBuffer(new byte[dataBufferSize]); // Dual buffer to prevent starvation
            mCamera.setPreviewCallbackWithBuffer(this);
//            mCamera.setPreviewCallback(this);
            mCamera.startPreview();
            mCamera.cancelAutoFocus();
        } catch (Exception e) {
            Log.e("DMFaceCameraUtil",e.getMessage());
        }
        return null;
    }

    private void OpenCameraAndSetSurfaceviewSize(int cameraId) {
        if(mCamera == null){
            try {
                mCamera = Camera.open(cameraId);
            } catch (Exception e) {
                Log.e("DMFaceCameraUtil","Camera ID:" + cameraId + "==== Camera.open:e="+e.getLocalizedMessage());
                // Toast.makeText(DmApplication.getInstance(), "open camera err", Toast.LENGTH_LONG).show();
                destroy();
                return;
            }
        }
        try {
            Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewSize(width, height);
            // ------------ OLD CODE
            parameters.setRotation(270);
            mCamera.setDisplayOrientation(270);
            // ------------
            // parameters.setRotation(orientation);
            // mCamera.setDisplayOrientation(orientation);
            // ------------
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(this.cameraId, info);
            mCamera.setParameters(parameters);
        }
        catch (RuntimeException e) {
            Log.e("DMFaceCameraUtil","@@@=== Camera getParameters Failed : empty parameters");
//            checkReboot();
            return;
        }

    }

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        try {
            // Log periodic preview frames
            // Log.e("DMFaceIRCameraUtil", "SUPER LOG: IR Preview Frame received. size=" + data.length);
            byte[] yuv;
//            yuv = data;

            // ------------ OLD CODE
            byte[] rotatedYuv = Rotator.rotateYUV420Degree270(data, width, height); // Software rotate to 270deg (Sensor seems specialized)
            // ------------
            // byte[] rotatedYuv = data; // Software rotate to 270deg (Sensor seems specialized)
            // if (orientation > 0) {
            //   if (orientation == 180){
            //     rotatedYuv = Rotator.rotateYUV420Degree180(data, width, height); }
            //   else if(orientation == 90){
            //     rotatedYuv = Rotator.rotateYUV420Degree90(data, width, height); }
            //   else if(orientation == 270){
            //     rotatedYuv = Rotator.rotateYUV420Degree270(data, width, height);
            //   }
            // }
            // ------------
            
            // Clone the shared buffer so asynchronous SDK processing isn't corrupted
            // by the next frame overwriting the shared buffer (causing "detect exceeded 2.5s" timeouts).
            byte[] clonedYuv = rotatedYuv.clone();
            FaceDetectHelper.getInstance().setCacheDataBlackWhite(clonedYuv);
            // After 90 rotation, width and height are swapped for the SDK
            // We no longer call setIRData here because RecognitionTask handles it sequentially.
            FaceClient.getInstance().setIRData(rotatedYuv, CameraConfig.width, CameraConfig.height);

            camera.addCallbackBuffer(data);
        } catch (Exception e) {
            Log.e("DMFaceCameraUtil","addCallbackBuffer error");
        }
    }

    public void setCameraId(int cameraId) {
        this.cameraId = cameraId;
    }

}
