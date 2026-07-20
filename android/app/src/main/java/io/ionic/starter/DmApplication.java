package io.ionic.starter;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.webkit.WebView;
// No import needed for classes in the same package

/**
 * Created by thinmoo_cch on 2019/6/9.
 */

public class DmApplication extends Application {
    private static DmApplication instance = null;

    public static DmApplication getInstance() {
        return instance;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        applyWebViewBypass();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        applyWebViewBypass();
    }

    private void applyWebViewBypass() {
        // Log.d("DmApplication", "applyWebViewBypass started");
        
        // Reflection-based hook to bypass SYSTEM_UID block
        HookWebView.hookWebView();
        
        // =========================================================================
        // WORKAROUND FOR WEBRTC SYSTEM UID DLOPEN CRASH:
        // Preload libandroidicu.so to bypass dynamic linker namespace restrictions.
        // =========================================================================
        preloadIcuLibrary();
        // =========================================================================
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                WebView.setDataDirectorySuffix("fs_intercom"); 
                // Log.d("DmApplication", "WebView data directory suffix set to fs_intercom");
            } catch (Throwable e) {
                // Log.w("DmApplication", "Failed to set WebView data directory suffix", e);
            }
        }
    }

    // =========================================================================
    // HELPER TO PRELOAD SYSTEM ICU LIBRARY FOR WEBRTC/WEBVIEW
    // =========================================================================
    private void preloadIcuLibrary() {
        try {
            boolean is64Bit = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                is64Bit = android.os.Process.is64Bit();
            } else {
                String arch = System.getProperty("os.arch");
                if (arch != null && (arch.contains("64") || arch.contains("armv8") || arch.contains("aarch64"))) {
                    is64Bit = true;
                }
            }
            String libDir = is64Bit ? "lib64" : "lib";
            
            String[] paths = {
                "/apex/com.android.i18n/" + libDir + "/libandroidicu.so",
                "/apex/com.android.runtime/" + libDir + "/bionic/libandroidicu.so",
                "/system/" + libDir + "/libandroidicu.so"
            };
            
            boolean loaded = false;
            for (String path : paths) {
                java.io.File file = new java.io.File(path);
                if (file.exists()) {
                    try {
                        System.load(path);
                        Log.i("DmApplication", "Successfully preloaded ICU library from: " + path);
                        loaded = true;
                        break;
                    } catch (UnsatisfiedLinkError e) {
                        Log.w("DmApplication", "Failed to load candidate: " + path + " - " + e.getMessage());
                    }
                }
            }
            if (!loaded) {
                try {
                    System.loadLibrary("androidicu");
                    Log.i("DmApplication", "Successfully loaded androidicu via System.loadLibrary");
                } catch (UnsatisfiedLinkError e) {
                    Log.e("DmApplication", "Could not preload libandroidicu.so: " + e.getMessage());
                }
            }
        } catch (Throwable t) {
            Log.e("DmApplication", "Error in preloadIcuLibrary", t);
        }
    }
    // =========================================================================

}
