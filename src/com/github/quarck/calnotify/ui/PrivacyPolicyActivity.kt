package com.github.quarck.calnotify.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.android.calendar.DynamicTheme
import org.qrck.seshat.R

private val dynamicTheme = DynamicTheme()

class PrivacyPolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dynamicTheme.onCreate(this)

        setContentView(R.layout.activity_privacy_policy)

        setSupportActionBar(findViewById<Toolbar?>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
