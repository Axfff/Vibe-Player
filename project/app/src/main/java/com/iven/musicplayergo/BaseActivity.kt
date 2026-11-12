package com.iven.musicplayergo

import android.content.Context
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import com.iven.musicplayergo.preferences.AppLanguage
import com.iven.musicplayergo.utils.Theming


abstract class BaseActivity: AppCompatActivity() {

    override fun onStart() {
        super.onStart()
        if (Theming.isThemeBlack(resources)) {
            window?.statusBarColor = Color.BLACK
        }
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.let(AppLanguage::context))
    }
}
