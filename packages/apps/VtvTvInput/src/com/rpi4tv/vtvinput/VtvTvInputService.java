/*
 * TvInputService that plays the VTV live HLS streams listed in
 * VtvChannels. Playback (MediaPlayer + mirror-URL fallback) lives in
 * VtvPlayer, shared with the standalone VtvPlayerActivity.
 */
package com.rpi4tv.vtvinput;

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.util.Log;
import android.view.Surface;

public class VtvTvInputService extends TvInputService {
    private static final String TAG = "VtvTvInput";

    @Override
    public Session onCreateSession(String inputId) {
        return new VtvSession(this);
    }

    private class VtvSession extends Session implements VtvPlayer.Listener {
        private final Context mContext;
        private final VtvPlayer mPlayer = new VtvPlayer(this);

        VtvSession(Context context) {
            super(context);
            mContext = context;
        }

        @Override
        public void onRelease() {
            mPlayer.release();
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            mPlayer.setSurface(surface);
            return true;
        }

        @Override
        public void onSetStreamVolume(float volume) {
            mPlayer.setVolume(volume);
        }

        @Override
        public boolean onTune(Uri channelUri) {
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
            VtvChannels.ChannelDef def = VtvChannels.findByNumber(queryDisplayNumber(channelUri));
            if (def == null) {
                Log.w(TAG, "onTune: no VTV channel matches " + channelUri);
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                return false;
            }
            mPlayer.tune(def.streamUrls);
            return true;
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
            // No caption track available on these streams.
        }

        @Override
        public void onPrepared() {
            notifyVideoAvailable();
        }

        @Override
        public void onAllMirrorsFailed() {
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
        }

        private String queryDisplayNumber(Uri channelUri) {
            try (Cursor cursor = mContext.getContentResolver().query(channelUri,
                    new String[] {TvContract.Channels.COLUMN_DISPLAY_NUMBER}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    return cursor.getString(0);
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Failed to query channel " + channelUri, e);
            }
            return null;
        }
    }
}
