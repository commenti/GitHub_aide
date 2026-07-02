package com.adobs.ide.core.ads;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * Mediation-safe interstitial ad wrapper that enforces a strict
 * alternating gap-based monetization pattern:
 *
 * <ul>
 *   <li><b>Odd-numbered</b> calls to {@link #handleClick(Runnable)} show a
 *       preloaded interstitial; the supplied action is invoked only after the
 *       ad is dismissed or fails to show.</li>
 *   <li><b>Even-numbered</b> calls skip the ad entirely and invoke the action
 *       immediately (the "gap" turn).</li>
 * </ul>
 *
 * <p>State is persisted via {@link SharedPreferences} so the alternation
 * survives process death. A configurable minimum time-gap guard prevents
 * back-to-back ads caused by rapid double-taps.</p>
 *
 * <p><b>Contract:</b> This class must NOT import from any {@code git} or
 * {@code native/security} package. It is called exclusively from the
 * UI layer (e.g., {@code MainActivity.fab_ad_action}).</p>
 */
public class AdManager {

    /* ── Constants ──────────────────────────────────────────────────── */

    private static final String TAG = "AdManager";

    private static final String PREFS_NAME        = "adobs_ad_manager";
    private static final String KEY_CLICK_COUNTER  = "click_counter";
    private static final String KEY_LAST_AD_SHOW   = "last_ad_show_ms";

    /** Minimum interval between two ad impressions (ms). Adjustable. */
    private static final long DEFAULT_MIN_AD_GAP_MS = 30_000L;

    /** Max preload retries before giving up until the next gap turn. */
    private static final int MAX_RETRY_ATTEMPTS = 3;

    /** Base backoff delay for the first retry; subsequent retries double it. */
    private static final long RETRY_BASE_DELAY_MS = 2_000L;

    /* ── Fields ─────────────────────────────────────────────────────── */

    private final Context appContext;
    private final SharedPreferences prefs;
    private final Handler mainHandler;
    private final String adUnitId;
    private final long minAdGapMs;

    /** Current Activity — WeakReference to avoid leaks across rotations. */
    private WeakReference<Activity> activityRef;

    /** The preloaded interstitial, or {@code null} if not yet loaded. */
    @Nullable
    private volatile InterstitialAd preloadedAd;

    /** True while a load request is in flight. */
    private volatile boolean isPreloading;

    /** True while a full-screen ad is being displayed. */
    private volatile boolean isAdShowing;

    /** Retry counter for exponential backoff. */
    private int retryAttempt;

    /* ── Singleton ──────────────────────────────────────────────────── */

    private static volatile AdManager instance;

    /**
     * Returns the singleton {@link AdManager}, creating it on first access.
     *
     * @param context any context; internally coerced to the application context.
     */
    @NonNull
    public static AdManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (AdManager.class) {
                if (instance == null) {
                    instance = new AdManager(
                            context.getApplicationContext(),
                            resolveAdUnitId(),
                            DEFAULT_MIN_AD_GAP_MS
                    );
                }
            }
        }
        return instance;
    }

    /**
     * <b>For testing only.</b> Creates a fresh instance without affecting
     * the singleton. Callers are responsible for its lifecycle.
     */
    @VisibleForTesting
    AdManager(@NonNull Context appContext,
              @NonNull String adUnitId,
              long minAdGapMs) {
        this.appContext  = appContext;
        this.prefs       = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.adUnitId    = adUnitId;
        this.minAdGapMs  = minAdGapMs;
        this.isPreloading = false;
        this.isAdShowing  = false;
        this.retryAttempt = 0;

        // Kick off the first preload so the ad is ready when the first
        // odd-numbered click arrives.
        preloadNextAd();
    }

    /* ── Configuration ──────────────────────────────────────────────── */

    /**
     * Supply the AdMob interstitial ad unit ID.
     * <p>Override or replace to provide your production ID.</p>
     */
    @NonNull
    private static String resolveAdUnitId() {
        // Google AdMob test interstitial ad unit
        return "ca-app-pub-3940256099942544/1033173712";
    }

    /**
     * Update the {@link Activity} reference used to display interstitials.
     * <p>Call from {@code Activity.onResume()} so the reference stays valid
     * across configuration changes.</p>
     */
    public void setActivity(@Nullable Activity activity) {
        this.activityRef = (activity != null)
                ? new WeakReference<>(activity)
                : null;
    }

    /* ── Public API ─────────────────────────────────────────────────── */

    /**
     * Handle a user-initiated action with the alternating ad pattern.
     *
     * <table summary="behaviour">
     *   <tr><th>Turn</th><th>Behaviour</th></tr>
     *   <tr><td>Odd (1st, 3rd, 5th…)</td>
     *       <td>Show preloaded interstitial → run {@code action} on
     *           dismiss/failure. If ad is not ready or the time-gap guard
     *           is active, run {@code action} immediately (fallback).</td></tr>
     *   <tr><td>Even (2nd, 4th, 6th…)</td>
     *       <td>Skip ad → run {@code action} immediately → preload next
     *           ad in the background.</td></tr>
     * </table>
     *
     * @param action work to perform; guaranteed to run exactly once.
     */
    public void handleClick(@NonNull final Runnable action) {
        final long    counter = getClickCounter();
        final boolean adTurn  = isAdTurn(counter);

        if (adTurn) {
            final Activity activity = resolveActivity();
            if (canShowAd() && isAdReady() && activity != null) {
                // ── Ad turn: show interstitial, defer action ──
                showAdAndThen(activity, action);
            } else {
                // ── Fallback: ad not ready / gap guard / no Activity ──
                // Never block the user.
                Log.d(TAG, "Ad turn but ad not showable — executing action immediately");
                action.run();
                preloadNextAd();
            }
        } else {
            // ── Gap turn: no ad ──
            action.run();
            preloadNextAd();
        }

        incrementClickCounter();
    }

    /**
     * Returns {@code true} if the next call to {@link #handleClick} will
     * attempt to show an ad. Useful for UI hints (e.g., changing the FAB
     * icon).
     */
    public boolean isNextClickAdTurn() {
        return isAdTurn(getClickCounter());
    }

    /**
     * Resets the persistent click counter to 1 so the next click is an
     * "ad turn". Useful for testing or user-preference reset.
     */
    public void resetCounter() {
        prefs.edit().putLong(KEY_CLICK_COUNTER, 1L).apply();
    }

    /**
     * Release resources. Call from {@code Activity.onDestroy()} if you are
     * not using the singleton pattern.
     */
    public void destroy() {
        mainHandler.removeCallbacksAndMessages(null);
        preloadedAd = null;
        isPreloading = false;
        isAdShowing  = false;
        retryAttempt = 0;
    }

    /* ── Counter / state ────────────────────────────────────────────── */

    /** Odd = ad turn (1-based: 1st click → odd). */
    private static boolean isAdTurn(long counter) {
        return (counter & 1L) == 1L;
    }

    private long getClickCounter() {
        return prefs.getLong(KEY_CLICK_COUNTER, 1L);
    }

    private void incrementClickCounter() {
        prefs.edit()
             .putLong(KEY_CLICK_COUNTER, getClickCounter() + 1L)
             .apply();
    }

    /* ── Time-gap guard ─────────────────────────────────────────────── */

    private boolean canShowAd() {
        if (isAdShowing) return false;
        long lastShow = prefs.getLong(KEY_LAST_AD_SHOW, 0L);
        return (System.currentTimeMillis() - lastShow) >= minAdGapMs;
    }

    private void recordAdShown() {
        prefs.edit()
             .putLong(KEY_LAST_AD_SHOW, System.currentTimeMillis())
             .apply();
    }

    /* ── Ad readiness ───────────────────────────────────────────────── */

    private boolean isAdReady() {
        return preloadedAd != null;
    }

    /* ── Show ad ────────────────────────────────────────────────────── */

    private void showAdAndThen(@NonNull Activity activity,
                               @NonNull Runnable action) {
        final InterstitialAd ad = preloadedAd;
        preloadedAd = null;   // consume the ad
        isAdShowing = true;

        ad.setFullScreenContentCallback(new FullScreenContentCallback() {

            @Override
            public void onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial displayed");
                recordAdShown();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError error) {
                Log.w(TAG, "Interstitial failed to show: " + error.getMessage());
                isAdShowing = false;
                action.run();
                preloadNextAd();
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial dismissed");
                isAdShowing = false;
                action.run();
                preloadNextAd();
            }

            @Override
            public void onAdClicked() {
                Log.d(TAG, "Interstitial clicked");
            }

            @Override
            public void onAdImpression() {
                Log.d(TAG, "Interstitial impression recorded");
            }
        });

        ad.show(activity);
    }

    /* ── Preload ────────────────────────────────────────────────────── */

    /** Begins an asynchronous ad load if one is not already in progress. */
    private void preloadNextAd() {
        if (isPreloading) return;          // deduplicate
        isPreloading = true;
        retryAttempt = 0;
        requestAd();
    }

    private void requestAd() {
        final AdRequest request = new AdRequest.Builder().build();

        InterstitialAd.load(appContext, adUnitId, request,
                new InterstitialAdLoadCallback() {

                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                        Log.d(TAG, "Interstitial preloaded successfully");
                        preloadedAd   = interstitialAd;
                        isPreloading  = false;
                        retryAttempt  = 0;
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        Log.w(TAG, "Interstitial load failed (attempt "
                                + retryAttempt + "): " + error.getMessage());
                        isPreloading = false;
                        retryWithBackoff();
                    }
                });
    }

    /** Exponential backoff: 2 s → 4 s → 8 s, then give up. */
    private void retryWithBackoff() {
        if (retryAttempt >= MAX_RETRY_ATTEMPTS) {
            Log.w(TAG, "Max preload retries reached — will retry on next gap turn");
            retryAttempt = 0;
            return;
        }
        retryAttempt++;
        long delay = RETRY_BASE_DELAY_MS * (1L << (retryAttempt - 1));

        Log.d(TAG, "Retrying preload in " + delay + " ms (attempt " + retryAttempt + ")");

        mainHandler.postDelayed(() -> {
            isPreloading = true;
            requestAd();
        }, delay);
    }

    /* ── Activity resolution ────────────────────────────────────────── */

    @Nullable
    private Activity resolveActivity() {
        if (activityRef == null) return null;
        Activity activity = activityRef.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            activityRef = null;
            return null;
        }
        return activity;
    }
}