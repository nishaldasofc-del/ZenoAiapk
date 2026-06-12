package com.ngai.zenoai.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.ngai.zenoai.R
import com.ngai.zenoai.databinding.ActivitySplashBinding
import com.ngai.zenoai.ui.main.MainActivity
import com.ngai.zenoai.utils.Constants

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var keepSplashOnScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        animateLogo()

        Handler(Looper.getMainLooper()).postDelayed({
            keepSplashOnScreen = false
            navigateToMain()
        }, Constants.SPLASH_DELAY_MS)
    }

    private fun animateLogo() {
        val pulseAnim = AnimationUtils.loadAnimation(this, R.anim.logo_pulse)
        binding.ivLogo.startAnimation(pulseAnim)

        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        binding.tvAppName.startAnimation(fadeIn)
        binding.tvTagline.startAnimation(fadeIn)
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            // Forward any deep link extras
            data = this@SplashActivity.intent?.data
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        finish()
    }
}
