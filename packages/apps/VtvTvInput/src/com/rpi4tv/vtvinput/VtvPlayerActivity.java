/*
 * Fullscreen player for a single VTV channel, launched from
 * VtvChannelListActivity. CHANNEL_UP/DOWN (or DPAD_LEFT/RIGHT, since most
 * remotes/keyboards lack dedicated channel keys) steps through VtvChannels
 * without leaving the player.
 */
package com.rpi4tv.vtvinput;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class VtvPlayerActivity extends Activity implements VtvPlayer.Listener, SurfaceHolder.Callback {
    public static final String EXTRA_CHANNEL_NUMBER = "channel_number";

    private static final long OVERLAY_HIDE_DELAY_MS = 3000;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHideOverlay = this::hideOverlay;

    private VtvPlayer mPlayer;
    private TextView mOverlay;
    private VtvChannels.ChannelDef mCurrentChannel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        mOverlay = findViewById(R.id.channel_overlay);
        SurfaceView surfaceView = findViewById(R.id.surface_view);
        surfaceView.getHolder().addCallback(this);

        mPlayer = new VtvPlayer(this);

        String number = getIntent().getStringExtra(EXTRA_CHANNEL_NUMBER);
        tuneTo(number != null ? number : VtvChannels.ALL[0].number);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mHideOverlay);
        mPlayer.release();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                stepChannel(1);
                return true;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                stepChannel(-1);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void stepChannel(int delta) {
        if (mCurrentChannel == null) {
            return;
        }
        int index = VtvChannels.indexOf(mCurrentChannel.number);
        if (index < 0) {
            return;
        }
        int next = (index + delta + VtvChannels.ALL.length) % VtvChannels.ALL.length;
        tuneTo(VtvChannels.ALL[next].number);
    }

    private void tuneTo(String number) {
        VtvChannels.ChannelDef def = VtvChannels.findByNumber(number);
        if (def == null) {
            def = VtvChannels.ALL[0];
        }
        mCurrentChannel = def;
        showOverlay(def);
        mPlayer.tune(def.streamUrls);
    }

    private void showOverlay(VtvChannels.ChannelDef def) {
        mOverlay.setText(def.number + "   " + def.name);
        mOverlay.setVisibility(View.VISIBLE);
        mHandler.removeCallbacks(mHideOverlay);
        mHandler.postDelayed(mHideOverlay, OVERLAY_HIDE_DELAY_MS);
    }

    private void hideOverlay() {
        mOverlay.setVisibility(View.GONE);
    }

    @Override
    public void onPrepared() {
        // Playback started; overlay already showing/timed to hide itself.
    }

    @Override
    public void onAllMirrorsFailed() {
        String name = mCurrentChannel != null ? mCurrentChannel.name : "";
        Toast.makeText(this, getString(R.string.channel_unavailable, name), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mPlayer.setSurface(holder.getSurface());
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mPlayer.setSurface(null);
    }
}
