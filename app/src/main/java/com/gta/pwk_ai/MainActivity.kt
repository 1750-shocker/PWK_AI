package com.gta.pwk_ai

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.JsonParser
import com.gta.pwk_ai.data.PasswordEntry
import com.gta.pwk_ai.ui.PasswordViewModel
import com.gta.pwk_ai.ui.PasswordViewModelFactory
import com.gta.pwk_ai.ui.theme.PWK_AITheme
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class MainActivity : ComponentActivity() {

    // Legacy data class for backward compatibility during import
    private data class LegacyPasswordEntry(
        val account: String?,
        val des: String?,
        val password: String?
    )

    private val viewModel: PasswordViewModel by viewModels {
        PasswordViewModelFactory((application as PasswordApplication).repository)
    }

    private var exportPasswordsCache: List<PasswordEntry>? = null

    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            exportPasswordsCache?.let { passwords ->
                performExportToUri(it, passwords)
                exportPasswordsCache = null
            }
        }
    }

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            performImportFromUri(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestPermissions()

        setContent {
            PWK_AITheme {
                PasswordApp(viewModel, ::exportData, ::importData)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val permissions = arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            val notGrantedPermissions = permissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (notGrantedPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, notGrantedPermissions.toTypedArray(), 100)
            }
        }
    }

    private fun exportData(passwords: List<PasswordEntry>) {
        if (passwords.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_data_to_export), Toast.LENGTH_SHORT).show()
            return
        }

        exportPasswordsCache = passwords
        createDocumentLauncher.launch("passwords_backup.json")
    }

    private fun performExportToUri(uri: Uri, passwords: List<PasswordEntry>) {
        try {
            val gson = Gson()
            val json = gson.toJson(passwords)
            
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(json)
                }
            }
            Toast.makeText(this, getString(R.string.exported_success), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("PasswordManager", "Export failed", e)
            Toast.makeText(this, getString(R.string.export_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun importData() {
        openDocumentLauncher.launch(arrayOf("application/json", "*/*"))
    }

    private fun performImportFromUri(uri: Uri) {
        try {
            val json = contentResolver.openInputStream(uri)?.use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    reader.readText()
                }
            }
            
            if (json.isNullOrBlank()) {
                Toast.makeText(this, getString(R.string.backup_not_found), Toast.LENGTH_SHORT).show()
                return
            }
            
            val gson = Gson()
            val newPasswords = mutableListOf<PasswordEntry>()
            
            try {
                // Try parsing as the new format first
                val jsonArray = JsonParser.parseString(json).asJsonArray
                if (jsonArray.size() > 0) {
                    val firstElement = jsonArray[0].asJsonObject
                    // Check if it's the old format (contains 'des')
                    if (firstElement.has("des")) {
                        val itemType = object : TypeToken<List<LegacyPasswordEntry>>() {}.type
                        val legacyPasswords: List<LegacyPasswordEntry> = gson.fromJson(json, itemType)
                        newPasswords.addAll(legacyPasswords.map { 
                            PasswordEntry(
                                title = it.des ?: "Unknown",
                                account = it.account ?: "",
                                password = it.password ?: "",
                                note = ""
                            )
                        })
                    } else {
                        // New format
                        val itemType = object : TypeToken<List<PasswordEntry>>() {}.type
                        val passwords: List<PasswordEntry> = gson.fromJson(json, itemType)
                        newPasswords.addAll(passwords.map { it.copy(id = 0) })
                    }
                }
            } catch (e: Exception) {
                // Fallback in case of parsing errors
                throw Exception("Invalid JSON format", e)
            }
            
            if (newPasswords.isEmpty()) {
                Toast.makeText(this, "No valid passwords found to import", Toast.LENGTH_SHORT).show()
                return
            }
            
            viewModel.importPasswords(newPasswords)
            Toast.makeText(this, getString(R.string.imported_success, newPasswords.size), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("PasswordManager", "Import failed", e)
            Toast.makeText(this, getString(R.string.import_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordApp(
    viewModel: PasswordViewModel, 
    onExport: (List<PasswordEntry>) -> Unit, 
    onImport: () -> Unit
) {
    val passwords by viewModel.passwords.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var passwordToEdit by remember { mutableStateOf<PasswordEntry?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        searchFocusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.password_manager)) },
                actions = {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.export_to_download)) },
                            onClick = { 
                                showMenu = false
                                onExport(passwords)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.import_from_download)) },
                            onClick = { 
                                showMenu = false
                                onImport()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                passwordToEdit = null
                showAddDialog = true 
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.add_password))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.search(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .focusRequester(searchFocusRequester),
                placeholder = { Text(stringResource(id = R.string.search_hint)) },
                singleLine = true
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(passwords, key = { it.id }) { password ->
                    PasswordItem(
                        passwordEntry = password,
                        onEdit = { 
                            passwordToEdit = password
                            showAddDialog = true
                        },
                        onDelete = { viewModel.delete(password) }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddEditPasswordDialog(
                passwordEntry = passwordToEdit,
                onDismiss = { showAddDialog = false },
                onSave = { newEntry ->
                    if (passwordToEdit == null) {
                        viewModel.insert(newEntry)
                    } else {
                        viewModel.update(newEntry.copy(id = passwordToEdit!!.id))
                    }
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun PasswordItem(
    passwordEntry: PasswordEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showOptions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { showOptions = true },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = passwordEntry.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = passwordEntry.account, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "********", fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            
            if (passwordEntry.note.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = passwordEntry.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showOptions) {
        AlertDialog(
            onDismissRequest = { showOptions = false },
            title = { Text(passwordEntry.title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = passwordEntry.account,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = passwordEntry.password,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { 
                            copyToClipboard(context, context.getString(R.string.password), passwordEntry.password)
                            showOptions = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) { 
                        Text(stringResource(id = R.string.copy_password)) 
                    }
                    Button(
                        onClick = { 
                            copyToClipboard(context, context.getString(R.string.account), passwordEntry.account)
                            showOptions = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) { 
                        Text(stringResource(id = R.string.copy_account)) 
                    }
                    Button(
                        onClick = { 
                            showOptions = false
                            onEdit()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) { 
                        Text(stringResource(id = R.string.edit)) 
                    }
                    Button(
                        onClick = { 
                            showOptions = false
                            showDeleteConfirm = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        shape = MaterialTheme.shapes.medium
                    ) { 
                        Text(stringResource(id = R.string.delete)) 
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showOptions = false }) { Text(stringResource(id = R.string.close)) }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(id = R.string.delete_password_title)) },
            text = { Text(stringResource(id = R.string.delete_password_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text(stringResource(id = R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(id = R.string.no)) }
            }
        )
    }
}

@Composable
fun AddEditPasswordDialog(
    passwordEntry: PasswordEntry?,
    onDismiss: () -> Unit,
    onSave: (PasswordEntry) -> Unit
) {
    var title by remember { mutableStateOf(passwordEntry?.title ?: "") }
    var account by remember { mutableStateOf(passwordEntry?.account ?: "") }
    var password by remember { mutableStateOf(passwordEntry?.password ?: "") }
    var note by remember { mutableStateOf(passwordEntry?.note ?: "") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (passwordEntry == null) stringResource(id = R.string.add_password) else stringResource(id = R.string.edit_password)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(id = R.string.title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it },
                    label = { Text(stringResource(id = R.string.account)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(id = R.string.password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(id = R.string.note_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank() || account.isBlank() || password.isBlank()) {
                    Toast.makeText(context, context.getString(R.string.required_fields_error), Toast.LENGTH_SHORT).show()
                } else {
                    onSave(PasswordEntry(title = title, account = account, password = password, note = note))
                }
            }) {
                Text(stringResource(id = R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.cancel)) }
        }
    )
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.copied_to_clipboard, label), Toast.LENGTH_SHORT).show()
}
