package fr.ardoise.tasks.ui

import android.Manifest
import android.content.IntentSender
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.ardoise.tasks.ui.theme.ArdoiseTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ardoise is dark in both system themes, so the bars must always carry
        // light icons; letting the system infer them yields black on charcoal.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            ArdoiseTheme {
                val model: HomeViewModel = viewModel()
                val state by model.state.collectAsState()
                val consent by model.consentRequest.collectAsState()

                val consentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result -> model.onConsentResult(result.resultCode, result.data) }

                val notificationLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { model.refreshSystemState() }

                // The permission can also be granted from system settings while
                // Ardoise is in the background, so re-read it on every resume.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) model.refreshSystemState()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(consent) {
                    val pending = consent ?: return@LaunchedEffect
                    try {
                        consentLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    } catch (error: IntentSender.SendIntentException) {
                        model.consentLaunchFailed()
                    }
                }

                HomeScreen(
                    state = state,
                    onSignIn = model::signIn,
                    onSelectList = model::selectList,
                    onMaxTasks = model::setMaxTasks,
                    onSyncInterval = model::setSyncInterval,
                    onNotificationToggle = model::setNotificationEnabled,
                    onWallpaperToggle = model::setWallpaperEnabled,
                    onRefresh = model::refreshNow,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onMessageShown = model::dismissMessage,
                )
            }
        }
    }
}
