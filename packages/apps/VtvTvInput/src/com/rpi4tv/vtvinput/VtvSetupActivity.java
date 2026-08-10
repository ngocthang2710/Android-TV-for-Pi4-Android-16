/*
 * Setup activity launched by TvProvision / the Live Channels app the first
 * time this input is configured. There is nothing to scan for — the
 * lineup is the fixed VTV1-VTV9 list in VtvChannels — so this just writes
 * those channels into the TvProvider for this input and returns.
 */
package com.rpi4tv.vtvinput;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class VtvSetupActivity extends Activity {
    private static final String TAG = "VtvTvInput";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String inputId = getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
        if (inputId == null) {
            Log.e(TAG, "Setup started without EXTRA_INPUT_ID");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        int count = addChannels(inputId);
        Toast.makeText(this, getString(R.string.setup_done, count), Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }

    private int addChannels(String inputId) {
        ContentResolver resolver = getContentResolver();
        // Drop whatever this input previously registered so re-running setup
        // (e.g. after editing VtvChannels) doesn't leave duplicate rows.
        resolver.delete(TvContract.buildChannelsUriForInput(inputId), null, null);

        int inserted = 0;
        for (VtvChannels.ChannelDef def : VtvChannels.ALL) {
            ContentValues values = new ContentValues();
            values.put(TvContract.Channels.COLUMN_INPUT_ID, inputId);
            values.put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_OTHER);
            values.put(TvContract.Channels.COLUMN_SERVICE_TYPE,
                    TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO);
            values.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, def.number);
            values.put(TvContract.Channels.COLUMN_DISPLAY_NAME, def.name);
            values.put(TvContract.Channels.COLUMN_SEARCHABLE, 1);
            // COLUMN_BROWSABLE is deliberately not set here: TvProvider only
            // lets callers holding the privileged ACCESS_ALL_EPG_DATA
            // permission write it (see TvProvider#blockIllegalAccessToChannelsSystemColumns).
            // That's the Live Channels app's job, not the input's — it flips
            // browsable=1 for this input's channels as part of its own
            // "set up channel source" flow after this setup activity returns.
            if (resolver.insert(TvContract.Channels.CONTENT_URI, values) != null) {
                inserted++;
            }
        }
        Log.i(TAG, "Inserted " + inserted + " VTV channels for input " + inputId);
        return inserted;
    }
}
