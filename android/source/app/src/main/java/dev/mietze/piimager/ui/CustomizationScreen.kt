package dev.mietze.piimager.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mietze.piimager.ImagerUiState
import dev.mietze.piimager.ImagerViewModel
import dev.mietze.piimager.data.ImageSource

@Composable
fun CustomizationScreen(state: ImagerUiState, vm: ImagerViewModel) {
    val c = state.customization
    val initFormat = (state.selectedSource as? ImageSource.Remote)?.item?.initFormat ?: "none"
    val supported = initFormat != "none" && initFormat.isNotBlank()
    val interfacesSupported = initFormat == "cloudinit-rpi" || initFormat == "rpi-preseed"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("OS customisation", fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                Text(
                    if (supported) "Image format: $initFormat"
                    else "This image does not advertise Raspberry Pi Imager customisation support.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!supported) {
            item {
                InfoCard(
                    "No customisation for this image",
                    "The selected image uses init_format=none. It will be written unchanged. " +
                        "This is expected for images such as emteria Android."
                )
            }
        } else {
            item {
                OptionSwitch(
                    title = "Apply OS customisation",
                    subtitle = "Prepare first boot with your settings.",
                    checked = c.enabled,
                    onChecked = { v -> vm.updateCustomization { it.copy(enabled = v) } }
                )
            }
            if (c.enabled) {
                item {
                    CustomCard("General") {
                        OutlinedTextField(
                            value = c.hostname,
                            onValueChange = { v -> vm.updateCustomization { it.copy(hostname = v) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Hostname") },
                            supportingText = { Text("Example: raspberrypi") }
                        )
                    }
                }

                item {
                    CustomCard("User account") {
                        OutlinedTextField(
                            value = c.username,
                            onValueChange = { v -> vm.updateCustomization { it.copy(username = v) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Username") }
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = c.password,
                            onValueChange = { v -> vm.updateCustomization { it.copy(password = v) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            label = { Text("Password") }
                        )
                        Spacer(Modifier.height(10.dp))
                        OptionSwitch(
                            title = "Passwordless sudo",
                            subtitle = "Allow this account to use sudo without a password.",
                            checked = c.passwordlessSudo,
                            onChecked = { v -> vm.updateCustomization { it.copy(passwordlessSudo = v) } }
                        )
                    }
                }

                item {
                    CustomCard("Wi-Fi") {
                        OptionSwitch(
                            title = "Configure Wi-Fi",
                            subtitle = "Connect automatically on first boot.",
                            checked = c.wifiEnabled,
                            onChecked = { v -> vm.updateCustomization { it.copy(wifiEnabled = v) } }
                        )
                        if (c.wifiEnabled) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = c.wifiSsid,
                                onValueChange = { v -> vm.updateCustomization { it.copy(wifiSsid = v) } },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("SSID") }
                            )
                            Spacer(Modifier.height(10.dp))
                            OptionSwitch(
                                title = "Open network",
                                subtitle = "No Wi-Fi password.",
                                checked = c.wifiOpen,
                                onChecked = { v -> vm.updateCustomization { it.copy(wifiOpen = v) } }
                            )
                            if (!c.wifiOpen) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = c.wifiPassword,
                                    onValueChange = { v -> vm.updateCustomization { it.copy(wifiPassword = v) } },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    label = { Text("Wi-Fi password") }
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            OptionSwitch(
                                title = "Hidden network",
                                subtitle = "The access point does not broadcast its SSID.",
                                checked = c.wifiHidden,
                                onChecked = { v -> vm.updateCustomization { it.copy(wifiHidden = v) } }
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = c.wifiCountry,
                                onValueChange = { v -> vm.updateCustomization { it.copy(wifiCountry = v.uppercase().take(2)) } },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Wi-Fi country") },
                                supportingText = { Text("ISO alpha-2, for example DE") }
                            )
                        }
                    }
                }
                item {
                    CustomCard("SSH") {
                        OptionSwitch(
                            title = "Enable SSH",
                            subtitle = "Start the SSH server on first boot.",
                            checked = c.sshEnabled,
                            onChecked = { v -> vm.updateCustomization { it.copy(sshEnabled = v) } }
                        )
                        if (c.sshEnabled) {
                            Spacer(Modifier.height(8.dp))
                            OptionSwitch(
                                title = "Password authentication",
                                subtitle = "Allow SSH login with the account password.",
                                checked = c.sshPasswordAuth,
                                onChecked = { v -> vm.updateCustomization { it.copy(sshPasswordAuth = v) } }
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = c.sshAuthorizedKeys,
                                onValueChange = { v -> vm.updateCustomization { it.copy(sshAuthorizedKeys = v) } },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 6,
                                label = { Text("Authorized SSH public keys") },
                                supportingText = { Text("One public key per line") }
                            )
                        }
                    }
                }

                item {
                    CustomCard("Locale") {
                        OutlinedTextField(
                            value = c.timezone,
                            onValueChange = { v -> vm.updateCustomization { it.copy(timezone = v) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Timezone") },
                            supportingText = { Text("Example: Europe/Berlin") }
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = c.keyboard,
                            onValueChange = { v -> vm.updateCustomization { it.copy(keyboard = v) } },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Keyboard layout") },
                            supportingText = { Text("Example: de, gb, us") }
                        )
                    }
                }
                item {
                    CustomCard("Raspberry Pi Connect") {
                        OptionSwitch(
                            title = "Enable Raspberry Pi Connect",
                            subtitle = "Provision Connect for remote access.",
                            checked = c.piConnectEnabled,
                            onChecked = { v -> vm.updateCustomization { it.copy(piConnectEnabled = v) } }
                        )
                        if (c.piConnectEnabled) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = c.piConnectToken,
                                onValueChange = { v -> vm.updateCustomization { it.copy(piConnectToken = v) } },
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                label = { Text("Connect token (optional)") },
                                supportingText = {
                                    Text(
                                        if (initFormat == "rpi-preseed")
                                            "Leave empty to use device-identity enrolment."
                                        else "Token provisioning is used when a token is supplied."
                                    )
                                }
                            )
                        }
                    }
                }

                if (interfacesSupported) {
                    item {
                        CustomCard("Hardware interfaces") {
                            OptionSwitch("I²C", "Enable I²C on first boot.", c.enableI2C) {
                                v -> vm.updateCustomization { it.copy(enableI2C = v) }
                            }
                            OptionSwitch("SPI", "Enable SPI on first boot.", c.enableSPI) {
                                v -> vm.updateCustomization { it.copy(enableSPI = v) }
                            }
                            OptionSwitch("1-Wire", "Enable 1-Wire on first boot.", c.enable1Wire) {
                                v -> vm.updateCustomization { it.copy(enable1Wire = v) }
                            }
                            OptionSwitch("USB gadget", "Enable Raspberry Pi USB gadget support.", c.enableUsbGadget) {
                                v -> vm.updateCustomization { it.copy(enableUsbGadget = v) }
                            }
                            Spacer(Modifier.height(8.dp))
                            SerialModePicker(c.serialMode) {
                                v -> vm.updateCustomization { it.copy(serialMode = v) }
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = vm::continueFromCustomization,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text(if (supported && c.enabled) "SAVE AND CONTINUE" else "CONTINUE", fontWeight = FontWeight.Bold)
            }
        }
    }
}
@Composable
private fun CustomCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OptionSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SerialModePicker(selected: String, onSelected: (String) -> Unit) {
    Text("Serial", fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(5.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        listOf("Disabled", "Default", "Console", "Hardware", "Console & Hardware").forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                label = { Text(mode) }
            )
        }
    }
}
