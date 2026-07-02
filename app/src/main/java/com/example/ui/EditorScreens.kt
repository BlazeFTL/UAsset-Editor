package com.example.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.GitTreeEntry
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.TabState
import com.example.viewmodel.SearchMatch
import com.example.viewmodel.FileSortOrder
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.launch

// Color tokens for our cohesive modern light theme
val LightBg = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightBorder = Color(0xFFE2E8F0)
val EditorActiveLineBg = Color(0xFFF1F5F9)
val LineNumberColor = Color(0xFF64748B) // More visible slate color
val ActiveLineNumberColor = Color(0xFF1E3A8A) // Highly visible active line color
val HighlightMatchBg = Color(0xFFFEF08A)
val ActiveMatchBg = Color(0xFFFDE047)

@Composable
fun AppContent(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val hasPermission by viewModel.hasStoragePermission.collectAsState()
    val context = LocalContext.current

    // Request storage permission on first load
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setStoragePermissionGranted(isGranted)
        if (!isGranted) {
            Toast.makeText(context, "Storage permission is optional; offline saving is active.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.setStoragePermissionGranted(true)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LightBg
    ) {
        when (currentScreen) {
            "LOGIN" -> LoginScreen(viewModel)
            "WORKSPACE" -> WorkspaceScreen(viewModel)
            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: MainViewModel) {
    val savedToken by viewModel.githubToken.collectAsState()
    val savedOwner by viewModel.repoOwner.collectAsState()
    val savedRepo by viewModel.repoName.collectAsState()
    val savedBranch by viewModel.branchName.collectAsState()

    var token by remember(savedToken) { mutableStateOf(savedToken ?: "") }
    var owner by remember(savedOwner) { mutableStateOf(savedOwner) }
    var repo by remember(savedRepo) { mutableStateOf(savedRepo) }
    var branch by remember(savedBranch) { mutableStateOf(savedBranch) }

    val isSyncing by viewModel.isSyncing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    // Device Flow States
    val deviceUserCode by viewModel.deviceUserCode.collectAsState()
    val deviceVerificationUri by viewModel.deviceVerificationUri.collectAsState()
    val deviceAuthPolling by viewModel.deviceAuthPolling.collectAsState()
    val deviceAuthError by viewModel.deviceAuthError.collectAsState()

    var selectedAuthTab by remember { mutableStateOf(0) } // 0: GitHub Account Login, 1: PAT Login

    val context = LocalContext.current

    LaunchedEffect(successMessage) {
        successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg)
            .statusBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.EditNote,
                contentDescription = "Editor Logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )

            Text(
                text = "uBlock Filter Editor",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )

            Text(
                text = "A high-performance editor with syntax highlighting, differential syncing, and GitHub integration.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (errorMessage != null || deviceAuthError != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: deviceAuthError ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Auth Selection Tabs
            TabRow(
                selectedTabIndex = selectedAuthTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedAuthTab == 0,
                    onClick = { selectedAuthTab = 0 },
                    text = { Text("GitHub Login") },
                    icon = { Icon(Icons.Filled.AccountCircle, contentDescription = "OAuth Login") }
                )
                Tab(
                    selected = selectedAuthTab == 1,
                    onClick = { selectedAuthTab = 1 },
                    text = { Text("Use PAT") },
                    icon = { Icon(Icons.Filled.Key, contentDescription = "PAT Login") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (selectedAuthTab == 0) {
                // GitHub OAuth Login (Device Flow)
                if (deviceUserCode == null) {
                    Text(
                        text = "Sign in securely via GitHub OAuth. No need to manually create, copy, or paste complex personal access tokens.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Button(
                        onClick = { viewModel.startDeviceFlowAuth() },
                        enabled = !deviceAuthPolling,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("initiate_oauth_button")
                    ) {
                        if (deviceAuthPolling) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Filled.Lock, contentDescription = "Lock icon")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign In with GitHub")
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "YOUR LOGIN CODE",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )

                            Text(
                                text = deviceUserCode ?: "",
                                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("GitHub Activation Code", deviceUserCode)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Copy Code")
                            }
                        }
                    }

                    Text(
                        text = "1. Click the button below to open GitHub.\n2. Paste the activation code above and click Authorize.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deviceVerificationUri ?: "https://github.com/login/device"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_activation_page_button")
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open link")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Activation Page")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Waiting for authorization on GitHub...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    TextButton(
                        onClick = { viewModel.cancelDeviceFlowAuth() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Cancel, contentDescription = "Cancel")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel Sign In")
                    }
                }
            } else {
                // Personal Access Token (PAT) Login
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("GitHub Personal Access Token (PAT)") },
                    placeholder = { Text("github_pat_...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_token_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { 
                        viewModel.saveGitHubCredentials(
                            token = token, 
                            owner = savedOwner.ifEmpty { "uBlockOrigin" }, 
                            repo = savedRepo.ifEmpty { "uAssets" }, 
                            branch = savedBranch.ifEmpty { "master" }
                        ) 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("authenticate_button"),
                    enabled = token.isNotEmpty() && !isSyncing
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Filled.Lock, contentDescription = "Auth", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Authenticate & Synchronize")
                    }
                }
            }

            TextButton(
                onClick = { viewModel.skipLogin() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("offline_mode_button")
            ) {
                Text("Continue in Offline/Viewer Mode", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(viewModel: MainViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val activeTabPath by viewModel.activeTabPath.collectAsState()
    val openTabs by viewModel.openTabs.collectAsState()
    val filesList by viewModel.allRepoFiles.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    val pinnedFiles by viewModel.pinnedFiles.collectAsState()
    val fileSortOrder by viewModel.fileSortOrder.collectAsState()
    val modifiedFiles by viewModel.modifiedFiles.collectAsState()

    val repoOwner by viewModel.repoOwner.collectAsState()
    val repoName by viewModel.repoName.collectAsState()
    val branchName by viewModel.branchName.collectAsState()

    val context = LocalContext.current
    var showSwitchRepoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(successMessage) {
        successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    if (showSwitchRepoDialog) {
        val currentOwner by viewModel.repoOwner.collectAsState()
        val currentRepo by viewModel.repoName.collectAsState()
        val currentBranch by viewModel.branchName.collectAsState()
        
        var dialogOwner by remember { mutableStateOf(currentOwner) }
        var dialogRepo by remember { mutableStateOf(currentRepo) }
        var dialogBranch by remember { mutableStateOf(currentBranch) }
        
        AlertDialog(
            onDismissRequest = { showSwitchRepoDialog = false },
            title = { Text("Switch Repository") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter GitHub repository details to switch your active workspace directory.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    
                    OutlinedTextField(
                        value = dialogOwner,
                        onValueChange = { dialogOwner = it },
                        label = { Text("Owner") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dialogRepo,
                        onValueChange = { dialogRepo = it },
                        label = { Text("Repository Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = dialogBranch,
                        onValueChange = { dialogBranch = it },
                        label = { Text("Branch") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Presets:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                dialogOwner = "uBlockOrigin"
                                dialogRepo = "uAssets"
                                dialogBranch = "master"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("uBlock Assets", style = MaterialTheme.typography.labelSmall)
                        }
                        
                        Button(
                            onClick = {
                                dialogOwner = "BlazeFTL"
                                dialogRepo = "My-Filters"
                                dialogBranch = "master"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("BlazeFTL Filters", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSwitchRepoDialog = false
                        viewModel.updateRepositorySelection(dialogOwner, dialogRepo, dialogBranch)
                    }
                ) {
                    Text("Switch")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchRepoDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = LightSurface,
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                ) {
                    FileBrowserPanel(
                        filesList = filesList,
                        isSyncing = isSyncing,
                        pinnedFiles = pinnedFiles,
                        fileSortOrder = fileSortOrder,
                        modifiedFiles = modifiedFiles,
                        onFileClick = { path ->
                            viewModel.openFileInTab(path)
                            coroutineScope.launch { drawerState.close() }
                        },
                        onSyncClick = { viewModel.syncRepositoryTree() },
                        onPinToggle = { viewModel.togglePinFile(it) },
                        onSortOrderChange = { viewModel.setFileSortOrder(it) },
                        repoOwner = repoOwner,
                        repoName = repoName,
                        branchName = branchName
                    )
                }
            }
        ) {
            Scaffold(
                containerColor = LightBg,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    WorkspaceTopBar(
                        activeTabPath = activeTabPath,
                        openTabs = openTabs,
                        isSyncing = isSyncing,
                        onMenuClick = { coroutineScope.launch { drawerState.open() } },
                        onTabSelect = { viewModel.selectTab(it) },
                        onTabClose = { viewModel.closeTab(it) },
                        onCommitClick = { viewModel.commitAndPushActiveTab() },
                        onLogoutClick = { viewModel.logout() },
                        onSwitchRepoClick = { showSwitchRepoDialog = true },
                        onQuickSwitchRepo = { owner, repo, branch ->
                            viewModel.updateRepositorySelection(owner, repo, branch)
                        },
                        repoOwner = repoOwner,
                        repoName = repoName,
                        branchName = branchName
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (activeTabPath == null) {
                            EmptyWorkspaceState { coroutineScope.launch { drawerState.open() } }
                        } else {
                            val activeTab = openTabs.find { it.path == activeTabPath }
                            if (activeTab != null) {
                                EditorWorkspace(
                                    tabState = activeTab,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyWorkspaceState(onOpenDrawer: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = "No file open",
                tint = LineNumberColor,
                modifier = Modifier.size(80.dp)
            )
            Text(
                text = "No File Open",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Open the repository drawer to select and sync files.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onOpenDrawer,
                modifier = Modifier.testTag("open_drawer_button")
            ) {
                Icon(Icons.Filled.MenuOpen, contentDescription = "Browse")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse Repository Files")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserPanel(
    filesList: List<GitTreeEntry>,
    isSyncing: Boolean,
    pinnedFiles: Set<String>,
    fileSortOrder: FileSortOrder,
    modifiedFiles: Set<String>,
    onFileClick: (String) -> Unit,
    onSyncClick: () -> Unit,
    onPinToggle: (String) -> Unit,
    onSortOrderChange: (FileSortOrder) -> Unit,
    repoOwner: String,
    repoName: String,
    branchName: String
) {
    var searchFilter by remember { mutableStateOf("") }
    
    val sortedAndFilteredFiles = remember(filesList, searchFilter, pinnedFiles, fileSortOrder, modifiedFiles) {
        val filtered = filesList.filter { it.path.contains(searchFilter, ignoreCase = true) }
        when (fileSortOrder) {
            FileSortOrder.ALPHABETICAL -> {
                filtered.sortedBy { it.path.substringAfterLast('/') }
            }
            FileSortOrder.PINNED_FIRST -> {
                filtered.sortedWith(compareByDescending<GitTreeEntry> { pinnedFiles.contains(it.path) }
                    .thenBy { it.path.substringAfterLast('/') })
            }
            FileSortOrder.MODIFIED_FIRST -> {
                filtered.sortedWith(compareByDescending<GitTreeEntry> { modifiedFiles.contains(it.path) }
                    .thenByDescending { pinnedFiles.contains(it.path) }
                    .thenBy { it.path.substringAfterLast('/') })
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Active Repository:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$repoOwner/$repoName",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = "Branch: $branchName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Repository Files",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = onSyncClick,
                enabled = !isSyncing,
                modifier = Modifier.testTag("sync_files_button")
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Sync, contentDescription = "Sync", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        OutlinedTextField(
            value = searchFilter,
            onValueChange = { searchFilter = it },
            placeholder = { Text("Search files in repo...") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("file_search_input"),
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search icon") }
        )

        // Sorting Option Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sort:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            AssistChip(
                onClick = { onSortOrderChange(FileSortOrder.PINNED_FIRST) },
                label = { Text("Pinned", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null, modifier = Modifier.size(12.dp)) },
                colors = if (fileSortOrder == FileSortOrder.PINNED_FIRST) {
                    AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer, labelColor = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    AssistChipDefaults.assistChipColors()
                },
                border = null
            )
            
            AssistChip(
                onClick = { onSortOrderChange(FileSortOrder.MODIFIED_FIRST) },
                label = { Text("Modified", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(12.dp)) },
                colors = if (fileSortOrder == FileSortOrder.MODIFIED_FIRST) {
                    AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer, labelColor = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    AssistChipDefaults.assistChipColors()
                },
                border = null
            )
            
            AssistChip(
                onClick = { onSortOrderChange(FileSortOrder.ALPHABETICAL) },
                label = { Text("A-Z", style = MaterialTheme.typography.labelSmall) },
                colors = if (fileSortOrder == FileSortOrder.ALPHABETICAL) {
                    AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer, labelColor = MaterialTheme.colorScheme.onPrimaryContainer)
                } else {
                    AssistChipDefaults.assistChipColors()
                },
                border = null
            )
        }

        Divider(color = LightBorder)

        if (sortedAndFilteredFiles.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = if (isSyncing) "Syncing repository..." else "No files found. Try syncing.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .testTag("file_browser_list"),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(sortedAndFilteredFiles) { _, entry ->
                    val isPinned = pinnedFiles.contains(entry.path)
                    val isModified = modifiedFiles.contains(entry.path)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onFileClick(entry.path) }
                            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.Description,
                            contentDescription = "File icon",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onPinToggle(entry.path) }
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = entry.path,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (isModified) {
                            Surface(
                                color = Color(0xFFF59E0B),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(horizontal = 6.dp)
                            ) {
                                Text(
                                    text = "MOD",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { onPinToggle(entry.path) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = "Pin File",
                                tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorkspaceTopBar(
    activeTabPath: String?,
    openTabs: List<TabState>,
    isSyncing: Boolean,
    onMenuClick: () -> Unit,
    onTabSelect: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onCommitClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSwitchRepoClick: () -> Unit,
    onQuickSwitchRepo: (String, String, String) -> Unit,
    repoOwner: String,
    repoName: String,
    branchName: String
) {
    Surface(
        color = LightSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMenuClick, modifier = Modifier.testTag("drawer_menu_button")) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "uBlock Editor",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "$repoOwner/$repoName ($branchName)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                val hasModifiedTab = openTabs.any { it.isModified }
                if (hasModifiedTab && activeTabPath != null) {
                    IconButton(
                        onClick = onCommitClick,
                        modifier = Modifier.testTag("commit_button")
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = "Commit to GitHub", tint = Color(0xFFF59E0B))
                    }
                }

                // Dedicated Quick-Switch Repository Button
                val isUAssets = repoOwner == "uBlockOrigin" && repoName == "uAssets"
                val switchTargetLabel = if (isUAssets) "My-Filters" else "uAssets"
                
                Button(
                    onClick = {
                        val targetOwner = if (isUAssets) "BlazeFTL" else "uBlockOrigin"
                        val targetRepo = if (isUAssets) "My-Filters" else "uAssets"
                        onQuickSwitchRepo(targetOwner, targetRepo, "master")
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("quick_switch_repo_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(switchTargetLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                
                Spacer(modifier = Modifier.width(4.dp))

                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.testTag("menu_dropdown_button")) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Active Repo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text("$repoOwner/$repoName", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Text("Branch: $branchName", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            onClick = {},
                            enabled = false
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Browse Files (Left Drawer)") },
                            onClick = {
                                showMenu = false
                                onMenuClick()
                            },
                            leadingIcon = { Icon(Icons.Filled.Menu, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Switch Repository") },
                            onClick = {
                                showMenu = false
                                onSwitchRepoClick()
                            },
                            leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Logout") },
                            onClick = {
                                showMenu = false
                                onLogoutClick()
                            },
                            leadingIcon = { Icon(Icons.Filled.ExitToApp, contentDescription = null) }
                        )
                    }
                }
            }

            if (openTabs.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = openTabs.indexOfFirst { it.path == activeTabPath }
                        .coerceIn(0, (openTabs.size - 1).coerceAtLeast(0)),
                    edgePadding = 12.dp,
                    containerColor = LightSurface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    divider = {}
                ) {
                    openTabs.forEach { tab ->
                        val isSelected = tab.path == activeTabPath
                        Tab(
                            selected = isSelected,
                            onClick = { onTabSelect(tab.path) },
                            modifier = Modifier
                                .testTag("tab_${tab.path.replace("/", "_")}")
                                .background(if (isSelected) LightBg else LightSurface)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (tab.isModified) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFF59E0B))
                                    )
                                }
                                Text(
                                    text = tab.path.substringAfterLast("/"),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close tab",
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onTabClose(tab.path) }
                                )
                            }
                        }
                    }
                }
            }
            Divider(color = LightBorder)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorWorkspace(
    tabState: TabState,
    viewModel: MainViewModel
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val isSearchOpen by viewModel.isSearchOpen.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val replaceQuery by viewModel.replaceQuery.collectAsState()
    val searchMatches by viewModel.searchMatches.collectAsState()
    val currentMatchIndex by viewModel.currentMatchIndex.collectAsState()
    val isRegexSearch by viewModel.isRegexSearch.collectAsState()
    val isCaseSensitiveSearch by viewModel.isCaseSensitiveSearch.collectAsState()

    val wordWrap by viewModel.wordWrap.collectAsState()
    val autoIndent by viewModel.autoIndent.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    // Shared horizontal scroll state across lines when word wrapping is off
    val sharedHorizontalScrollState = rememberScrollState()

    // Line list state
    val lazyListState = rememberLazyListState()

    // Track scroll state of each tab to restore it
    LaunchedEffect(tabState.path, tabState.scrollRestoreTrigger) {
        if (tabState.scrollIndex < tabState.lines.size) {
            lazyListState.scrollToItem(tabState.scrollIndex, tabState.scrollOffset)
        }
    }

    LaunchedEffect(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset) {
        viewModel.saveTabScrollState(
            tabState.path,
            lazyListState.firstVisibleItemIndex,
            lazyListState.firstVisibleItemScrollOffset
        )
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    LaunchedEffect(tabState.activeLineIndex, imeBottom) {
        tabState.activeLineIndex?.let { index ->
            if (index in 0 until tabState.lines.size) {
                // Ensure active line is scrolled into view smoothly when selection or keyboard changes
                lazyListState.animateScrollToItem(index)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightBg)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointers = event.changes
                        if (pointers.size >= 2) {
                            val p1 = pointers[0]
                            val p2 = pointers[1]
                            if (p1.pressed && p2.pressed) {
                                val prevPos1 = p1.previousPosition
                                val prevPos2 = p2.previousPosition
                                val currentPos1 = p1.position
                                val currentPos2 = p2.position
                                
                                val dx = currentPos1.x - currentPos2.x
                                val dy = currentPos1.y - currentPos2.y
                                val currentDistance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                                
                                val pdx = prevPos1.x - prevPos2.x
                                val pdy = prevPos1.y - prevPos2.y
                                val prevDistance = Math.sqrt((pdx * pdx + pdy * pdy).toDouble()).toFloat()
                                
                                if (prevDistance > 0f && currentDistance > 0f) {
                                    val scale = currentDistance / prevDistance
                                    if (Math.abs(scale - 1f) > 0.005f) {
                                        val newSize = (fontSize * scale).coerceIn(10f, 30f)
                                        viewModel.setFontSize(newSize)
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LightSurface)
        ) {
            // Find & Replace Panel (Collapsible)
            if (isSearchOpen) {
                FindReplacePanel(
                    query = searchQuery,
                    replaceQuery = replaceQuery,
                    matchesCount = searchMatches.size,
                    currentMatchIdx = currentMatchIndex,
                    isRegex = isRegexSearch,
                    isCase = isCaseSensitiveSearch,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onReplaceQueryChange = { viewModel.setReplaceQuery(it) },
                    onNext = {
                        viewModel.nextMatch()?.let { match ->
                            coroutineScope.launch { lazyListState.scrollToItem(match.lineIndex) }
                        }
                    },
                    onPrev = {
                        viewModel.prevMatch()?.let { match ->
                            coroutineScope.launch { lazyListState.scrollToItem(match.lineIndex) }
                        }
                    },
                    onReplace = { viewModel.replaceCurrentMatch() },
                    onReplaceAll = { viewModel.replaceAllMatches() },
                    onToggleRegex = { viewModel.toggleRegexSearch() },
                    onToggleCase = { viewModel.toggleCaseSensitiveSearch() },
                    onClose = { viewModel.toggleSearch() }
                )
                Divider(color = LightBorder)
            }

            // Sequential toolbars with no container layout to prevent any potential measurement gaps
            EditorSettingsToolbar(
                wordWrap = wordWrap,
                onWordWrapToggle = { viewModel.toggleWordWrap() },
                onFindToggle = { viewModel.toggleSearch() },
                onUndoClick = { viewModel.undo() },
                onRedoClick = { viewModel.redo() },
                hasUndo = tabState.undoStack.isNotEmpty(),
                hasRedo = tabState.redoStack.isNotEmpty(),
                activeLineIndex = tabState.activeLineIndex,
                linesCount = tabState.lines.size,
                onGoToLine = { lineNum ->
                    val index = (lineNum - 1).coerceIn(0, tabState.lines.size - 1)
                    coroutineScope.launch {
                        lazyListState.scrollToItem(index)
                        viewModel.selectLine(index)
                    }
                },
                onZoomIn = { viewModel.setFontSize(fontSize + 1f) },
                onZoomOut = { viewModel.setFontSize(fontSize - 1f) },
                onScrollToTop = {
                    coroutineScope.launch {
                        lazyListState.scrollToItem(0)
                        viewModel.selectLine(0)
                    }
                },
                onScrollToBottom = {
                    coroutineScope.launch {
                        val lastIdx = (tabState.lines.size - 1).coerceAtLeast(0)
                        lazyListState.scrollToItem(lastIdx)
                        viewModel.selectLine(lastIdx)
                    }
                },
                onRefreshClick = { viewModel.refreshActiveFileContent() },
                isSyncing = isSyncing
            )

            Divider(color = LightBorder)
        }

            // Dynamic width computation for line numbers to prevent layout shifting
            val lineNumbersWidth = remember(tabState.lines.size) {
                val digits = tabState.lines.size.toString().length
                (digits * 9 + 20).coerceAtLeast(36).dp
            }

            // Lazy high-performance scroll rendering
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(bottom = 120.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("editor_lazy_column")
            ) {
                itemsIndexed(
                    items = tabState.lines,
                    key = { index, _ -> "$index-${tabState.path}" }
                ) { index, lineText ->
                    LineRow(
                        index = index,
                        text = lineText,
                        isActive = index == tabState.activeLineIndex,
                        wordWrap = wordWrap,
                        fontSize = fontSize,
                        sharedHorizontalScrollState = sharedHorizontalScrollState,
                        lineNumbersWidth = lineNumbersWidth,
                        matches = searchMatches.filter { it.lineIndex == index },
                        activeMatchStartChar = if (currentMatchIndex >= 0 && searchMatches[currentMatchIndex].lineIndex == index) searchMatches[currentMatchIndex].startChar else null,
                        onLineClick = { viewModel.selectLine(index) },
                        onTextChange = { viewModel.updateLine(index, it) },
                        onEnterPressed = { viewModel.insertLineBelowWithText(index, it) },
                        onBackspaceAtStart = { viewModel.mergeLineWithPrevious(index) }
                    )
                }
            }
        }
    }

@Composable
fun LineRow(
    index: Int,
    text: String,
    isActive: Boolean,
    wordWrap: Boolean,
    fontSize: Float,
    sharedHorizontalScrollState: ScrollState,
    lineNumbersWidth: androidx.compose.ui.unit.Dp,
    matches: List<SearchMatch>,
    activeMatchStartChar: Int?,
    onLineClick: () -> Unit,
    onTextChange: (String) -> Unit,
    onEnterPressed: (String) -> Unit,
    onBackspaceAtStart: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isActive) {
        if (isActive) {
            focusRequester.requestFocus()
        }
    }

    var textFieldValue by remember(isActive) {
        val prependedText = if (isActive) "\u200B$text" else text
        mutableStateOf(
            TextFieldValue(
                text = prependedText,
                selection = TextRange(prependedText.length)
            )
        )
    }

    var lastExternalText by remember { mutableStateOf(text) }

    if (text != lastExternalText) {
        lastExternalText = text
        val prependedText = if (isActive) "\u200B$text" else text
        textFieldValue = TextFieldValue(
            text = prependedText,
            selection = TextRange(prependedText.length)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) EditorActiveLineBg else Color.Transparent)
            .clickable { onLineClick() }
    ) {
        // Line number gutter
        Text(
            text = (index + 1).toString(),
            fontFamily = FontFamily.Monospace,
            fontSize = (fontSize - 1).coerceAtLeast(8f).sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) ActiveLineNumberColor else LineNumberColor,
            textAlign = TextAlign.End,
            modifier = Modifier
                .width(lineNumbersWidth)
                .padding(end = 12.dp, top = 4.dp, bottom = 4.dp)
        )

        // Line Content
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            if (isActive) {
                // Character-by-character styled live inline text field
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (!wordWrap) Modifier.horizontalScroll(sharedHorizontalScrollState) else Modifier
                        )
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { newValue ->
                            if (newValue.text.contains('\n')) {
                                val cleanedText = newValue.text.replace("\u200B", "")
                                val parts = cleanedText.split('\n')
                                val currentPart = parts[0]
                                val remainingPart = parts.getOrElse(1) { "" }
                                lastExternalText = currentPart
                                onTextChange(currentPart)
                                onEnterPressed(remainingPart)
                            } else if (!newValue.text.startsWith("\u200B")) {
                                onBackspaceAtStart()
                            } else {
                                val actualText = newValue.text.substring(1)
                                lastExternalText = actualText
                                textFieldValue = newValue
                                if (actualText != text) {
                                    onTextChange(actualText)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.key == Key.Backspace && keyEvent.type == KeyEventType.KeyDown) {
                                    if (textFieldValue.selection.start <= 1 && textFieldValue.selection.end <= 1) {
                                        onBackspaceAtStart()
                                        true
                                    } else {
                                        false
                                    }
                                } else {
                                    false
                                }
                            }
                            .testTag("line_edit_tf_$index"),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            lineBreak = LineBreak(
                                strategy = LineBreak.Strategy.Simple,
                                strictness = LineBreak.Strictness.Loose,
                                wordBreak = LineBreak.WordBreak.Default
                            )
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        visualTransformation = remember {
                            VisualTransformation { annotated ->
                                val textWithoutZeroWidth = if (annotated.text.startsWith("\u200B")) {
                                    annotated.text.substring(1)
                                } else {
                                    annotated.text
                                }
                                val highlighted = highlightFilterLine(textWithoutZeroWidth)
                                TransformedText(
                                    highlighted,
                                    if (annotated.text.startsWith("\u200B")) {
                                        object : OffsetMapping {
                                            override fun originalToTransformed(offset: Int): Int = (offset - 1).coerceAtLeast(0)
                                            override fun transformedToOriginal(offset: Int): Int = offset + 1
                                        }
                                    } else {
                                        OffsetMapping.Identity
                                    }
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                        keyboardActions = KeyboardActions(onDone = { onLineClick() })
                    )
                }
            } else {
                val highlighted = remember(text) { highlightFilterLine(text) }
                // Overlay search highlights onto the annotated string
                val finalAnnotated = remember(highlighted, matches, activeMatchStartChar) {
                    if (matches.isEmpty()) {
                        highlighted
                    } else {
                        val annotatedBuilder = AnnotatedString.Builder(highlighted)
                        matches.forEach { match ->
                            val isCurrent = activeMatchStartChar != null && match.startChar == activeMatchStartChar
                            annotatedBuilder.addStyle(
                                style = SpanStyle(background = if (isCurrent) ActiveMatchBg else HighlightMatchBg),
                                start = match.startChar,
                                end = match.endChar
                            )
                        }
                        annotatedBuilder.toAnnotatedString()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (!wordWrap) Modifier.horizontalScroll(sharedHorizontalScrollState) else Modifier
                        )
                ) {
                    Text(
                        text = finalAnnotated,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            lineBreak = LineBreak(
                                strategy = LineBreak.Strategy.Simple,
                                strictness = LineBreak.Strictness.Loose,
                                wordBreak = LineBreak.WordBreak.Default
                            )
                        ),
                        softWrap = wordWrap,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSettingsToolbar(
    wordWrap: Boolean,
    onWordWrapToggle: () -> Unit,
    onFindToggle: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    hasUndo: Boolean,
    hasRedo: Boolean,
    activeLineIndex: Int?,
    linesCount: Int,
    onGoToLine: (Int) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onScrollToTop: () -> Unit,
    onScrollToBottom: () -> Unit,
    onRefreshClick: () -> Unit,
    isSyncing: Boolean
) {
    var showGoToDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(LightSurface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onUndoClick,
            enabled = hasUndo,
            modifier = Modifier.testTag("undo_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Undo,
                contentDescription = "Undo",
                tint = if (hasUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }

        IconButton(
            onClick = onRedoClick,
            enabled = hasRedo,
            modifier = Modifier.testTag("redo_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Redo,
                contentDescription = "Redo",
                tint = if (hasRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }

        IconButton(
            onClick = onRefreshClick,
            enabled = !isSyncing,
            modifier = Modifier.testTag("refresh_active_file_button")
        ) {
            if (isSyncing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh File Content",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(LightBorder)
        )

        IconButton(
            onClick = onWordWrapToggle,
            modifier = Modifier.testTag("word_wrap_toggle")
        ) {
            Icon(
                imageVector = if (wordWrap) Icons.Filled.WrapText else Icons.Outlined.WrapText,
                contentDescription = "Word Wrap",
                tint = if (wordWrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }

        IconButton(
            onClick = onFindToggle,
            modifier = Modifier.testTag("find_replace_toggle")
        ) {
            Icon(Icons.Filled.FindInPage, contentDescription = "Find & Replace", tint = MaterialTheme.colorScheme.onSurface)
        }

        IconButton(
            onClick = { showGoToDialog = true },
            modifier = Modifier.testTag("go_to_line_button")
        ) {
            Icon(Icons.Filled.FormatListNumbered, contentDescription = "Go to line", tint = MaterialTheme.colorScheme.onSurface)
        }

        IconButton(
            onClick = onZoomIn,
            modifier = Modifier.testTag("zoom_in_button")
        ) {
            Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom In", tint = MaterialTheme.colorScheme.onSurface)
        }

        IconButton(
            onClick = onZoomOut,
            modifier = Modifier.testTag("zoom_out_button")
        ) {
            Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom Out", tint = MaterialTheme.colorScheme.onSurface)
        }

        IconButton(
            onClick = onScrollToTop,
            modifier = Modifier.testTag("scroll_to_top_button")
        ) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = "Go to Top", tint = MaterialTheme.colorScheme.onSurface)
        }

        IconButton(
            onClick = onScrollToBottom,
            modifier = Modifier.testTag("scroll_to_bottom_button")
        ) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = "Go to Bottom", tint = MaterialTheme.colorScheme.onSurface)
        }

        Spacer(modifier = Modifier.weight(1f))

        // Info tag
        Text(
            text = "Line: ${activeLineIndex?.let { it + 1 } ?: "-"} / $linesCount",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 4.dp)
        )
    }

    if (showGoToDialog) {
        var lineNumberInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGoToDialog = false },
            title = { Text("Go to Line Number", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                OutlinedTextField(
                    value = lineNumberInput,
                    onValueChange = { lineNumberInput = it.filter { char -> char.isDigit() } },
                    label = { Text("Line number (1-$linesCount)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val num = lineNumberInput.toIntOrNull()
                        if (num != null) {
                            onGoToLine(num)
                        }
                        showGoToDialog = false
                    },
                    enabled = lineNumberInput.isNotEmpty()
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoToDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = LightSurface
        )
    }
}

@Composable
fun FindReplacePanel(
    query: String,
    replaceQuery: String,
    matchesCount: Int,
    currentMatchIdx: Int,
    isRegex: Boolean,
    isCase: Boolean,
    onQueryChange: (String) -> Unit,
    onReplaceQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onToggleRegex: () -> Unit,
    onToggleCase: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("find_replace_panel"),
        colors = CardDefaults.cardColors(containerColor = LightSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(1.dp, LightBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Find row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Find pattern...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("find_input_field"),
                    singleLine = true
                )

                IconButton(
                    onClick = onToggleRegex,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = if (isRegex) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                ) {
                    Text(".*", color = if (isRegex) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onToggleCase,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = if (isCase) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                ) {
                    Text("Aa", color = if (isCase) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Find", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Replace row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = replaceQuery,
                    onValueChange = onReplaceQueryChange,
                    placeholder = { Text("Replace with...") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("replace_input_field"),
                    singleLine = true
                )

                Button(
                    onClick = onReplace,
                    enabled = matchesCount > 0 && currentMatchIdx >= 0,
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("Replace")
                }

                Button(
                    onClick = onReplaceAll,
                    enabled = matchesCount > 0,
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("All")
                }
            }

            // Matches counter & Navigation row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (matchesCount > 0) "${currentMatchIdx + 1} of $matchesCount matches" else "No matches",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onPrev, enabled = matchesCount > 0) {
                    Icon(
                        imageVector = Icons.Filled.NavigateBefore,
                        contentDescription = "Previous Match",
                        tint = if (matchesCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }

                IconButton(onClick = onNext, enabled = matchesCount > 0) {
                    Icon(
                        imageVector = Icons.Filled.NavigateNext,
                        contentDescription = "Next Match",
                        tint = if (matchesCount > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(LightBorder)
    )
}
