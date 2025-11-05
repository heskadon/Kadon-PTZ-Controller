package com.kadon.ptzcontroller.ui.component

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import timber.log.Timber

/**
 * Composable function that displays the application's version name.
 */
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val versionName = getAppVersion(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PTZ Controller",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Version: $versionName",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Retrieves the application's version name from the package manager.
 * @param context The application context.
 * @return The version name as a string, or "N/A" if not found.
 */
private fun getAppVersion(context: Context): String? {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        if (packageInfo != null)
            packageInfo.versionName
        else
            "N/A"
    } catch (e: PackageManager.NameNotFoundException) {
        Timber.e("Error retrieving app version: ${e.message}")
        "N/A"
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
)
@Composable
fun AboutScreenPreview() {
    MaterialTheme { AboutScreen() }
}