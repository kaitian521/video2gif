package com.dodotechhk.video2gif

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * 应用内评分弹窗(移植自 ComplexMusic 的 AppReviewGate + GoogleReviewManager,
 * 本项目无 flavor/DI,合并为单一对象;仅海外版 = Google Play In-App Review)。
 *
 * 触发条件:
 * - 装机 ≥ 7 天;
 * - 从未弹过,或距上次弹出 ≥ 15 天。
 *
 * 是否真正显示由 Play Services 按配额决定;**即便未显示也记录本次时间**,
 * 避免短期内反复尝试消耗配额(与参考实现一致)。
 */
object AppReviewGate {

    private const val TAG = "AppReviewGate"
    private const val PREFS = "AppSettings"
    private const val KEY_LAST_REVIEW_PROMPT_TIME = "last_review_prompt_time"

    private const val MIN_INSTALL_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private const val MIN_INTERVAL_MS = 15L * 24 * 60 * 60 * 1000

    fun shouldAskForReview(context: Context): Boolean {
        val installTime = installTime(context)
        if (installTime <= 0L) return false
        val now = System.currentTimeMillis()
        if (now - installTime < MIN_INSTALL_AGE_MS) return false
        val lastPrompt = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_REVIEW_PROMPT_TIME, 0L)
        if (lastPrompt > 0L && now - lastPrompt < MIN_INTERVAL_MS) return false
        return true
    }

    /** 条件满足则调起应用内评分组件,并记录本次弹出时间。 */
    fun maybeAskForReview(activity: Activity) {
        if (!shouldAskForReview(activity)) return
        markPrompted(activity)
        runCatching {
            val manager = ReviewManagerFactory.create(activity)
            manager.requestReviewFlow().addOnCompleteListener { request ->
                if (!request.isSuccessful) {
                    Log.w(TAG, "requestReviewFlow failed", request.exception)
                    return@addOnCompleteListener
                }
                manager.launchReviewFlow(activity, request.result).addOnCompleteListener {
                    Log.d(TAG, "launchReviewFlow done")
                }
            }
        }
    }

    private fun markPrompted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_REVIEW_PROMPT_TIME, System.currentTimeMillis())
            .apply()
    }

    /**
     * 装机时间:直接取 PackageInfo.firstInstallTime(系统真值)。
     * 参考项目是首启往 prefs 写时间戳再读;语义相同,这里免去首启打点。
     */
    private fun installTime(context: Context): Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    }.getOrDefault(0L)
}
