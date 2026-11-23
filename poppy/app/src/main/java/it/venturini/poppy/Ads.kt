package it.venturini.poppy

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Pubblicità con ricompensa (facoltativa, solo se l'utente la sceglie).
 * ATTENZIONE: gli ID qui sotto sono quelli di TEST di Google. Per incassare davvero crea un account AdMob,
 * registra l'app e sostituisci REWARDED_UNIT_ID (e l'App ID in AndroidManifest.xml).
 */
object Ads {
    const val REWARDED_UNIT_ID = "ca-app-pub-7987496381410095/4175163588"

    private var started = false

    fun show(activity: Activity, onReward: () -> Unit, onFail: () -> Unit) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(
            activity, ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (info.canRequestAds()) load(activity, onReward, onFail) else onFail()
                }
            },
            { load(activity, onReward, onFail) }
        )
    }

    private fun load(activity: Activity, onReward: () -> Unit, onFail: () -> Unit) {
        if (!started) {
            started = true
            MobileAds.initialize(activity) {}
        }
        RewardedAd.load(activity, REWARDED_UNIT_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdFailedToShowFullScreenContent(e: AdError) { onFail() }
                }
                ad.show(activity) { onReward() }
            }

            override fun onAdFailedToLoad(e: LoadAdError) { onFail() }
        })
    }
}
