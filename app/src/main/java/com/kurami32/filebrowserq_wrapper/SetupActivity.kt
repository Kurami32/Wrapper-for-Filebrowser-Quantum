package com.kurami32.filebrowserq_wrapper

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import java.util.regex.Pattern

class SetupActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var urlInput: EditText
    private lateinit var saveButton: Button

    // Request POST_NOTIFICATIONS permissions (on Android 13+)
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Notifications disabled — you may miss download alerts :(", Toast.LENGTH_SHORT).show()
        }
        startMainActivity(prefs.getString("webview_url", "") ?: "")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        // Avoid androidx.preference dependency by using getSharedPreferences directly
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)

        // If URL was previously saved, load the page directly. This is for not being prompting the "setup" screen each time that you open the app.
        val savedUrl = prefs.getString("webview_url", null)
        if (savedUrl != null) {
            startMainActivity(savedUrl)
            return
        }

        urlInput = findViewById(R.id.url_input)
        saveButton = findViewById(R.id.save_button)

        val logoView: ImageView = findViewById(R.id.app_logo)
        logoView.setImageResource(R.mipmap.ic_launcher)

        saveButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isNotEmpty() && isValidUrl(url)) {
                prefs.edit {
                    putString("webview_url", url)
                    apply()
                }
                requestStoragePermissions()
            } else {
                // Show a toast if you don't entered any URL
                // I was thinking on block any other URL non related with filebrowser (the URL filled), but...
                // The users will have different URLs for their filebrowser + The integrations like onlyoffice could not work, and also could do the things more complex
                // So, if you enter any other URL (not related with filebrowser), this will load and save it anyways.
                // You will need to delete the app data (from android settings) for enter a new URL (which should be your filebrowser URL)
                // Also, I'm not sure if other pages will load fine because, so I don't recommend do that.
                Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isValidUrl(url: String): Boolean {
        val urlPattern = Pattern.compile(
            "^((https?://)?([a-z0-9-]+\\.)+[a-z]{2,}(:\\d+)?(/.*)?)$",
            Pattern.CASE_INSENSITIVE
        )
        return urlPattern.matcher(url).matches() ||
                url.startsWith("http://") ||
                url.startsWith("https://")
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Request notification permissions (On Android 13+)
            requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
                if (shouldShowRequestPermissionRationale(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    showPermissionExplanation()
                } else {
                    requestPermissions(
                        arrayOf(
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ),
                        STORAGE_PERMISSION_CODE
                    )
                }
            } else {
                // On phones with Android 11+ the legacy permissions for storage are not required, so if you are using a newer version
                // of android, will use this instead.
                startMainActivity(prefs.getString("webview_url", "") ?: "")
            }
        }
    }

    private fun showPermissionExplanation() {
        AlertDialog.Builder(this)
            .setTitle("Storage permissions are needed, please enable them")
            .setMessage("The storage permissions are needed to download and upload files (for the file picker).")
            .setPositiveButton("Grant") { _, _ ->
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    STORAGE_PERMISSION_CODE
                )
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
            }
            .create()
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startMainActivity(prefs.getString("webview_url", "") ?: "")
            } else {
                if (!shouldShowRequestPermissionRationale(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    showGoToSettingsDialog()
                } else {
                    Toast.makeText(this, "Permissions denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showGoToSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("Storage permissions is required for save the downloads. You can enable it in app settings :)")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .create()
            .show()
    }

    private fun startMainActivity(url: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("webview_url", url)
        startActivity(intent)
        finish()
    }

    companion object {
        private const val STORAGE_PERMISSION_CODE = 101
    }
}
