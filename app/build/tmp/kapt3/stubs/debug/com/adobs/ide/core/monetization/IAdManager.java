package com.adobs.ide.core.monetization;

import android.app.Activity;
import android.content.Context;

/**
 * Contract for all AdMob interactions used by the IDE.
 */
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\bf\u0018\u00002\u00020\u0001J\u0010\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H&J\u0010\u0010\u0006\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H&J,\u0010\u0007\u001a\u00020\u00032\u0006\u0010\b\u001a\u00020\t2\f\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u00030\u000b2\f\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u00030\u000bH&\u00a8\u0006\r"}, d2 = {"Lcom/adobs/ide/core/monetization/IAdManager;", "", "initAds", "", "context", "Landroid/content/Context;", "loadRewardedInterstitial", "showRewardedInterstitial", "activity", "Landroid/app/Activity;", "onRewardEarned", "Lkotlin/Function0;", "onAdDismissed", "app_debug"})
public abstract interface IAdManager {
    
    /**
     * Initializes the Mobile Ads SDK. Must be called once, typically from
     * Application.onCreate() or the first Activity's onCreate().
     */
    public abstract void initAds(@org.jetbrains.annotations.NotNull()
    android.content.Context context);
    
    /**
     * Loads (pre-fetches) a Rewarded Interstitial ad so it is ready to show
     * instantly when the user triggers a gated action.
     */
    public abstract void loadRewardedInterstitial(@org.jetbrains.annotations.NotNull()
    android.content.Context context);
    
    /**
     * Shows the pre-loaded Rewarded Interstitial ad.
     * @param onRewardEarned invoked when the user earns the reward (i.e. watched enough of the ad).
     *                      The gated action (e.g. zip extraction) should be executed here.
     * @param onAdDismissed invoked when the ad is dismissed, regardless of reward state.
     *                     Useful for triggering a reload of the next ad.
     */
    public abstract void showRewardedInterstitial(@org.jetbrains.annotations.NotNull()
    android.app.Activity activity, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onRewardEarned, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onAdDismissed);
}