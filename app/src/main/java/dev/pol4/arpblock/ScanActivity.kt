package dev.pol4.arpblock

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.pol4.arpblock.ui.theme.ARPBlockTheme
import com.example.commandexecutor.CommandExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

// Extension function to convert IP address to a comparable long value
fun String.toIpAddress(): Long {
    return split(".").reversed().foldIndexed(0L) { index, acc, s ->
        acc or (s.toLong() shl (index * 8))
    }
}

// Data class DeviceInfo with Comparable implementation
data class DeviceInfo(val ip: String, val mac: String) : Comparable<DeviceInfo> {
    override fun compareTo(other: DeviceInfo): Int {
        return this.ip.toIpAddress().compareTo(other.ip.toIpAddress())
    }
}

class ScanActivity : ComponentActivity() {
    private val processMap = mutableMapOf<String, Process?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val interfaceInfo = intent.getParcelableExtra<NetworkInterfaceInfo>("INTERFACE_INFO")

        if (interfaceInfo == null || interfaceInfo.gateway.isNullOrEmpty()) {
            Toast.makeText(this, "No gateway information available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            ARPBlockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScanScreen(interfaceInfo)
                }
            }
        }
    }

    @Composable
    fun ScanScreen(interfaceInfo: NetworkInterfaceInfo) {
        var isLoading by remember { mutableStateOf(true) }
        val deviceList = remember { mutableStateListOf<DeviceInfo>() }

        LaunchedEffect(Unit) {
            scanNetwork(interfaceInfo, deviceList) {
                isLoading = false
            }
        }

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 70.dp),  // Add padding at the bottom to avoid overlap with the button
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                DeviceList(deviceList)
            }

            Button(
                onClick = {
                    deviceList.clear()
                    isLoading = true
                    scanNetwork(interfaceInfo, deviceList) {
                        isLoading = false
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Start Scan")
                }
            }
        }
    }


    // DeviceList composable function to display sorted list of devices
    @Composable
    fun DeviceList(devices: List<DeviceInfo>) {
        val sortedDevices = devices.sorted()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            items(sortedDevices) { device ->
                DeviceRow(device)
            }
        }
    }


    @Composable
    fun DeviceRow(device: DeviceInfo) {
        var isBlocking by remember { mutableStateOf(false) }
        var isButtonEnabled by remember { mutableStateOf(true) }
        val defaultButtonColor = MaterialTheme.colorScheme.primary
        val clickedButtonColor = MaterialTheme.colorScheme.onPrimary
        var buttonColor by remember { mutableStateOf(defaultButtonColor) }
        val interactionSource = remember { MutableInteractionSource() }
        val scope = rememberCoroutineScope()

        LaunchedEffect(interactionSource) {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> buttonColor = clickedButtonColor
                    is PressInteraction.Release, is PressInteraction.Cancel -> buttonColor = defaultButtonColor
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = device.ip, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = device.mac, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(
                    onClick = {
                        isButtonEnabled = false
                        buttonColor = clickedButtonColor  // Change button color when clicked
                        scope.launch {
                            if (isBlocking) {
                                stopArpBlock(device.ip) {
                                    isBlocking = false
                                    isButtonEnabled = true
                                    buttonColor = defaultButtonColor  // Restore original color after operation completes
                                }
                            } else {
                                startArpBlock(device.ip) {
                                    isBlocking = true
                                    isButtonEnabled = true
                                    buttonColor = defaultButtonColor  // Restore original color after operation completes
                                }
                            }
                        }
                    },
                    enabled = isButtonEnabled,
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .size(50.dp)
                        .background(color = buttonColor, shape = CircleShape)
                        .padding(10.dp)
                        .border(1.dp, buttonColor, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = if (isBlocking) Icons.Filled.Close else Icons.Filled.Check,
                        contentDescription = null,
                        tint = if (isBlocking) Color.Red else Color.Green,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }

    private fun startArpBlock(ip: String, onCompleted: () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            val pnetPath = File(filesDir, "pnet").absolutePath
            try {
                val command = "$pnetPath arpblock $ip"
                val process = CommandExecutor.executeCommand(command)
                if (process != null) {
                    processMap[ip] = process
                }
                withContext(Dispatchers.Main) {
                    onCompleted()
                }
            } catch (e: Exception) {
                Log.e("ScanActivity", "Error starting arpblock command", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ScanActivity, "Failed to start block", Toast.LENGTH_SHORT).show()
                    onCompleted()
                }
            }
        }
    }

    private fun stopArpBlock(ip: String, onCompleted: () -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val getPid = CommandExecutor.executeCommand("ps -ef | grep -v su | grep pnet | grep $ip | awk '{print \$2}'")
                val pid = BufferedReader(InputStreamReader(getPid?.inputStream)).readLine()
                Log.d("ScanActivity", "PID of $ip : $pid")
                if (!pid.isNullOrEmpty()) {
                    val killCommand = "kill -9 $pid"
                    CommandExecutor.executeCommand(killCommand)
                    processMap.remove(ip)
                }
                withContext(Dispatchers.Main) {
                    onCompleted()
                }
            } catch (e: Exception) {
                Log.e("ScanActivity", "Error stopping arpblock command", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ScanActivity, "Failed to stop block", Toast.LENGTH_SHORT).show()
                    onCompleted()
                }
            }
        }
    }

    private fun scanNetwork(
        interfaceInfo: NetworkInterfaceInfo,
        deviceList: MutableList<DeviceInfo>,
        onCompleted: () -> Unit
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            val pnetPath = File(filesDir, "pnet").absolutePath
            Log.d("ScanActivity", "pnetPath: $pnetPath")

            try {
                val process = CommandExecutor.executeCommand("$pnetPath arpscan ${interfaceInfo.name}")
                val reader = BufferedReader(InputStreamReader(process?.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process?.errorStream))
                val myIp = interfaceInfo.ip
                val gatewayIp = interfaceInfo.gateway

                reader.use {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.d("ScanActivity", "Output: $line")
                        val parts = line!!.split(" ")
                        if (parts.size == 2) {
                            val ip = parts[0]
                            val mac = parts[1]
                            if (ip != myIp && ip != gatewayIp) {
                                withContext(Dispatchers.Main) {
                                    deviceList.add(DeviceInfo(ip, mac))
                                }
                            }
                        }
                    }
                }

                errorReader.use {
                    var errorLine: String?
                    while (errorReader.readLine().also { errorLine = it } != null) {
                        Log.e("ScanActivity", "Error: $errorLine")
                    }
                }

                val exitCode = process?.waitFor()
                if (exitCode != 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ScanActivity, "Scan failed", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e("ScanActivity", "Error executing pnet command", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ScanActivity, "Scan failed", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

            withContext(Dispatchers.Main) {
                onCompleted()
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun ScanActivityPreview() {
        ARPBlockTheme {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                DeviceList(
                    devices = listOf(
                        DeviceInfo("192.168.1.2", "00:11:22:33:44:55"),
                        DeviceInfo("192.168.1.3", "66:77:88:99:AA:BB"),
                        DeviceInfo("192.168.1.4", "CC:DD:EE:FF:00:11")
                    )
                )

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = { /* Do nothing in preview */ },
                    enabled = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Start Scan")
                }
            }
        }
    }
}
