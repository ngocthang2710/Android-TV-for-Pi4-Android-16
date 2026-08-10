/*
 * Static lineup of the free-to-air VTV national channels, as public HLS
 * (m3u8) streams. Each channel lists a primary CDN URL plus one fallback,
 * tried in order by VtvTvInputService if the primary fails.
 *
 * URLs sourced from the public, community-maintained iptv-org playlist
 * (github.com/iptv-org/iptv, streams/vn.m3u), which aggregates freely
 * available, non-DRM live streams of terrestrial free-to-air channels.
 * CDN endpoints for IPTV mirrors change over time; if a channel stops
 * loading, update its URL(s) here.
 */
package com.rpi4tv.vtvinput;

public final class VtvChannels {

    public static final class ChannelDef {
        public final String number;
        public final String name;
        public final String[] streamUrls;

        ChannelDef(String number, String name, String... streamUrls) {
            this.number = number;
            this.name = name;
            this.streamUrls = streamUrls;
        }
    }

    public static final ChannelDef[] ALL = {
        channel("1", "VTV1", "vtv1"),
        channel("2", "VTV2", "vtv2"),
        channel("3", "VTV3", "vtv3"),
        channel("4", "VTV4", "vtv4"),
        channel("5", "VTV5", "vtv5"),
        new ChannelDef("6", "VTV6",
                "https://live-a.fptplay53.net/live/media/vtv6/live247-hls-avc/index.m3u8",
                "https://vtvgolive-failover.vtvdigital.vn/vtvgo/vtv6tt-manifest.m3u8"),
        channel("7", "VTV7", "vtv7"),
        channel("8", "VTV8", "vtv8"),
        channel("9", "VTV9", "vtv9"),
    };

    /** Builds the common case: fpt CDN primary + vtvdigital failover, same slug on both. */
    private static ChannelDef channel(String number, String name, String slug) {
        return new ChannelDef(number, name,
                "https://live-a.fptplay53.net/live/media/" + slug + "/live247-hls-avc/index.m3u8",
                "https://vtvgolive-failover.vtvdigital.vn/vtvgo/" + slug + "-manifest.m3u8");
    }

    public static ChannelDef findByNumber(String number) {
        if (number == null) {
            return null;
        }
        for (ChannelDef def : ALL) {
            if (def.number.equals(number)) {
                return def;
            }
        }
        return null;
    }

    /** Index into ALL for the given channel number, or -1 if not found. */
    public static int indexOf(String number) {
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i].number.equals(number)) {
                return i;
            }
        }
        return -1;
    }

    private VtvChannels() {}
}
