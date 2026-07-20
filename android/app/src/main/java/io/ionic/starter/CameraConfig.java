package io.ionic.starter;

public class CameraConfig {

    private static boolean isVerticalCamera = true;

    private static int orientation = isVerticalCamera ? 270 : 180;//
    public static int width = isVerticalCamera ? 480 : 640;
    public static int height = isVerticalCamera ? 640 : 480;

    public static int getOrientation() {
        return orientation;
    }

    public static int getWidth() {
        if (orientation == 90 || orientation == 270)
            return height;
        return width;
    }

    public static int getHeight() {
        if (orientation == 90 || orientation == 270)
            return width;
        return height;
    }
}
