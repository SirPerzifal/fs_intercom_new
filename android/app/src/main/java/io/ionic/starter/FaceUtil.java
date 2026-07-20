package io.ionic.starter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;
import android.view.SurfaceView;

import com.thinmoo.facerecognition.FaceRect;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class FaceUtil {
	public final static int REQUEST_PICTURE_CHOOSE = 1;
	public final static int  REQUEST_CAMERA_IMAGE = 2;
	public final static int REQUEST_CROP_IMAGE = 3;
	private final static String TAG = "FaceUtil";

	static public void drawFaceRect(Canvas canvas, FaceRect face, int width, int height, boolean frontCamera, boolean DrawOriRect) {
		if(canvas == null) {
			return;
		}
		int left = face.bound.left;
		int top = face.bound.top;
		int right = face.bound.right;
		int bottom = face.bound.bottom;

		Paint paint = new Paint(); 
		paint.setColor(Color.rgb(255, 203, 15));
		int len = (bottom - top) / 8;
		if (len / 8 >= 2) paint.setStrokeWidth(len / 8);
		else paint.setStrokeWidth(2);
		
		Rect rect = new Rect(left, top, right, bottom);
		FaceUtil.chooseToRoom(rect, width, height);
		if(frontCamera) {
			top = rect.top;
			rect.top = width - rect.bottom;
			rect.bottom = width - top;
		}

		if (DrawOriRect) {
			paint.setStyle(Style.STROKE);
			canvas.drawRect(rect, paint);
		}
		else {
			int drawl = rect.left	- len;
			int drawr = rect.right	+ len;
			int drawu = rect.top 	- len;
			int drawd = rect.bottom	+ len;
			
			canvas.drawLine(drawl,drawd,drawl,drawd-len, paint);
			canvas.drawLine(drawl,drawd,drawl+len,drawd, paint);
			canvas.drawLine(drawr,drawd,drawr,drawd-len, paint);
			canvas.drawLine(drawr,drawd,drawr-len,drawd, paint);
			canvas.drawLine(drawl,drawu,drawl,drawu+len, paint);
			canvas.drawLine(drawl,drawu,drawl+len,drawu, paint);
			canvas.drawLine(drawr,drawu,drawr,drawu+len, paint);
			canvas.drawLine(drawr,drawu,drawr-len,drawu, paint);
		}
}

	public static void chooseToRoom(Rect rect, int width, int height) {
		zoomRect(rect, (float) DMFaceCameraUtil.widthFaceFrame / width
				,(float) DMFaceCameraUtil.heightFaceFrame / height);
	}

	public static void zoomRect(Rect rect, float ratioWidth,float ratioHeight) {
		rect.left *= ratioWidth;
		rect.top *= ratioHeight;
		rect.right *= ratioWidth;
		rect.bottom *= ratioHeight;
	}

	public static void parseFacesToCanvas(SurfaceView surfaceView, List<FaceRect> faceRects, int _width, int _height, String username) {
		if (surfaceView != null) {
			Matrix mScaleMatrix = new Matrix();
			Canvas canvas = null;
			try {
				canvas = surfaceView.getHolder().lockCanvas();
				if (null != canvas) {
					canvas.drawColor(0, PorterDuff.Mode.CLEAR);
					canvas.setMatrix(mScaleMatrix);

					if (null != faceRects && faceRects.size() > 0) {
						for (FaceRect faceRect : faceRects) {
							drawFaceRect(canvas, faceRect, _width, _height,
									false, true);
						}
					}
				}
			}
			catch (IllegalStateException ise) {
				Log.e(TAG,"Surface has already been released!");
			}
			catch (Exception e) {
				Log.e(TAG,"canvas.draw:e:" + e.getLocalizedMessage());
			}
			finally {
				try {
					if (canvas != null) {
						surfaceView.getHolder().unlockCanvasAndPost(canvas);
					}
				}
				catch (Exception ve) {
					Log.e(TAG,"canvas.draw:ve:" + ve.getLocalizedMessage());
				}
			}
		}
	}

	public static Bitmap nv21ToBitmap(byte[] nv21, int width, int height) {
		Bitmap bitmap = null;
		try {
			YuvImage image = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			image.compressToJpeg(new Rect(0, 0, width, height), 80, stream);
			bitmap = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
			stream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return bitmap;
	}

	public static void saveBitmapToFile(Bitmap bitmap, String name) {
		if (bitmap == null) return;
		File dir = new File("/sdcard/FaceDebug/");
		if (!dir.exists()) {
			dir.mkdirs();
		}
		File file = new File(dir, name + ".jpg");
		java.io.FileOutputStream fos = null;
		try {
			fos = new java.io.FileOutputStream(file);
			bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
			fos.flush();
			Log.d("FaceUtil", "Debug image saved: " + file.getAbsolutePath());
		} catch (Exception e) {
			Log.e("FaceUtil", "Error saving debug image: " + e.getMessage());
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

}
