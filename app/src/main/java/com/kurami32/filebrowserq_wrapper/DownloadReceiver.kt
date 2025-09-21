package com.kurami32.filebrowserq_wrapper

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class DownloadReceiver : BroadcastReceiver() {
    val fileName = PassName.NameFile
    private val tag = "DownloadReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id == -1L) return

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(id)
        val cursor = downloadManager.query(query)
        cursor.use { c ->
            if (c != null && c.moveToFirst()) {
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val localUriStr = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                        Log.i(tag, "Download successful (id=$id) localUri=$localUriStr")
                        Toast.makeText(context, "Download Finished: $fileName", Toast.LENGTH_SHORT).show()

                        // Inform that the download finished so it can show the toast
                        val done = Intent("com.kurami32.filebrowserq.DOWNLOAD_FINISHED").apply {
                            putExtra("download_id", id)
                            putExtra("download_status", "finished")
                            // Restrict broadcast to this package only
                            `package` = context.packageName
                        }
                        context.sendBroadcast(done)
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        Log.w(tag, "Download failed (id=$id) reason=$reason")
                        Toast.makeText(context, "Download failed (reason=$reason)", Toast.LENGTH_LONG).show()

                        val fail = Intent("com.kurami32.filebrowserq.DOWNLOAD_FINISHED").apply {
                            putExtra("download_id", id)
                            putExtra("download_status", "failed")
                            `package` = context.packageName
                        }
                        context.sendBroadcast(fail)
                    }
                    else -> {
                        Log.i(tag, "Download status $status for id=$id")
                    }
                }
            } else {
                Log.w(tag, "Couldn't query DownloadManager for id $id")
            }
        }
    }
}
