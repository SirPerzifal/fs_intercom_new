package io.ionic.starter.plugin;

import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.content.Context;
import android.util.Log;
import android.media.AudioManager;
import android.media.MediaPlayer;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.PluginMethod;

@CapacitorPlugin(name = "Ringtone")
public class RingtonePlugin extends Plugin {

    private Ringtone ringtone;
    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;

    @Override
    public void load() {
        audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
    }

    @PluginMethod
    public void playOutgoing(PluginCall call) {
        Log.e("RingtonePlugin", "playOutgoing() dipanggil");

        Context context = getContext();
        int resID = context.getResources().getIdentifier("outgoing_call", "raw", context.getPackageName());

        if (resID == 0) {
            call.reject("Audio resource not found");
            return;
        }

        mediaPlayer = MediaPlayer.create(context, resID);
        if (mediaPlayer != null) {
            mediaPlayer.setLooping(true);
            mediaPlayer.start();
            call.resolve();
        } else {
            call.reject("MediaPlayer gagal dibuat");
        }
    }

    @PluginMethod
    public void stopOutgoing(PluginCall call) {
        Log.e("RingtonePlugin", "stopOutgoing() dipanggil");
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        call.resolve();
    }

    @PluginMethod
    public void setSpeakerOn(PluginCall call) {
        Log.e("RingtonePlugin", "set speaker on");
        if (audioManager != null) {
            Log.e("RingtonePlugin", "masuk ke if");
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(true);
            call.resolve();
        } else {
            Log.e("RingtonePlugin", "masuk ke else");
            call.reject("AudioManager not available");
        }
    }

    @PluginMethod
    public void setEarpieceOn(PluginCall call) {
        Log.e("RingtonePlugin", "set earpiece on");
        if (audioManager != null) {
            Log.e("RingtonePlugin", "masuk ke if");
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(false);
            call.resolve();
        } else {
            Log.e("RingtonePlugin", "masuk ke else");
            call.reject("AudioManager not available");
        }
    }

   @PluginMethod
    public void play(PluginCall call) {
        Log.e("RingtonePlugin", "play() dipanggil");
        Context context = getContext();
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        ringtone = RingtoneManager.getRingtone(context, notification);
        if (ringtone != null && !ringtone.isPlaying()) {
            ringtone.play();
        }
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        Log.e("RingtonePlugin", "stop() dipanggil");
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
        call.resolve();
    }
}
