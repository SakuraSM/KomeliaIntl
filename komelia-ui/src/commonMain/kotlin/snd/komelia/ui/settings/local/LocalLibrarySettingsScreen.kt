package snd.komelia.ui.settings.local

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.Res
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_library_add
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_library_auto_scan
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_library_not_available
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_library_refresh
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_library_remove
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_library_sources
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_library_excluded_books
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_library_excluded_books_description
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.local_library_restore_book
import io.github.snd_r.komelia.ui.komelia_ui.generated.resources.settings_navigation_local_library
import org.jetbrains.compose.resources.stringResource
import snd.komelia.ui.LocalKomeliaLayout
import snd.komelia.ui.LocalViewModelFactory
import snd.komelia.ui.common.components.SettingsSection
import snd.komelia.ui.dialogs.permissions.StoragePermissionRequestDialog
import snd.komelia.ui.settings.SettingsScreenContainer
import snd.komga.client.library.ScanInterval

class LocalLibrarySettingsScreen : Screen {
    @Composable
    override fun Content() {
        val factory = LocalViewModelFactory.current
        val vm = rememberScreenModel { factory.getLocalLibraryViewModel() }
        var selectingDirectory by remember { mutableStateOf(false) }
        val scanState by vm.scanState.collectAsState()
        val layout = LocalKomeliaLayout.current

        LaunchedEffect(Unit) { vm.initialize() }
        if (selectingDirectory) {
            StoragePermissionRequestDialog { directory ->
                selectingDirectory = false
                directory?.let(vm::addLibrary)
            }
        }

        SettingsScreenContainer(stringResource(Res.string.settings_navigation_local_library)) {
            if (!vm.isAvailable) {
                Text(
                    stringResource(Res.string.local_library_not_available),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@SettingsScreenContainer
            }

            SettingsSection(
                title = stringResource(Res.string.local_library_sources),
                supportingText = scanState.error ?: vm.error,
            ) {
                Button(onClick = { selectingDirectory = true }, enabled = !vm.loading) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(stringResource(Res.string.local_library_add))
                }
                if (vm.loading || scanState.scanningLibraryId != null) {
                    CircularProgressIndicator()
                }
                vm.libraries.forEach { library ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(layout.controlSpacing),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(layout.controlSpacing),
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text(library.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    library.root,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { vm.refresh(library.id) }, enabled = !vm.loading) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = stringResource(Res.string.local_library_refresh),
                                )
                            }
                            IconButton(onClick = { vm.remove(library.id) }, enabled = !vm.loading) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = stringResource(Res.string.local_library_remove),
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(Res.string.local_library_auto_scan))
                            Switch(
                                checked = library.scanInterval != ScanInterval.DISABLED,
                                onCheckedChange = { vm.setScheduled(library.id, it) },
                            )
                        }
                    }
                }
            }

            if (vm.excludedBooks.isNotEmpty()) {
                SettingsSection(
                    title = stringResource(Res.string.local_library_excluded_books),
                    supportingText = stringResource(Res.string.local_library_excluded_books_description),
                ) {
                    vm.excludedBooks.forEach { exclusion ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(layout.controlSpacing),
                        ) {
                            Icon(
                                Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(exclusion.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    exclusion.libraryName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { vm.restore(exclusion) }, enabled = !vm.loading) {
                                Icon(
                                    Icons.Rounded.Restore,
                                    contentDescription = stringResource(Res.string.local_library_restore_book),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
