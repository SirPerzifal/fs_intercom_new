package io.ionic.starter;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
// import android.widget.Toast;

/**
 * Created by thinmoo_cch on 2019/5/5.
 */

public class DMFaceCameraUtil implements SurfaceHolder.Callback, Camera.PreviewCallback{

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
    private int cameraId = 0;

    /**
     * Camera Display width
     */
    // ------------ OLD CODE
    public static int width = 640;//1920*1080,1280*720,640*480,640*360,320*240
    // ------------
    // public static int width = CameraConfig.getWidth();//1920*1080,1280*720,640*480,640*360,320*240
    // ------------

    /**
     * Camera Display height
     */
    // ------------ OLD CODE
    public static int height = 480;
    // ------------
    // public static int height = CameraConfig.getHeight();;
    // ------------

    /**
     * The view is used to draw face frames
     */
    private SurfaceView sfvFaceRect;

    private static String faceCameraOrientation;

    public static int widthFaceFrame = 400;
    public static int heightFaceFrame = 300;
    public static int orientation = CameraConfig.getOrientation();


    private Activity activity;
    private boolean isBackgroundMode = false;


    public DMFaceCameraUtil(Activity activity, int surfaceId, int faceRectId) {

        surfaceView = (SurfaceView) activity.findViewById(surfaceId);
        surfaceView.setZOrderOnTop(true);
        surfaceView.setZOrderMediaOverlay(true);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        int width = surfaceView.getWidth();
        int height = surfaceView.getHeight();
        if (width > 0 && height > 0){
            widthFaceFrame = width;
            heightFaceFrame = height;
        }

        sfvFaceRect = (SurfaceView) activity.findViewById(faceRectId);
        sfvFaceRect.setZOrderOnTop(true);
        sfvFaceRect.getHolder().setFormat(PixelFormat.TRANSLUCENT);

        FaceDetectHelper.getInstance().setMulticolorSurfaceView(sfvFaceRect);
    }

    public DMFaceCameraUtil(Activity activity, SurfaceView surfaceView) {
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
            Log.e("DMFaceCameraUtil", "SurfaceView is null!");
        }
    }

    public DMFaceCameraUtil show() {
        OpenCameraAndSetSurfaceviewSize(cameraId);

        if(mCamera!=null){
            SetAndStartPreview(surfaceHolder);
        }
        return this;
    }

    public void setBackgroundMode(boolean backgroundMode) {
        this.isBackgroundMode = backgroundMode;
    }

    public void destroy(){
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

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceHolder = holder;
        if (mCamera != null) {
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
            //mCamera.setParameters(parameters);
           // mCamera.addCallbackBuffer(new byte[dataBufferSize]);
            //mCamera.addCallbackBuffer(new byte[dataBufferSize]); // Dual buffer to prevent starvation

            // Menjadi ini (5 buffer):
            mCamera.addCallbackBuffer(new byte[dataBufferSize]);
            mCamera.addCallbackBuffer(new byte[dataBufferSize]);
            mCamera.addCallbackBuffer(new byte[dataBufferSize]);
            mCamera.addCallbackBuffer(new byte[dataBufferSize]);
            mCamera.addCallbackBuffer(new byte[dataBufferSize]);

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
            // mCamera.setDisplayOrientation(0);
            // mCamera.setDisplayOrientation(90);
            // mCamera.setDisplayOrientation(180);
            mCamera.setDisplayOrientation(270); //ubah dsini untuk posisi kamera
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
            Log.d("OnPreviewFrame", "Preview frame received, size");
            byte[] yuv;

            // ------------ OLD CODE
            // byte[] rotatedYuv = Rotator.rotateYUV420Degree180(data, width, height);
            // ------------
            byte[] rotatedYuv = data;
            if (orientation > 0) {
                if (orientation == 180){
                    rotatedYuv = Rotator.rotateYUV420Degree180(data, width, height);
                } else if(orientation == 90){
                    rotatedYuv = Rotator.rotateYUV420Degree90(data, width, height);
                } else if(orientation == 270){
                    rotatedYuv = Rotator.rotateYUV420Degree270(data, width, height);
                }
            }
            // ------------

            byte[] clonedYuv = rotatedYuv.clone();
            FaceDetectHelper.getInstance().setCacheMulticolor(clonedYuv);

            camera.addCallbackBuffer(data);
        } catch (Exception e) {
            Log.e("DMFaceCameraUtil","addCallbackBuffer error");
        }
    }

    public void setCameraId(int cameraId) {
        this.cameraId = cameraId;
    }

}
