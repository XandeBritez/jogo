package fodinha.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.CompositionLocalProvider
import fodinha.app.ui.FodinhaTheme
import fodinha.app.ui.LocalGameSettings
import fodinha.app.ui.OptionsScreen
import fodinha.app.ui.HomeScreen
import fodinha.app.ui.LobbyScreen
import fodinha.app.ui.TableScreen

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestBluetoothPermissions()
        setContent {
            FodinhaTheme {
                App()
            }
        }
    }

    /** API 31+ exige concessao em runtime; so o manifest nao basta. */
    private fun requestBluetoothPermissions() {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissionLauncher.launch(needed)
    }
}

@Composable
private fun App(vm: GameViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbar.showSnackbar(it)
            vm.dismissError()
        }
    }

    CompositionLocalProvider(LocalGameSettings provides ui.settings) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent,
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                )
                .padding(inner)
        ) {
            when (ui.screen) {
                Screen.HOME -> HomeScreen(
                    ui = ui,
                    onName = vm::setName,
                    onStartBots = vm::startBotGame,
                    onHostWifi = vm::hostWifiRoom,
                    onScanWifi = vm::startRoomDiscovery,
                    onStopScanWifi = vm::stopRoomDiscovery,
                    onJoinWifi = vm::joinWifiRoom,
                    onHostBluetooth = vm::hostBluetoothRoom,
                    onScanBluetooth = vm::loadPairedDevices,
                    onJoinBluetooth = vm::joinBluetooth,
                    onOpenOptions = vm::openOptions,
                )

                Screen.LOBBY -> LobbyScreen(
                    ui = ui,
                    onAddBot = vm::addBot,
                    onRemoveBot = vm::removeBot,
                    onStart = vm::startGame,
                    onLeave = vm::leave,
                )

                Screen.TABLE -> ui.view?.let { v ->
                    TableScreen(
                        view = v,
                        onBid = vm::bid,
                        onPlay = vm::playCard,
                        onPlayBlind = vm::playBlind,
                        onNextRound = vm::nextRound,
                        onLeave = vm::leave,
                    )
                }

                Screen.OPTIONS -> OptionsScreen(
                    settings = ui.settings,
                    playerName = ui.playerName,
                    onSettings = vm::setSettings,
                    onName = vm::setName,
                    onBack = vm::closeOptions,
                )
            }
        }
    }
    }
}
