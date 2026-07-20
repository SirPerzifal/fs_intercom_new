package io.ionic.starter;

public class Rotator {

    private static byte[] sharedRotated90Buffer;
    private static byte[] sharedRotated180Buffer;
    private static byte[] sharedRotated270Buffer;
    private static byte[] sharedRotated0Buffer;

    public static byte[] rotateYUV420Degree90(byte[] data, int imageWidth, int imageHeight) {
        int length = imageWidth * imageHeight * 3 / 2;
        if (sharedRotated90Buffer == null || sharedRotated90Buffer.length != length) {
            sharedRotated90Buffer = new byte[length];
        }
        byte[] yuv = sharedRotated90Buffer;
        
        // Rotate the Y luma
        int i = 0;
        for (int x = 0; x < imageWidth; x++) {
            for (int y = imageHeight - 1; y >= 0; y--) {
                yuv[i] = data[y * imageWidth + x];
                i++;
            }

        }

        // Rotate the U and V color components
        i = imageWidth * imageHeight * 3 / 2 - 1;
        for (int x = imageWidth - 1; x > 0; x = x - 2) {
            for (int y = 0; y < imageHeight / 2; y++) {
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + x];
                i--;
                yuv[i] = data[(imageWidth * imageHeight) + (y * imageWidth) + (x - 1)];
                i--;
            }
        }
        return yuv;
    }

    public static byte[] rotateYUV420Degree180(byte[] data, int imageWidth, int imageHeight) {
        int length = imageWidth * imageHeight * 3 / 2;
        if (sharedRotated180Buffer == null || sharedRotated180Buffer.length != length) {
            sharedRotated180Buffer = new byte[length];
        }
        byte[] yuv = sharedRotated180Buffer;
        
        int i;
        int count = 0;

        for (i = imageWidth * imageHeight - 1; i >= 0; i--) {
            yuv[count] = data[i];
            count++;
        }

        for (i = imageWidth * imageHeight * 3 / 2 - 1; i >= imageWidth
                * imageHeight; i -= 2) {
            yuv[count] = data[i - 1];
            count++;
            yuv[count] = data[i];
            count++;
        }
        return yuv;
    }

    public static byte[] rotateYUV420Degree270(byte[] data, int imageWidth, int imageHeight) {
        int length = imageWidth * imageHeight * 3 / 2;
        if (sharedRotated270Buffer == null || sharedRotated270Buffer.length != length) {
            sharedRotated270Buffer = new byte[length];
        }
        byte[] yuv = sharedRotated270Buffer;
        
        int wh = 0;
        int uvHeight = 0;
        if (imageWidth != 0 || imageHeight != 0) {
            wh = imageWidth * imageHeight;
            uvHeight = imageHeight >> 1;
        }

        int k = 0;
        for (int i = 0; i < imageWidth; i++) {
            int nPos = 0;
            for (int j = 0; j < imageHeight; j++) {
                yuv[k] = data[nPos + i];
                k++;
                nPos += imageWidth;
            }
        }

        for (int i = 0; i < imageWidth; i += 2) {
            int nPos = wh;
            for (int j = 0; j < uvHeight; j++) {
                yuv[k] = data[nPos + i];
                yuv[k + 1] = data[nPos + i + 1];
                k += 2;
                nPos += imageWidth;
            }
        }
        return rotateYUV420Degree180(yuv, imageWidth, imageHeight);
    }

    public static byte[] rotateYUV420Degree0(byte[] data, int imageWidth, int imageHeight) {
        int length = imageWidth * imageHeight * 3 / 2;
        if (sharedRotated0Buffer == null || sharedRotated0Buffer.length != length) {
            sharedRotated0Buffer = new byte[length];
        }
        byte[] yuv = sharedRotated0Buffer;
        
        int i;
        int count = 0;

        for (i = 0; i < imageWidth * imageHeight; i++) {
            yuv[count] = data[i];
            count++;
        }

        for (i = imageWidth * imageHeight * 3 / 2 - 1; i >= imageWidth
                * imageHeight; i -= 2) {
            yuv[count] = data[i - 1];
            count++;
            yuv[count] = data[i];
            count++;
        }
        return yuv;
    }
}
