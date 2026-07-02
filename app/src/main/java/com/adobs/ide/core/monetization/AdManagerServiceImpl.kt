package com.adobs.ide.core.monetization

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default implementation of [IAdManager].
 *
 * Uses Google's official TEST ad unit IDs. Replace these with real ad unit
 * IDs before a production release.
 */
@Singleton
class AdManagerServiceImpl @Inject constructor() : IAdManager {

    companion object {
        private const val TAG = "AdManagerService"

        // Official Google TEST ad unit IDs — safe to ship during development.
        const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
        const val TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID =
            "ca-app-pub-3940256099942544/5354046379"
    }

    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var isLoading: Boolean = false

    override fun initAds(context: Context) {
        MobileAds.initialize(context.applicationContext) { initializationStatus ->
            Log.d(TAG, "MobileAds initialized: ${initializationStatus.adapterStatusMap}")
        }
    }

    override fun loadRewardedInterstitial(context: Context) {
        if (isLoading || rewardedInterstitialAd != null) {
            // Already loaded or a load is in flight — avoid duplicate requests.
            return
        }

        isLoading = true
        val adRequest = AdRequest.Builder().build()

        RewardedInterstitialAd.load(
            context.applicationContext,
            TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    Log.d(TAG, "Rewarded interstitial loaded")
                    rewardedInterstitialAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Rewarded interstitial failed to load: ${adError.message}")
                    rewardedInterstitialAd = null
                    isLoading = false
                }
            }
        )
    }

    override fun showRewardedInterstitial(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onAdDismissed: () -> Unit
    ) {
        val ad = rewardedInterstitialAd

        if (ad == null) {
            Log.w(TAG, "Rewarded interstitial not ready — proceeding without ad.")
            // Fail-open: don't block the user's workflow if the ad isn't ready.
            onRewardEarned()
            onAdDismissed()
            // Kick off a fresh load for next time.
            loadRewardedInterstitial(activity)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad dismissed")
                rewardedInterstitialAd = null
                onAdDismissed()
                // Pre-load the next ad immediately.
                loadRewardedInterstitial(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Ad failed to show: ${adError.message}")
                rewardedInterstitialAd = null
                onAdDismissed()
                loadRewardedInterstitial(activity)
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad showed full screen content")
            }
        }

        ad.show(activity) { rewardItem ->
            Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            onRewardEarned()
        }
    }
}
