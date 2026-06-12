package com.dodotechhk.video2gif

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.dodotechhk.video2gif.ui.Video2gifApp
import com.dodotechhk.video2gif.ui.theme.Video2gifTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Video2gifTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Video2gifApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }

        // 应用内评分:启动 5 秒后按条件尝试(装机 ≥7 天 + 距上次 ≥15 天,见 AppReviewGate;
        // 触发时机与参考项目 ComplexMusic 一致)。
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing) AppReviewGate.maybeAskForReview(this)
        }, 5000)
    }
}
