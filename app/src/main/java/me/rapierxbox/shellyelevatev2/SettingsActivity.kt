package me.rapierxbox.shellyelevatev2

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import me.rapierxbox.shellyelevatev2.databinding.SettingsActivityBinding

@SuppressLint("UseSwitchCompatOrMaterialCode")
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: SettingsActivityBinding // Declare the binding object

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, SettingsFragment()).commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}