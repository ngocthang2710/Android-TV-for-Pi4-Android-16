/*
 * YouTube for this device: a WebView wrapper around
 * https://www.youtube.com/tv, YouTube's own official web client built for
 * TV remotes (no GMS/Play Services on this AOSP build, so the real
 * closed-source YouTube app isn't buildable/installable here -- see the
 * AndroidManifest.xml comment).
 */
package com.rpi4tv.youtubetv;

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
    // How long a 2nd BACK press (with nowhere left to go) has to follow the
    // 1st one to count as "press back again to exit" -- see onKeyDown().
    // Only applies when mJustForcedHome is false (see that field).
    private static final long EXIT_ON_DOUBLE_BACK_WINDOW_MS = 800;

    private WebView mWebView;
    private ProgressBar mLoadingSpinner;
    private String mHomeUrl;
    private long mLastBackPressUptimeMs = -1;
    // True right after a BACK press force-navigates to the home URL (see
    // onKeyDown()) -- a real, unambiguous signal that the page is now
    // showing youtube.com/tv's actual home screen. Lets the very next BACK
    // press exit in one more press instead of needing the double-back
    // safety net below: user feedback was that needing several presses
    // total to actually leave the app felt broken. Reset to false by
    // anything else that consumes a BACK press, since it's only
    // trustworthy immediately after that forced navigation.
    private boolean mJustForcedHome;

    // Fullscreen HTML5 <video> support (WebChromeClient#onShowCustomView):
    // the player can request the browser's own fullscreen mode on top of
    // the page, which needs a dedicated overlay view to render into.
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
        // youtube.com/tv branches on user agent to decide whether to serve
        // its TV-remote-friendly leanback UI instead of the mobile/desktop
        // site; append a TV hint to the WebView's normal Chrome UA rather
        // than replacing it outright, so the rest of the UA (Chrome/WebView
        // version) stays accurate for YouTube's own compatibility checks.
        settings.setUserAgentString(settings.getUserAgentString() + " SMART-TV; ATV");

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
            // No shouldOverrideUrlLoading override: every link YouTube's own
            // TV UI produces (search, watch, sign-in, channel pages) should
            // stay inside this WebView rather than bouncing out to a system
            // browser that doesn't exist as a user-facing app here.
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

        mHomeUrl = getString(R.string.youtube_tv_url);
        mWebView.loadUrl(mHomeUrl);
        mWebView.requestFocus();
    }

    /**
     * BACK is meant to go to YouTube's home screen first, only exiting once
     * already there, same as the real app. Layers deep to actually get
     * there, in the order they were found on-device:
     *
     * <p>1. android:enableOnBackInvokedCallback="false" in
     * AndroidManifest.xml. Without it the platform's default
     * predictive-back callback intercepts BACK before onKeyDown() below
     * ever sees it and just finishes the Activity outright.
     *
     * <p>2. {@link WebView#canGoBack()} / {@link WebView#goBack()} (and
     * even jumping several steps at once via
     * {@link WebView#goBackOrForward}) turned out to be the wrong tool
     * entirely for this specific site, not just slow: youtube.com/tv is a
     * single-page app, and its client router doesn't reliably repaint the
     * page to match the history entry after an externally-triggered
     * multi-step history jump -- confirmed on-device repeatedly with
     * generous waits (up to 6s): the URL/WebBackForwardList updated
     * correctly but the visible page kept showing the video. This wasn't a
     * one-off; it reproduced across multiple attempts with the same code.
     *
     * <p>The reliable fix: don't touch WebView history at all. Compare the
     * current URL against the home URL ({@link #isHomeUrl}) and, if not
     * home, force a real {@link WebView#loadUrl} to the home URL -- a full
     * navigation the page can't fail to react to, unlike a history replay
     * the SPA's own router has to notice and handle itself.
     *
     * <p>That still leaves the "Add your Google Account"-style case (a
     * client-side overlay with no URL change at all) needing something
     * else: Escape, the convention TV-oriented JS apps listen for from a
     * remote's Back button, dispatched via {@link #dispatchEscapeToPage}.
     * Since there's no reliable native signal there for "nothing left to
     * go back to", that path keeps a double-back-to-exit safety net --
     * except right after this method itself already forced the home
     * navigation, which IS unambiguous; see {@link #mJustForcedHome}.
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
            // The previous BACK press already forced this exact page load;
            // this one exits immediately, no double-tap needed.
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

    /**
     * True if {@code url} is youtube.com/tv's home screen. youtube.com/tv
     * uses hash-based client-side routing ("#/watch?v=...",
     * "#/surface?...", etc.) rather than distinct URL paths, so the home
     * screen is identified by an empty or "/" fragment, not just a prefix
     * match on the base URL -- a naive prefix match would also match every
     * sub-page, since they all share the same base URL before the "#".
     */
    private boolean isHomeUrl(String url) {
        if (url == null) {
            return true;
        }
        final int hashIndex = url.indexOf('#');
        final String base = hashIndex >= 0 ? url.substring(0, hashIndex) : url;
        final String fragment = hashIndex >= 0 ? url.substring(hashIndex + 1) : "";
        final String normalizedBase = base.endsWith("/")
                ? base.substring(0, base.length() - 1) : base;
        return normalizedBase.equals(mHomeUrl) && (fragment.isEmpty() || fragment.equals("/"));
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
