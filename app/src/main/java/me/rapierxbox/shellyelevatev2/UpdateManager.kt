package me.rapierxbox.shellyelevatev2

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import androidx.core.net.toUri

object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val REPO_OWNER = "RapierXbox"
    private const val REPO_NAME = "ShellyElevate"

    data class UpdateInfo(val version: String, val changelog: String, val apkUrl: String)

    fun fetchLatestUpdateInfo(): UpdateInfo? {
        return try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest")
                .build()
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val json = JSONObject(response.body.string())
                val version = json.getString("tag_name").removePrefix("v")
                val changelog = json.getString("body")
                val assets = json.getJSONArray("assets")
                val apkUrl = assets.getJSONObject(0).getString("browser_download_url")
                UpdateInfo(version, changelog, apkUrl)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching update info", e)
            null
        }
    }

    fun isNewVersionAvailable(local: String, remote: String): Boolean {
        return remote > local
    }

    fun promptAndDownloadUpdate(context: Context, updateInfo: UpdateInfo) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Update available")
            .setMessage("Version ${updateInfo.version} is available.\n\nChangelog:\n${updateInfo.changelog}\n\nDo you want to update?")
            .setPositiveButton(R.string.update) { _, _ ->
                showDownloadDialog(context, updateInfo.apkUrl)
            }
            .setNegativeButton(R.string.not_now, null)
            .show()
    }

    private fun showDownloadDialog(context: Context, apkUrl: String) {
        val progressIndicator = LinearProgressIndicator(context).apply {
            isIndeterminate = true
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Downloading update")
            .setView(progressIndicator)
            .setCancelable(false)
            .show()

        val fileName = "update.apk"
        val file = File(context.getExternalFilesDir(null), fileName)
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(apkUrl.toUri())
            .setTitle("Downloading update")
            .setDescription("Please wait...")
            .setDestinationUri(Uri.fromFile(file))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = manager.enqueue(request)

        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    context.unregisterReceiver(this)
                    dialog.dismiss()
                    installApk(context, file)
                }
            }
        }, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED)
    }

    private fun installApk(context: Context, apkFile: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".provider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No handler for APK install", e)
        }
    }
}