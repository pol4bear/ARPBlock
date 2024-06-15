package dev.pol4.arpblock

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.commandexecutor.CommandExecutor
import dev.pol4.arpblock.ui.theme.ARPBlockTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        copyBinaryIfNeeded()
        var interfaceOptions by mutableStateOf(getInterfaceOptions())

        setContent {
            ARPBlockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainContent(
                        options = interfaceOptions,
                        onScanClicked = { selectedOption ->
                            Log.d(
                                "MainActivity",
                                "Scan button clicked with option: $selectedOption"
                            )
                            val intent = Intent(this, ScanActivity::class.java).apply {
                                putExtra("INTERFACE_INFO", selectedOption)
                            }
                            startActivity(intent)
                        },
                        onCloseClicked = {
                            Log.d("MainActivity", "Close button clicked")
                            finish()
                        },
                        onRefreshClicked = {
                            Log.d("MainActivity", "Refresh button clicked")
                            interfaceOptions = getInterfaceOptions()
                        }
                    )
                }
            }
        }
    }

    private fun copyBinaryIfNeeded() {
        val currentVersion = packageManager.getPackageInfo(packageName, 0).versionCode
        val sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedVersion = sharedPreferences.getInt("version_code", -1)

        val binaryName = if (is64Bit()) "arm64-v8a/pnet" else "armeabi-v7a/pnet"
        val destination = File(filesDir, "pnet")

        if (!destination.exists() || currentVersion != savedVersion) {
            try {
                Log.d("MainActivity", "Copying binary $binaryName to $destination")
                assets.open(binaryName).use { input ->
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }
                // Add execute permission to the binary
                if (destination.setExecutable(true, false)) {
                    Log.d(
                        "MainActivity",
                        "Successfully set executable permission for $destination"
                    )
                } else {
                    Log.e("MainActivity", "Failed to set executable permission for $destination")
                }
                sharedPreferences.edit().putInt("version_code", currentVersion).apply()
                Log.d("MainActivity", "Binary copied successfully")
            } catch (e: IOException) {
                Log.e("MainActivity", "Error copying binary", e)
            }
        } else {
            Log.d("MainActivity", "Binary is already up to date")
        }

        // Log the absolute path of the binary
        Log.d("MainActivity", "Binary absolute path: ${destination.absolutePath}")
        // Check if the binary exists and log the result
        if (destination.exists()) {
            Log.d("MainActivity", "Binary exists")
        } else {
            Log.e("MainActivity", "Binary does not exist")
        }
        // Check if the binary is executable and log the result
        if (destination.canExecute()) {
            Log.d("MainActivity", "Binary is executable")
        } else {
            Log.e("MainActivity", "Binary is not executable")
        }
    }

    private fun is64Bit(): Boolean {
        val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        Log.d("MainActivity", "Device is 64-bit: $is64Bit")
        return is64Bit
    }

    private fun getInterfaceOptions(): List<NetworkInterfaceInfo> {
        val binaryPath = File(filesDir, "pnet").absolutePath
        Log.d("MainActivity", "Executing binary at: $binaryPath")

        val binaryFile = File(binaryPath)
        if (!binaryFile.exists()) {
            Log.e("MainActivity", "Binary file does not exist")
            return emptyList()
        }
        if (!binaryFile.canExecute()) {
            Log.e("MainActivity", "Binary file is not executable")
            return emptyList()
        }

        return try {
            val process = CommandExecutor.executeCommand(binaryPath + " interfaces")
            val result = process?.inputStream?.bufferedReader()?.readText() ?: ""
            Log.d("MainActivity", "Binary execution result: $result")
            result.lines()
                .mapNotNull {
                    val parts = it.split(" : ")
                    if (parts.size > 1) {
                        val details = parts[1].split(", ")
                        if (details.size >= 2 && details[1] != "0.0.0.0/0") {
                            val (ip, subnet) = details[1].split("/")
                            NetworkInterfaceInfo(
                                name = parts[0].trim(),
                                mac = details[0],
                                ip = ip,
                                subnet = subnet,
                                gateway = if (details.size == 3) details[2] else null
                            )
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
                .filter { it.name.isNotEmpty() } // Remove empty lines
                .sortedWith(compareBy(
                    { !it.name.startsWith("wlan") },
                    { !it.name.startsWith("eth") },
                    { it.name }
                ))
        } catch (e: IOException) {
            Log.e("MainActivity", "Error executing binary", e)
            emptyList()
        }
    }
}

@Composable
fun MainContent(options: List<NetworkInterfaceInfo>, onScanClicked: (NetworkInterfaceInfo) -> Unit, onCloseClicked: () -> Unit, onRefreshClicked: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf(options.firstOrNull() ?: NetworkInterfaceInfo("Interface name", "", "", "", null)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .padding(vertical = 16.dp)
                .align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { expanded = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6200EE),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = selectedOption.name,
                            fontSize = 16.sp // Set the font size here
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    val interactionSource = remember { MutableInteractionSource() }

                    IconButton(
                        onClick = { onRefreshClicked() },
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .size(56.dp)  // Increase the size of the IconButton
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                },
                                onClick = {
                                    selectedOption = option
                                    expanded = false
                                    Log.d("MainContent", "Selected interface: $selectedOption")
                                }
                            )
                        }
                    }
                }


                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { onScanClicked(selectedOption) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "Scan!", fontSize = 18.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onCloseClicked() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF44336),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "Close", fontSize = 18.sp)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainActivityPreview() {
    ARPBlockTheme {
        MainContent(
            options = listOf(
                NetworkInterfaceInfo("wlan0", "00:11:22:33:44:55", "192.168.1.2", "24", "192.168.1.1"),
                NetworkInterfaceInfo("eth0", "66:77:88:99:AA:BB", "192.168.1.3", "24", "192.168.1.1"),
                NetworkInterfaceInfo("lo", "00:00:00:00:00:00", "127.0.0.1", "8", null)
            ),
            onScanClicked = {},
            onCloseClicked = {},
            onRefreshClicked = {}
        )
    }
}
