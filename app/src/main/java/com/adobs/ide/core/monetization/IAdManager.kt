package com.adobs.ide.core.monetization

import android.app.Activity
import android.content.Context

/**
 * Contract for all AdMob interactions used by the IDE.
 */
interface IAdManager {

    /**
     * Initializes the Mobile Ads SDK. Must be called once, typically from
     * Application.onCreate() or the first Activity's onCreate().
     */
    fun initAds(context: Context)

    /**
     * Loads (pre-fetches) a Rewarded Interstitial ad so it is ready to show
     * instantly when the user triggers a gated action.
     */
    fun loadRewardedInterstitial(context: Context)

    /**
     * Shows the pre-loaded Rewarded Interstitial ad.
     * @param onRewardEarned invoked when the user earns the reward (i.e. watched enough of the ad).
     *                       The gated action (e.g. zip extraction) should be executed here.
     * @param onAdDismissed invoked when the ad is dismissed, regardless of reward state.
     *                      Useful for triggering a reload of the next ad.
     */
    fun showRewardedInterstitial(
        activity: Activity,
        onRewardEarned: () -> Unit,
        onAdDismissed: () -> Unit
    )
}
