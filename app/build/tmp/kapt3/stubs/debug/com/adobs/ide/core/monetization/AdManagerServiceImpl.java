package com.adobs.ide.core.monetization;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Default implementation of [IAdManager].
 *
 * Uses Google's official TEST ad unit IDs. Replace these with real ad unit
 * IDs before a production release.
 */
@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00004\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\b\u0007\u0018\u0000 \u00122\u00020\u0001:\u0001\u0012B\u0007\b\u0007\u00a2\u0006\u0002\u0010\u0002J\u0010\u0010\u0007\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nH\u0016J\u0010\u0010\u000b\u001a\u00020\b2\u0006\u0010\t\u001a\u00020\nH\u0016J,\u0010\f\u001a\u00020\b2\u0006\u0010\r\u001a\u00020\u000e2\f\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\b0\u00102\f\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\b0\u0010H\u0016R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0010\u0010\u0005\u001a\u0004\u0018\u00010\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0013"}, d2 = {"Lcom/adobs/ide/core/monetization/AdManagerServiceImpl;", "Lcom/adobs/ide/core/monetization/IAdManager;", "()V", "isLoading", "", "rewardedInterstitialAd", "Lcom/google/android/gms/ads/rewardedinterstitial/RewardedInterstitialAd;", "initAds", "", "context", "Landroid/content/Context;", "loadRewardedInterstitial", "showRewardedInterstitial", "activity", "Landroid/app/Activity;", "onRewardEarned", "Lkotlin/Function0;", "onAdDismissed", "Companion", "app_debug"})
public final class AdManagerServiceImpl implements com.adobs.ide.core.monetization.IAdManager {
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "AdManagerService";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741";
    @org.jetbrains.annotations.NotNull()
    public static final java.lang.String TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/5354046379";
    @org.jetbrains.annotations.Nullable()
    private com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd rewardedInterstitialAd;
    private boolean isLoading = false;
    @org.jetbrains.annotations.NotNull()
    public static final com.adobs.ide.core.monetization.AdManagerServiceImpl.Companion Companion = null;
    
    @javax.inject.Inject()
    public AdManagerServiceImpl() {
        super();
    }
    
    @java.lang.Override()
    public void initAds(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
    }
    
    @java.lang.Override()
    public void loadRewardedInterstitial(@org.jetbrains.annotations.NotNull()
    android.content.Context context) {
    }
    
    @java.lang.Override()
    public void showRewardedInterstitial(@org.jetbrains.annotations.NotNull()
    android.app.Activity activity, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onRewardEarned, @org.jetbrains.annotations.NotNull()
    kotlin.jvm.functions.Function0<kotlin.Unit> onAdDismissed) {
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0007"}, d2 = {"Lcom/adobs/ide/core/monetization/AdManagerServiceImpl$Companion;", "", "()V", "TAG", "", "TEST_BANNER_AD_UNIT_ID", "TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}