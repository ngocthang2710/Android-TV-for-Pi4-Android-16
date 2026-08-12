/*
 * Netflix for this device: a WebView wrapper around
 * https://www.netflix.com (no GMS/Play Services on this AOSP build, and
 * likely no full Widevine DRM CDM either -- see the AndroidManifest.xml
 * comment). Reuses everything learned building YouTubeTv/SpotifyTv/
 * PlayStoreTv (predictive-back opt-out, force-navigate-to-home BACK,
 * Escape-key fallback, double-back-to-exit, path-based isHomeUrl) rather
 * than rediscovering those bugs again here.
 */
package com.rpi4tv.netflixtv;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final long EXIT_ON_DOUBLE_BACK_WINDOW_MS = 800;

    private WebView mWebView;
    private ProgressBar mLoadingSpinner;
    private String mHomeUrl;
    private long mLastBackPressUptimeMs = -1;
    private boolean mJustForcedHome;

    private View mFullscreenView;
    private WebChromeClient.CustomViewCallback mFullscreenCallback;
    private FrameLayout mFullscreenContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mWebView = findViewById(R.id.web_view);
        mLoadingSpinner = findViewById(R.id.loading_spinner);
        mFullscreenContainer = new FrameLayout(this);
        mFullscreenContainer.setBackgroundColor(0xFF000000);
        addContentView(mFullscreenContainer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mFullscreenContainer.setVisibility(View.GONE);

        final WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(mWebView, true);

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                mLoadingSpinner.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                mLoadingSpinner.setVisibility(View.GONE);
            }
        });

        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (mFullscreenView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                mFullscreenView = view;
                mFullscreenCallback = callback;
                mFullscreenContainer.addView(view, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                mFullscreenContainer.setVisibility(View.VISIBLE);
                mWebView.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (mFullscreenView == null) {
                    return;
                }
                mFullscreenContainer.removeView(mFullscreenView);
                mFullscreenContainer.setVisibility(View.GONE);
                mWebView.setVisibility(View.VISIBLE);
                mFullscreenView = null;
                if (mFullscreenCallback != null) {
                    mFullscreenCallback.onCustomViewHidden();
                    mFullscreenCallback = null;
                }
            }
        });

        mHomeUrl = getString(R.string.netflix_url);
        mWebView.loadUrl(mHomeUrl);
        mWebView.requestFocus();
    }

    /**
     * See YouTubeTv's MainActivity#onKeyDown() for the full reasoning.
     * {@link #isHomeUrl} is path-based like SpotifyTv/PlayStoreTv's
     * (netflix.com uses normal path routing -- "/browse", "/title/...",
     * "/search", "/login" -- not hash-based routing).
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_BACK) {
            return super.onKeyDown(keyCode, event);
        }
        if (mFullscreenView != null) {
            mWebView.getWebChromeClient().onHideCustomView();
            mLastBackPressUptimeMs = -1;
            mJustForcedHome = false;
            return true;
        }

        if (!isHomeUrl(mWebView.getUrl())) {
            mWebView.loadUrl(mHomeUrl);
            mLastBackPressUptimeMs = -1;
            mJustForcedHome = true;
            return true;
        }

        if (mJustForcedHome) {
            mJustForcedHome = false;
            return super.onKeyDown(keyCode, event);
        }

        final long now = SystemClock.uptimeMillis();
        if (mLastBackPressUptimeMs >= 0
                && now - mLastBackPressUptimeMs <= EXIT_ON_DOUBLE_BACK_WINDOW_MS) {
            return super.onKeyDown(keyCode, event);
        }
        mLastBackPressUptimeMs = now;
        dispatchEscapeToPage();
        Toast.makeText(this, "Press Back again to exit", Toast.LENGTH_SHORT).show();
        return true;
    }

    private boolean isHomeUrl(String url) {
        if (url == null) {
            return true;
        }
        String noFragment = url.contains("#") ? url.substring(0, url.indexOf('#')) : url;
        String noQuery = noFragment.contains("?")
                ? noFragment.substring(0, noFragment.indexOf('?')) : noFragment;
        final String normalized = noQuery.endsWith("/")
                ? noQuery.substring(0, noQuery.length() - 1) : noQuery;
        final String normalizedHome = mHomeUrl.endsWith("/")
                ? mHomeUrl.substring(0, mHomeUrl.length() - 1) : mHomeUrl;
        return normalized.equals(normalizedHome);
    }

    private void dispatchEscapeToPage() {
        final long now = SystemClock.uptimeMillis();
        mWebView.dispatchKeyEvent(
                new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE, 0));
        mWebView.dispatchKeyEvent(
                new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE, 0));
    }

    @Override
    protected void onDestroy() {
        mWebView.destroy();
        super.onDestroy();
    }
}
