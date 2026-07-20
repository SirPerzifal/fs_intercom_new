package io.ionic.starter.serialportdemo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.List;

import android.os.Build;

import com.thinmoo.facerecognition.FaceClient;
import com.thinmoo.facerecognition.facedb.FaceTemplateDao;
import com.thinmoo.facerecognition.facedb.FaceTemplateDom;

public class ImageApiService {
    private static final String TAG = "ImageApiService";
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 120000;
    private static final int TIMEOUT_MS = 60000;

    private final String baseUrl;
     private String serverBaseUrl;
    private final Context context;
    private ImageApiListener listener;
    private String intercomId;

    public interface ImageApiListener {
        void onImageInfoReceived(ImageInfo imageInfo);
        void onImageReceived(Bitmap image);
        void onFaceDataReceived(Bitmap image, String userId, String imageName);
        void onMultipleFacesReceived(List<FaceRegistrationData> faceDataList);
        void onSDKKeyReceived(String key);
        void onError(String error);
        void onRetryAttempt(int attemptNumber, int maxRetries);
    }

    public static class ImageInfo {
        public String imageUrl;
        public String imageName;
        public String metadata;
        public long timestamp;
        public String userId;

        public ImageInfo(String imageUrl, String imageName, String metadata, long timestamp, String userId) {
            this.imageUrl = imageUrl;
            this.imageName = imageName;
            this.metadata = metadata;
            this.timestamp = timestamp;
            this.userId = userId;
        }
    }

    public static class FaceRegistrationData {
        public ImageInfo imageInfo;
        public Bitmap bitmap;
        public String userId;
        public String imageName;

        public FaceRegistrationData(ImageInfo info, Bitmap bitmap, String userId, String imageName) {
            this.imageInfo = info;
            this.bitmap = bitmap;
            this.userId = userId;
            this.imageName = imageName;
        }
    }

    public ImageApiService(String baseUrl, Context context) {
        this.baseUrl = baseUrl;
        this.context = context;
    }

    public void setListener(ImageApiListener listener) {
        this.listener = listener;
    }

    public void setIntercomId(String intercomId) {
        this.intercomId = intercomId;
    }

