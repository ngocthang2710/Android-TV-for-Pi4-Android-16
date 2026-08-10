/*
 * App's launcher entry point: a grid of VTV channel tiles. Tapping (or
 * D-pad OK on) one opens VtvPlayerActivity to play it directly, without
 * going through Live Channels/TvProvider at all.
 */
package com.rpi4tv.vtvinput;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

public class VtvChannelListActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel_list);

        GridLayout grid = findViewById(R.id.channel_grid);
        LayoutInflater inflater = LayoutInflater.from(this);
        View firstTile = null;
        for (VtvChannels.ChannelDef def : VtvChannels.ALL) {
            View tile = inflater.inflate(R.layout.item_channel_tile, grid, false);
            ((TextView) tile.findViewById(R.id.tile_number)).setText(def.number);
            ((TextView) tile.findViewById(R.id.tile_name)).setText(def.name);
            tile.setOnClickListener(v -> openChannel(def));

            // Keep the 280x160dp size item_channel_tile.xml's root already
            // declares (a fresh LayoutParams here would reset it to
            // wrap_content); just add the inter-tile spacing.
            GridLayout.LayoutParams params = (GridLayout.LayoutParams) tile.getLayoutParams();
            params.setMargins(16, 16, 16, 16);
            grid.addView(tile, params);

            if (firstTile == null) {
                firstTile = tile;
            }
        }
        if (firstTile != null) {
            firstTile.requestFocus();
        }
    }

    private void openChannel(VtvChannels.ChannelDef def) {
        Intent intent = new Intent(this, VtvPlayerActivity.class);
        intent.putExtra(VtvPlayerActivity.EXTRA_CHANNEL_NUMBER, def.number);
        startActivity(intent);
    }
}
