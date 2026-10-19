package app.musix.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.musix.data.BackupException
import app.musix.data.Backups
import app.musix.log.AppLog
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Save and restore a backup of your settings and on-phone data, by hand or once a day to a folder. */
@Composable
fun BackupScreen(nav: NavController) {
    val context = LocalContext.current
    val backups = remember { Backups(context) }
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    var busy by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    // Re-reads the automatic backup's state after a change.
    var refresh by remember { mutableIntStateOf(0) }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = runCatching { backups.saveTo(uri) }
            result.exceptionOrNull()?.let { AppLog.w("Backup", "Saving a backup failed: ${it.message}") }
            busy = false
            snack.showSnackbar(if (result.isSuccess) "Backup saved" else "Couldn't save the backup")
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching { backups.setFolder(uri) }
            .onFailure { AppLog.w("Backup", "Couldn't use the chosen folder: ${it.message}") }
            .isSuccess
        refresh++
        if (ok) {
            busy = true
            scope.launch {
                val saved = backups.backUpToFolder()
                busy = false
                refresh++
                snack.showSnackbar(if (saved) "Backups on. The first one is saved." else "Couldn't write to that folder")
            }
        } else {
            scope.launch { snack.showSnackbar("Couldn't use that folder") }
        }
    }

    Screen(title = "Backup", nav = nav) { padding ->
        Column(
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            Hint(
                "A backup holds your settings, vibes, mixes, play history, Home and library layout, and anything else " +
                    "you set up in Musix. Passwords, tokens and API keys are never included: after restoring on a new phone, " +
                    "enter them again under Connections. Your library comes back with the next sync.",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                Button(enabled = !busy, onClick = { saver.launch("musix-backup-${stamp()}.json") }) {
                    Icon(Icons.Rounded.Save, null)
                    Spacer(Modifier.width(Space.Small))
                    Text("Save backup")
                }
                OutlinedButton(enabled = !busy, onClick = { opener.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) {
                    Icon(Icons.Rounded.Restore, null)
                    Spacer(Modifier.width(Space.Small))
                    Text("Restore")
                }
            }

            Text("Automatic backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = Space.Medium))
            Hint(
                "Once a day, Musix saves a backup to a folder you choose and keeps the last 7. " +
                    "To back up to your NAS, choose a folder from an app that shows it in the file picker " +
                    "(for example an SMB, Nextcloud or Syncthing app).",
            )
            val folder = remember(refresh) { backups.folder }
            val lastAt = remember(refresh) { backups.lastAutoAt }
            val lastError = remember(refresh) { backups.lastAutoError }
            if (folder != null) {
                Text("Saving to ${folderName(context, folder)}", style = MaterialTheme.typography.bodyLarge)
                val last = if (lastAt > 0) {
                    "Last backup " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lastAt))
                } else {
                    "No backup yet"
                }
                Hint(last)
                lastError?.let { Text("Last try failed: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.Small)) {
                    Button(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            val saved = backups.backUpToFolder()
                            busy = false
                            refresh++
                            snack.showSnackbar(if (saved) "Backup saved" else "Couldn't save the backup")
                        }
                    }) { Text("Back up now") }
                    OutlinedButton(enabled = !busy, onClick = { folderPicker.launch(null) }) { Text("Change folder") }
                    TextButton(enabled = !busy, onClick = {
                        backups.setFolder(null)
                        refresh++
                    }) { Text("Turn off") }
                }
            } else {
                OutlinedButton(enabled = !busy, onClick = { folderPicker.launch(null) }) {
                    Icon(Icons.Rounded.Folder, null)
                    Spacer(Modifier.width(Space.Small))
                    Text("Choose a folder")
                }
            }
        }
    }

    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text("Restore this backup?") },
            text = { Text("Your current settings, vibes, mixes and history are replaced by the backup's. Musix then restarts.") },
            confirmButton = {
                TextButton(onClick = {
                    restoreUri = null
                    busy = true
                    scope.launch {
                        try {
                            backups.restoreFrom(uri)
                            restartAfterRestore(context)
                        } catch (e: BackupException) {
                            busy = false
                            snack.showSnackbar(e.message ?: "Couldn't restore the backup")
                        } catch (e: Exception) {
                            AppLog.w("Backup", "Restoring a backup failed: ${e.message}")
                            busy = false
                            snack.showSnackbar("Couldn't restore the backup")
                        }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text("Cancel") } },
        )
    }
}

private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.ROOT).format(Date())

/** The folder's name as the file picker shows it. */
private fun folderName(context: Context, tree: Uri): String = runCatching {
    val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull() ?: "the chosen folder"

/** Settings and stores are kept in memory, so the app starts again to load the restored ones. */
private fun restartAfterRestore(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    val component = launch.component ?: return
    context.startActivity(Intent.makeRestartActivityTask(component))
    Runtime.getRuntime().exit(0)
}
