package com.weslley.wesdownloader

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weslley.wesdownloader.domain.MediaMode
import com.weslley.wesdownloader.ui.AppViewModel
import com.weslley.wesdownloader.ui.WesDownloaderScreen
import com.weslley.wesdownloader.ui.theme.WesDownloaderTheme

class MainActivity : ComponentActivity() {
    private var sharedRequest by mutableStateOf<SharedRequest?>(null)

    private data class SharedRequest(val text: String, val id: Long)
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedRequest = intent.sharedText()?.let { SharedRequest(it, System.nanoTime()) }
        requestRuntimePermissions()
        setContent {
            val appViewModel: AppViewModel = viewModel(factory = AppViewModel.factory(application))
            LaunchedEffect(sharedRequest?.id) {
                sharedRequest?.text?.let { sharedText ->
                    appViewModel.setUrl(sharedText)
                    appViewModel.inspect()
                }
            }
            WesDownloaderTheme {
                WesDownloaderScreen(
                    viewModel = appViewModel,
                    onOpen = ::openMedia,
                    onShare = ::shareMedia,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedRequest = intent.sharedText()?.let { SharedRequest(it, System.nanoTime()) }
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun openMedia(uri: Uri, mode: MediaMode) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType(mode, uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Abrir com"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Nenhum aplicativo consegue abrir este arquivo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareMedia(uri: Uri, mode: MediaMode) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType(mode, uri)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Compartilhar arquivo"))
    }

    private fun mimeType(mode: MediaMode, uri: Uri): String = contentResolver.getType(uri)
        ?: if (mode == MediaMode.AUDIO) "audio/*" else "video/*"

    private fun Intent.sharedText(): String? = if (action == Intent.ACTION_SEND && type == "text/plain") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getStringExtra(Intent.EXTRA_TEXT)
        else @Suppress("DEPRECATION") getStringExtra(Intent.EXTRA_TEXT)
    } else null
}
