package io.github.pronablua.deviceidchanger

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ManagerScreen(manager: ManagerViewModel) {
    val state by manager.state.collectAsStateWithLifecycle()
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
    val destination = ThreePaneScaffoldDestinationItem(if (state.selected == null) ListDetailPaneScaffoldRole.List else ListDetailPaneScaffoldRole.Detail, state.selected?.packageName)
    val scaffoldValue = calculateThreePaneScaffoldValue(directive.maxHorizontalPartitions, ListDetailPaneScaffoldDefaults.adaptStrategies(), destination)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, manager) {
        var firstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!firstResume) manager.refreshStatus()
                firstResume = false
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = state.selected != null) { manager.back() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(if (state.selected == null) "Device ID Changer" else "App identity", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (state.selected != null) {
                        IconButton(onClick = manager::back, enabled = !state.busy, modifier = Modifier.size(UiSpacing.touch)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back to apps")
                        }
                    }
                },
                actions = {
                    IconButton(
                        modifier = Modifier.size(UiSpacing.touch),
                        enabled = !state.busy && !state.checkingRoot,
                        onClick = {
                            manager.refreshStatus()
                            state.selected?.let(manager::select) ?: manager.refreshApps()
                        }
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh status and app settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            ListDetailPaneScaffold(
                directive = directive,
                value = scaffoldValue,
                modifier = Modifier.widthIn(max = UiSpacing.contentWidth).fillMaxSize(),
                listPane = {
                    AnimatedPane {
                        PaneFocus { HomeScreen(state, manager, Modifier.fillMaxSize()) }
                    }
                },
                detailPane = {
                    AnimatedPane {
                        PaneFocus {
                            if (state.selected != null) AppScreen(state, manager, Modifier.fillMaxSize())
                            else Box(Modifier.fillMaxSize().padding(UiSpacing.large), contentAlignment = Alignment.Center) {
                                MessageCard("Choose an app to get started", "Its saved override and runtime status will appear here.")
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun PaneFocus(content: @Composable () -> Unit) {
    val neutralFocus = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().focusRestorer(neutralFocus).focusGroup()) {
        Box(Modifier.fillMaxSize().focusRequester(neutralFocus).focusable()) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeScreen(state: ManagerState, manager: ManagerViewModel, modifier: Modifier) {
    val focus = LocalFocusManager.current
    val filteredApps = state.apps.filter {
        it.label.contains(state.query, ignoreCase = true) || it.packageName.contains(state.query, ignoreCase = true)
    }
    LazyColumn(modifier, contentPadding = PaddingValues(start = UiSpacing.normal, end = UiSpacing.normal, bottom = UiSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        item {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Column(Modifier.fillMaxWidth().padding(UiSpacing.large), verticalArrangement = Arrangement.spacedBy(UiSpacing.normal)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                        Icon(Icons.Outlined.Fingerprint, null, Modifier.size(UiSpacing.icon))
                        Text("APP IDENTITIES", style = MaterialTheme.typography.labelMedium)
                    }
                    Text("A separate ID.\nFor the app you choose.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
                    Text("Manage Android ID overrides and apply changes by restarting the selected app.", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.small), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                        Tag("Owner user 0")
                        Tag("ARM64 prototype")
                    }
                }
            }
        }
        item { ModuleCard(state, manager::refreshStatus) }
        if (!state.ownerUser) {
            item { MessageCard("Owner profile required", "This prototype manages apps only in Android’s owner profile, user 0. Open it from that profile to continue.") }
        } else {
            item {
                Text("Choose an app", modifier = Modifier.padding(top = UiSpacing.normal).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            }
            item {
                OutlinedTextField(
                        value = state.query,
                        onValueChange = manager::search,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search apps") },
                        placeholder = { Text("Search by name or package") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) IconButton(onClick = { manager.search("") }, modifier = Modifier.size(UiSpacing.touch)) { Icon(Icons.Outlined.Close, "Clear search") }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        shape = MaterialTheme.shapes.small
                )
            }
            item {
                Text("${filteredApps.size} apps · system and shared-UID apps excluded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.loadingApps) {
                item { LoadingRow("Loading your apps") }
            } else if (state.catalogError != null) {
                item { MessageCard("App list unavailable", state.catalogError) }
            } else if (filteredApps.isEmpty()) {
                item { MessageCard("No apps found", if (state.query.isBlank()) "Install an ordinary launchable app in owner user 0 to get started." else "Try another app name or package name.") }
            }
            items(filteredApps, key = { it.packageName }) { app ->
                val isSelected = state.selected?.packageName == app.packageName
                val supportingColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(onClick = { focus.clearFocus(); manager.select(app) }, modifier = Modifier.semantics { selected = isSelected }, enabled = !state.busy, shape = MaterialTheme.shapes.medium, color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().heightIn(min = UiSpacing.touch).padding(UiSpacing.normal), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.normal)) {
                        AppIcon(app, UiSpacing.appIcon)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UiSpacing.tiny)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = supportingColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (app.abi == AppAbi.Unsupported32Bit) Text("32-bit · unsupported", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = supportingColor)
                    }
                }
            }
        }
        item {
            Text("Android 10–17 compatibility target · 0.1.0 prototype", Modifier.padding(top = UiSpacing.small), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModuleCard(state: ManagerState, refresh: () -> Unit) {
    val available = state.status?.successful == true
    val runtime = state.status?.fields?.get("runtime")
    val moduleState = state.status?.fields?.get("module_state")
    val errorCode = state.status?.fields?.get("error_code")
    val protocolMatches = state.status?.fields?.get("protocol_version") == "1"
    val title = when {
        !state.ownerUser -> "Owner profile only"
        state.checkingRoot -> "Checking root access"
        errorCode == "module_missing" -> "Module is not installed"
        errorCode == "module_pending_reboot" -> "Phone restart required"
        available && !protocolMatches -> "Module version is incompatible"
        available && moduleState == "disabled" -> "Module is disabled"
        available && moduleState == "removed" -> "Module removal pending"
        available && moduleState == "incomplete" -> "Module installation is incomplete"
        available && runtime == "pending_reboot" -> "Phone restart required"
        available && runtime == "unavailable" -> "Runtime unavailable"
        available -> "Module available"
        else -> "Module connection unavailable"
    }
    val description = when {
        !state.ownerUser -> "Configuration is disabled in this profile."
        state.checkingRoot -> "Allow this app in your root manager if prompted."
        errorCode == "module_missing" -> "Install the Device ID Changer module ZIP in your root manager, then restart your phone to finish setup."
        errorCode == "module_pending_reboot" -> "The module is staged for installation. Restart your phone once to finish setup."
        available && !protocolMatches -> "Install the module release that matches this manager before making changes."
        available && moduleState == "disabled" -> "Enable the module in your root manager, then restart your phone."
        available && moduleState == "removed" -> "The module is marked for removal in your root manager."
        available && moduleState == "incomplete" -> "Install a complete module ZIP before applying an override."
        available && runtime == "pending_reboot" -> "Restart your phone to load the installed runtime."
        available && runtime == "unavailable" -> "Check that Zygisk and this module are enabled in your root manager."
        available -> "Runtime is unverified. Saved settings are checked separately for each app."
        else -> state.status?.message.orEmpty().ifBlank { "Install the module, restart your phone, and grant root access." }
    }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(UiSpacing.normal), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.normal)) {
                Icon(Icons.Outlined.Shield, null, tint = MaterialTheme.colorScheme.primary)
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (state.checkingRoot) CircularProgressIndicator(Modifier.size(UiSpacing.icon))
            }
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!available && !state.checkingRoot && state.ownerUser) {
                TextButton(onClick = refresh, modifier = Modifier.heightIn(min = UiSpacing.touch)) { Text("Check again") }
            }
        }
    }
}

@Composable
private fun AppScreen(state: ManagerState, manager: ManagerViewModel, modifier: Modifier) {
    val app = state.selected ?: return
    val focus = LocalFocusManager.current
    val cleanedId = Identity.cleanId(state.editedId)
    val invalid = state.editedId.isNotEmpty() && !Identity.validId(cleanedId)
    val observed = state.selectionReply?.fields?.get("observed_id").orEmpty()
    LazyColumn(modifier, contentPadding = PaddingValues(start = UiSpacing.normal, end = UiSpacing.normal, bottom = UiSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(UiSpacing.large)) {
        item {
            Row(Modifier.padding(vertical = UiSpacing.normal), horizontalArrangement = Arrangement.spacedBy(UiSpacing.normal), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, UiSpacing.detailIcon)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    Text(app.label, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                    SelectionContainer { Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("Owner user 0 · UID ${app.uid}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (!state.moduleReady) {
            item { ModuleCard(state, manager::refreshStatus) }
        }
        if (app.abi != AppAbi.Arm64) {
            item {
                MessageCard(
                    if (app.abi == AppAbi.Unsupported32Bit) "32-bit app is unsupported" else "Process architecture is unconfirmed",
                    if (app.abi == AppAbi.Unsupported32Bit) "This module runs only in ARM64 app processes. Changes are disabled for this app."
                    else "The app’s library path does not establish its process architecture. The override still needs testing in the selected app."
                )
            }
        }
        if (state.selectionLoading) {
            item { LoadingRow("Reading saved configuration") }
        } else if (state.selectionReply?.successful != true) {
            item { MessageCard("Settings could not be read", state.selectionReply?.message.orEmpty().ifBlank { "Refresh this screen to try again." }) }
        }
        item {
            Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().padding(UiSpacing.large), verticalArrangement = Arrangement.spacedBy(UiSpacing.normal)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                        Icon(Icons.Outlined.Fingerprint, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Android ID", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                    }
                    ValueRow("Saved override", if (state.selectionLoading) "Loading…" else if (state.overrideEnabled) state.configuredId else if (state.selectionReply?.successful == true) "None" else "Unknown", monospace = state.overrideEnabled)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ValueRow("Observed in target app", observed.ifBlank { "Not observed" }, monospace = observed.isNotBlank())
                    Text("A saved override does not verify a runtime read. The manager’s own Android ID is not used to check this app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.normal)) {
                Text("Choose an identifier", modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = state.editedId,
                    onValueChange = manager::editId,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canEdit,
                    label = { Text("New Android ID") },
                    placeholder = { Text("e.g. 7a9f21c46b08d3e5") },
                    supportingText = { Text(if (invalid) "Enter exactly 16 hexadecimal characters: 0–9, a–f." else "16 hexadecimal characters · changes apply to this app") },
                    isError = invalid,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    shape = MaterialTheme.shapes.small
                )
                OutlinedButton(onClick = manager::randomize, enabled = state.canEdit, modifier = Modifier.heightIn(min = UiSpacing.touch)) {
                    Icon(Icons.Outlined.Shuffle, null, Modifier.size(UiSpacing.icon))
                    Spacer(Modifier.width(UiSpacing.small))
                    Text("Generate random ID")
                }
            }
        }
        if (state.notice != null) {
            item {
                MessageCard(state.notice.title, state.notice.message, if (state.notice.successful) Icons.Outlined.CheckCircle else Icons.Outlined.Info, announce = true, isError = !state.notice.successful)
            }
        }
        if (state.busy) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Saving the configuration and restarting the selected app…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                Button(onClick = { focus.clearFocus(); manager.apply() }, modifier = Modifier.fillMaxWidth().heightIn(min = UiSpacing.button), enabled = state.canEdit && Identity.validId(cleanedId)) {
                    Icon(Icons.Outlined.RestartAlt, null, Modifier.size(UiSpacing.icon))
                    Spacer(Modifier.width(UiSpacing.small))
                    Text("Apply and restart app", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(onClick = { focus.clearFocus(); manager.restore() }, modifier = Modifier.fillMaxWidth().heightIn(min = UiSpacing.button), enabled = state.canRestore) {
                    Icon(Icons.Outlined.Restore, null, Modifier.size(UiSpacing.icon))
                    Spacer(Modifier.width(UiSpacing.small))
                    Text("Restore and restart app")
                }
                Text("The selected app will close and reopen. Restore removes its override. Neither action clears app data.", Modifier.padding(UiSpacing.tiny), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AppIcon(app: AppEntry, size: Dp) {
    Surface(Modifier.size(size), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(contentAlignment = Alignment.Center) {
            if (app.icon != null) Image(app.icon.asImageBitmap(), contentDescription = null, modifier = Modifier.padding(UiSpacing.tiny).fillMaxSize())
            else Icon(Icons.Outlined.Apps, null, Modifier.size(UiSpacing.icon), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Text(text, Modifier.padding(UiSpacing.small), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ValueRow(label: String, value: String, monospace: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.titleMedium, fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default)
        }
    }
}

@Composable
private fun MessageCard(title: String, message: String, icon: ImageVector = Icons.Outlined.Info, announce: Boolean = false, isError: Boolean = false) {
    val containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(modifier = Modifier.semantics { if (announce) liveRegion = LiveRegionMode.Polite }, shape = MaterialTheme.shapes.medium, color = containerColor, contentColor = contentColor) {
        Row(Modifier.fillMaxWidth().padding(UiSpacing.normal), horizontalArrangement = Arrangement.spacedBy(UiSpacing.normal)) {
            Icon(icon, null, Modifier.size(UiSpacing.icon), tint = contentColor)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UiSpacing.small)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LoadingRow(label: String) {
    Row(Modifier.fillMaxWidth().padding(UiSpacing.normal), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.normal)) {
        CircularProgressIndicator(Modifier.size(UiSpacing.icon))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
