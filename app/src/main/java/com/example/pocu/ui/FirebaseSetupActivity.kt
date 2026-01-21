package com.example.pocu.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * OBSOLETO - Redirige a ServerSetupActivity
 */
class FirebaseSetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, ServerSetupActivity::class.java))
        finish()
    }
}
