/*
 * Thin wrapper around MediaPlayer that tunes to a channel's list of mirror
 * URLs, walking to the next one on error. Shared by VtvTvInputService's
 * Session (the Live Channels / TvInputService path) and VtvPlayerActivity
 * (the standalone "tap the app icon, pick a channel" path) so the
 * playback/fallback logic only has to live in one place.
 */
package com.rpi4tv.vtvinput;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;

class VtvPlayer {
    interface Listener {
        void onPrepared();
        /** Called once every mirror URL for the current channel has failed. */
        void onAllMirrorsFailed();
    }

    private static final String TAG = "VtvTvInput";

    private final Listener mListener;
    private MediaPlayer mPlayer;
    private Surface mSurface;
    private float mVolume = 1f;
    private String[] mUrls;
    private int mUrlIndex;

    VtvPlayer(Listener listener) {
        mListener = listener;
    }

    void tune(String[] urls) {
        mUrls = urls;
        mUrlIndex = 0;
        playCurrentUrl();
    }

    void setSurface(Surface surface) {
        mSurface = surface;
        if (mPlayer != null) {
            mPlayer.setSurface(surface);
        }
    }

    void setVolume(float volume) {
        mVolume = volume;
        if (mPlayer != null) {
            mPlayer.setVolume(volume, volume);
        }
    }

    void release() {
        if (mPlayer != null) {
            mPlayer.setOnPreparedListener(null);
            mPlayer.setOnErrorListener(null);
            mPlayer.reset();
            mPlayer.release();
            mPlayer = null;
        }
    }

    private void playCurrentUrl() {
        if (mUrls == null || mUrlIndex >= mUrls.length) {
            Log.w(TAG, "playCurrentUrl: exhausted all mirrors");
            mListener.onAllMirrorsFailed();
            return;
        }
        String url = mUrls[mUrlIndex];
        Log.i(TAG, "Tuning to " + url);
        release();

        mPlayer = new MediaPlayer();
        mPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build());
        mPlayer.setOnPreparedListener(mp -> {
            mp.setVolume(mVolume, mVolume);
            mp.start();
            mListener.onPrepared();
        });
        mPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.w(TAG, "MediaPlayer error what=" + what + " extra=" + extra
                    + " url=" + url + ", trying next mirror");
            mUrlIndex++;
            playCurrentUrl();
            return true;
        });
        try {
            mPlayer.setDataSource(url);
            if (mSurface != null) {
                mPlayer.setSurface(mSurface);
            }
            mPlayer.prepareAsync();
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "setDataSource failed for " + url, e);
            mUrlIndex++;
            playCurrentUrl();
        }
    }
}
