package com.goncalocosta.lumen.home

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast

fun uninstallPackage(activity: Activity, packageName: String) {
    val uri = Uri.parse("package:$packageName")
    val flags = Intent.FLAG_ACTIVITY_NEW_TASK
    val candidates = listOf(
        Intent(Intent.ACTION_DELETE, uri).addFlags(flags),
        Intent("android.intent.action.UNINSTALL_PACKAGE", uri).addFlags(flags)
    )
    val pm = activity.packageManager
    for (intent in candidates) {
        if (intent.resolveActivity(pm) != null) {
            activity.startActivity(intent)
            return
        }
    }
    try {
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val pending = PendingIntent.getActivity(
            activity,
            packageName.hashCode(),
            Intent(activity, MainActivity::class.java),
            piFlags
        )
        pm.packageInstaller.uninstall(packageName, pending.intentSender)
    } catch (e: Exception) {
        Toast.makeText(activity, "Cannot uninstall ${e.message ?: packageName}", Toast.LENGTH_SHORT).show()
    }
}