    public void getImageInfoAndDownload() {
        new GetImageInfoAndDownloadTask().execute(); 
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

    public void getSDKKey() {
        new GetSDKKeyTask().execute();
    }

    // private class GetImageInfoTask extends AsyncTask<Void, Void, String> {
    private class GetImageInfoAndDownloadTask extends AsyncTask<Void, Integer, List<FaceRegistrationData>> {
        private String lastError;

        @Override
        protected List<FaceRegistrationData> doInBackground(Void... voids) {
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    publishProgress(attempt);

                    String endpoint = baseUrl + "/api/image-info?serial_number=" + getDeviceId() ;
                    URL url = new URL(endpoint);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(TIMEOUT_MS);
                    connection.setReadTimeout(TIMEOUT_MS);
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("User-Agent", "AndroidApp/1.0");

                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "Response code: " + responseCode + " (Attempt " + attempt + ")");

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;

                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();

                        JSONObject responseObject = new JSONObject(response.toString());
                        JSONArray jsonArray = responseObject.getJSONArray("result");
                        JSONArray jsonArrayToDelete = responseObject.getJSONArray("users_to_delete");

                        List<FaceRegistrationData> faceDataList = new ArrayList<>();
                        Log.d(TAG, "Complete response: " + responseObject.toString());

                        Log.d(TAG, "Received " + jsonArray.length() + " face records from server");

                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);

                            int id = jsonObject.getInt("id");
                            String imageBase64 = jsonObject.getString("image");
                            String userId = String.valueOf(id);
                            String imageName = jsonObject.optString("imageName", "person_" + id + ".jpg");


                            // Create ImageInfo object
                            ImageInfo imageInfo = new ImageInfo(
                                    jsonObject.optString("imageUrl", ""),
                                    imageName,
                                    jsonObject.optString("metadata", ""),
                                    jsonObject.optLong("timestamp", System.currentTimeMillis()),
                                    userId
                            );

                            // Decode Base64 to Bitmap
                            try {
                                Log.d(TAG, "Base64 length: " + imageBase64.length());
                                Log.d(TAG, "Base64 starts with: " + imageBase64.substring(0, Math.min(50, imageBase64.length())));

                                byte[] decodedBytes = Base64.decode(imageBase64, Base64.DEFAULT);
                                Log.d(TAG, "Decoded bytes length: " + decodedBytes.length);
                                Log.d(TAG, "First 10 decoded bytes: " + Arrays.toString(Arrays.copyOf(decodedBytes, Math.min(10, decodedBytes.length))));

                                // Check what BitmapFactory thinks about this data
                                BitmapFactory.Options options = new BitmapFactory.Options();
                                options.inJustDecodeBounds = true; // Don't load the bitmap, just get info
                                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length, options);
                                Log.d(TAG, "Image info - width: " + options.outWidth + ", height: " + options.outHeight + ", mime: " + options.outMimeType);

                                if (options.outWidth <= 0 || options.outHeight <= 0) {
                                    Log.e(TAG, "Invalid image dimensions detected");
                                } else {
                                    // Try to decode the actual bitmap
                                    Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                                    if (bitmap != null) {
                                        Log.d(TAG, "Bitmap created successfully: " + bitmap.getWidth() + "x" + bitmap.getHeight());
                                        faceDataList.add(new FaceRegistrationData(imageInfo, bitmap, userId, imageName));
                                    } else {
                                        Log.e(TAG, "BitmapFactory.decodeByteArray returned null despite valid dimensions");
                                    }
                                }

                            } catch (Exception e) {
                                Log.e(TAG, "Error decoding image for user " + userId + ": " + e.getMessage(), e);
                            }
                        }


                        for (int i = 0; i < jsonArrayToDelete.length(); i++) {
                            JSONObject jsonObjectToDelete = jsonArrayToDelete.getJSONObject(i);

                            String id = jsonObjectToDelete.getString("id");

                            FaceTemplateDao dao = new FaceTemplateDao(context);
                            dao.deleteTemplateByUserID(id);
                            FaceClient.getInstance().delByUserID(id);


                        }

                        connection.disconnect();
                        // if (!faceDataList.isEmpty()) {
                        //     return faceDataList;
                        // } else {
                        //     lastError = "No valid images found in response";
                        //     Log.e(TAG, lastError);
                        // }
                        // Return the faceDataList (even if empty) to terminate the AsyncTask retry loop on successful HTTP 200 response
                        if (faceDataList.isEmpty()) {
                            lastError = "No new face records on server";
                        }
                        return faceDataList;

                    } else {
                        lastError = "Server returned error code: " + responseCode;
                        Log.w(TAG, lastError + " (Attempt " + attempt + ")");
                    }

                    connection.disconnect();

                } catch (Exception e) {
                    lastError = "Network error: " + e.getMessage();
                    Log.e(TAG, "Error on attempt " + attempt, e);
                }

                // Wait before retry (except on last attempt)
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            Log.e(TAG, "All the attempt has failed");
            return null; // All attempts failed
        }

        /*
        @Override
        protected String doInBackground(Void... voids) {
            HttpURLConnection connection = null;
            try {
                // URL url = new URL(baseUrl + "/api/image-info?serial_number=" + intercomId);
                URL url = new URL(baseUrl + "/api/image-info?serial_number=" + getDeviceId() );
                // String endpoint = serverBaseUrl + "/api/image-info?serial_number=" + getDeviceId()
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "AndroidApp/1.0");

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Response code: " + responseCode);
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    return response.toString();
                } else {
                    return "Error: " + responseCode;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching face list", e);
                return "Error: " + e.getMessage();
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
            */

        @Override
        protected void onProgressUpdate(Integer... values) {
            int attemptNumber = values[0];
            Log.e(TAG, "What is the attempt number" + attemptNumber);
            if (listener != null) {
                listener.onRetryAttempt(attemptNumber, MAX_RETRIES);
            }
        }

        @Override
        protected void onPostExecute(List<FaceRegistrationData> faceDataList) {
            if (listener != null) {
                if (faceDataList != null && !faceDataList.isEmpty()) {
                    Log.d(TAG, "Processing " + faceDataList.size() + " faces for registration");

                    // Call the new bulk registration method
                    listener.onMultipleFacesReceived(faceDataList);

                    // For backward compatibility, also call individual methods for the first item
                    FaceRegistrationData firstFace = faceDataList.get(0);
                    listener.onImageInfoReceived(firstFace.imageInfo);
                    listener.onImageReceived(firstFace.bitmap);
                    listener.onFaceDataReceived(firstFace.bitmap, firstFace.userId, firstFace.imageName);

                    Log.e(TAG, "i finished this checking");
                } else {
                    listener.onError("Failed to complete image retrieval. " + lastError);
                }
            }
        }

        /*
        @Override
        protected void onPostExecute(String result) {
            if (result == null || result.startsWith("Error")) {
                if (listener != null) listener.onError(result);
                return;
            }

            try {
                JSONObject root = new JSONObject(result);
                JSONArray facesArray = root.optJSONArray("result");
                if (facesArray == null) {
                    if (listener != null) listener.onError("Invalid JSON structure: 'result' array missing");
                    return;
                }

                List<FaceRegistrationData> faceDataList = new ArrayList<>();
                for (int i = 0; i < facesArray.length(); i++) {
                    JSONObject faceObj = facesArray.getJSONObject(i);
                    String userId = faceObj.getString("userId");
                    String imageName = faceObj.getString("imageName");
                    String imageUrl = faceObj.getString("imageUrl");

                    // Download image synchronously within this AsyncTask background thread
                    Bitmap bitmap = downloadBitmap(imageUrl);
                    if (bitmap != null) {
                        faceDataList.add(new FaceRegistrationData(bitmap, userId, imageName));
                    } else {
                        Log.e(TAG, "Failed to download image for user: " + userId);
                    }
                }

                if (listener != null) {
                    listener.onMultipleFacesReceived(faceDataList);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing face list JSON", e);
                if (listener != null) listener.onError("Parsing error: " + e.getMessage());
            }
        }

        private Bitmap downloadBitmap(String urlStr) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlStr);
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                return BitmapFactory.decodeStream(input);
            } catch (Exception e) {
                Log.e(TAG, "Error downloading bitmap: " + urlStr, e);
                return null;
            } finally {
                if (connection != null) connection.disconnect();
            }
        } */
    }

    private class GetSDKKeyTask extends AsyncTask<Void, Void, String> {
        // ---------------- Change for rebooting issue
        @Override
        protected String doInBackground(Void... voids) {
            int maxAttempts = 10;
            long retryDelayMs = 15000; // 15 seconds

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                HttpURLConnection connection = null;
                try {
                    String serialNum = getDeviceId();
                    String endpoint = baseUrl + "/api/sdkkey?serial_number=" + serialNum;
                    Log.d(TAG, "SUPER LOG: Fetching SDK key from URL: " + endpoint + " (Attempt " + attempt + ")");
                    URL url = new URL(endpoint);

                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);

                    int responseCode = connection.getResponseCode();
                    Log.d(TAG, "SDK key response code: " + responseCode + " (Attempt " + attempt + ")");

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        StringBuilder responseStrBuilder = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            responseStrBuilder.append(line);
                        }
                        reader.close();
                        String responseString = responseStrBuilder.toString();
                        Log.d(TAG, "SUPER LOG: Server Response String: " + responseString);

                        if (!responseString.isEmpty()) {
                            JSONObject root = new JSONObject(responseString);
                            String fetchedKey = root.optString("key", "");
                            Log.d(TAG, "SUPER LOG: Parsed key: " + fetchedKey);
                            if (fetchedKey != null && !fetchedKey.isEmpty()) {
                                return fetchedKey;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error fetching SDK key on attempt " + attempt + ": " + e.getMessage());
                } finally {
                    if (connection != null) connection.disconnect();
                }

                // Wait before retrying (except on the last attempt)
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            Log.e(TAG, "All attempts to fetch SDK key have failed.");
            return null;
        }
        // ---------------- end ofChange for rebooting issue


        @Override
        protected void onPostExecute(String key) {
            if (key != null && !key.isEmpty() && listener != null) {
                listener.onSDKKeyReceived(key);
            }
        }
    }
}
