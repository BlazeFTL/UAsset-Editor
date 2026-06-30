package com.example.ui

import android.Manifest
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.api.GitTreeEntry
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.TabState
import com.example.viewmodel.SearchMatch
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.launch

// Color tokens for our cohesive high-contrast dark theme
val DarkBg = Color(0xFF121417)
val DarkSurface = Color(0xFF1A1D24)
val DarkBorder = Color(0xFF2C313C)
val EditorActiveLineBg = Color(0xFF232834)
val LineNumberColor = Color(0xFF5C6370)
val ActiveLineNumberColor = Color(0xFF61AFEF)
val HighlightMatchBg = Color(0xFF61AFEF).copy(alpha = 0.3f)
val ActiveMatchBg = Color(0xFFE5C07B).copy(alpha = 0.4f)

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
        color = DarkBg
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
    var token by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("uBlockOrigin") }
    var repo by remember { mutableStateOf("uAssets") }
    var branch by remember { mutableStateOf("master") }

    val isSyncing by viewModel.isSyncing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

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
            .background(DarkBg)
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
                    color = Color.White
                )
            )

            Text(
                text = "A high-performance editor with syntax highlighting, differential syncing, and GitHub integration.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // PAT Input
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("GitHub Personal Access Token (PAT)") },
                placeholder = { Text("github_pat_...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("github_token_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = Color.Gray
                )
            )

            // Owner & Repo Configs
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = owner,
                    onValueChange = { owner = it },
                    label = { Text("Repo Owner") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("repo_owner_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = Color.Gray
                    )
                )

                OutlinedTextField(
                    value = repo,
                    onValueChange = { repo = it },
                    label = { Text("Repo Name") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("repo_name_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = Color.Gray
                    )
                )
            }

            OutlinedTextField(
                value = branch,
                onValueChange = { branch = it },
                label = { Text("Branch") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("repo_branch_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = Color.Gray
                )
            )

            Button(
                onClick = { viewModel.saveGitHubCredentials(token, owner, repo, branch) },
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

            TextButton(
                onClick = { viewModel.skipLogin() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("offline_mode_button")
            ) {
                Text("Continue in Offline/Viewer Mode", color = Color.Gray)
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

    val context = LocalContext.current

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DarkSurface,
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
            ) {
                FileBrowserPanel(
                    filesList = filesList,
                    isSyncing = isSyncing,
                    onFileClick = { path ->
                        viewModel.openFileInTab(path)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onSyncClick = { viewModel.syncRepositoryTree() }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                WorkspaceTopBar(
                    activeTabPath = activeTabPath,
                    openTabs = openTabs,
                    isSyncing = isSyncing,
                    onMenuClick = { coroutineScope.launch { drawerState.open() } },
                    onTabSelect = { viewModel.selectTab(it) },
                    onTabClose = { viewModel.closeTab(it) },
                    onCommitClick = { viewModel.commitAndPushActiveTab() },
                    onLogoutClick = { viewModel.logout() }
                )
            },
            containerColor = DarkBg
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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

@Composable
fun EmptyWorkspaceState(onOpenDrawer: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
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
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Open the repository drawer to select and sync files.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
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

@Composable
fun FileBrowserPanel(
    filesList: List<GitTreeEntry>,
    isSyncing: Boolean,
    onFileClick: (String) -> Unit,
    onSyncClick: () -> Unit
) {
    var searchFilter by remember { mutableStateOf("") }
    val filteredFiles = remember(filesList, searchFilter) {
        filesList.filter { it.path.contains(searchFilter, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Repository Files",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
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
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search icon") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = DarkBg,
                unfocusedContainerColor = DarkBg
            )
        )

        Divider(color = DarkBorder)

        if (filteredFiles.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = if (isSyncing) "Syncing repository..." else "No files found. Try syncing.",
                    color = Color.Gray,
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
                itemsIndexed(filteredFiles) { _, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onFileClick(entry.path) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = "File",
                            tint = ActiveLineNumberColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = entry.path,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
    onLogoutClick: () -> Unit
) {
    Surface(
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkBorder),
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
                    Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Color.White)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "uBlock Editor",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

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
                        Icon(Icons.Filled.CloudUpload, contentDescription = "Commit to GitHub", tint = Color(0xFFFFB74D))
                    }
                }

                IconButton(onClick = onLogoutClick, modifier = Modifier.testTag("logout_button")) {
                    Icon(Icons.Filled.ExitToApp, contentDescription = "Logout", tint = Color.LightGray)
                }
            }

            if (openTabs.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = openTabs.indexOfFirst { it.path == activeTabPath }.coerceAtLeast(0),
                    edgePadding = 12.dp,
                    containerColor = DarkSurface,
                    contentColor = Color.White,
                    divider = {}
                ) {
                    openTabs.forEach { tab ->
                        val isSelected = tab.path == activeTabPath
                        Tab(
                            selected = isSelected,
                            onClick = { onTabSelect(tab.path) },
                            modifier = Modifier
                                .testTag("tab_${tab.path.replace("/", "_")}")
                                .background(if (isSelected) DarkBg else DarkSurface)
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
                                            .background(Color(0xFFFFB74D))
                                    )
                                }
                                Text(
                                    text = tab.path.substringAfterLast("/"),
                                    color = if (isSelected) Color.White else Color.Gray,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close tab",
                                    tint = if (isSelected) Color.White else Color.Gray,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onTabClose(tab.path) }
                                )
                            }
                        }
                    }
                }
            }
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

    // Line list state
    val lazyListState = rememberLazyListState()

    // Track scroll state of each tab to restore it
    LaunchedEffect(tabState.path) {
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Find & Replace Panel (Collapsible)
            AnimatedVisibility(
                visible = isSearchOpen,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
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
            }

            // Quick Operations / Shortcuts bar
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
                }
            )

            Divider(color = DarkBorder)

            // Dynamic width computation for line numbers to prevent layout shifting
            val lineNumbersWidth = remember(tabState.lines.size) {
                val digits = tabState.lines.size.toString().length
                (digits * 9 + 20).coerceAtLeast(36).dp
            }

            // Lazy high-performance scroll rendering
            LazyColumn(
                state = lazyListState,
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
                        lineNumbersWidth = lineNumbersWidth,
                        matches = searchMatches.filter { it.lineIndex == index },
                        activeMatchStartChar = if (currentMatchIndex >= 0 && searchMatches[currentMatchIndex].lineIndex == index) searchMatches[currentMatchIndex].startChar else null,
                        onLineClick = { viewModel.selectLine(index) },
                        onTextChange = { viewModel.updateLine(index, it) }
                    )
                }
            }

            // Keyboard/Auxiliary Shortcut Action Bar at the bottom
            if (tabState.activeLineIndex != null) {
                KeyboardShortcutBar(
                    onDuplicate = { viewModel.duplicateActiveLine() },
                    onDelete = { viewModel.deleteActiveLine() },
                    onMoveUp = {
                        viewModel.moveActiveLineUp()
                        coroutineScope.launch {
                            val active = tabState.activeLineIndex ?: 0
                            if (active > 0) lazyListState.scrollToItem(active - 1)
                        }
                    },
                    onMoveDown = {
                        viewModel.moveActiveLineDown()
                        coroutineScope.launch {
                            val active = tabState.activeLineIndex ?: 0
                            if (active < tabState.lines.size - 1) lazyListState.scrollToItem(active + 1)
                        }
                    },
                    onInsertAbove = { viewModel.insertLineAbove() },
                    onInsertBelow = { viewModel.insertLineBelow() },
                    onDeselect = { viewModel.selectLine(null); focusManager.clearFocus() }
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
    lineNumbersWidth: androidx.compose.ui.unit.Dp,
    matches: List<SearchMatch>,
    activeMatchStartChar: Int?,
    onLineClick: () -> Unit,
    onTextChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isActive) {
        if (isActive) {
            focusRequester.requestFocus()
        }
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
            fontSize = 12.sp,
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
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .testTag("line_edit_tf_$index"),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Color.White
                    ),
                    cursorBrush = SolidColor(Color.White),
                    visualTransformation = remember {
                        VisualTransformation { annotated ->
                            TransformedText(
                                highlightFilterLine(annotated.text),
                                OffsetMapping.Identity
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onLineClick() })
                )
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

                val horizontalScrollState = rememberScrollState()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (!wordWrap) Modifier.horizontalScroll(horizontalScrollState) else Modifier
                        )
                ) {
                    Text(
                        text = finalAnnotated,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
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
    onGoToLine: (Int) -> Unit
) {
    var showGoToDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onUndoClick,
            enabled = hasUndo,
            modifier = Modifier.testTag("undo_button")
        ) {
            Icon(Icons.Filled.Undo, contentDescription = "Undo", tint = if (hasUndo) Color.White else Color.DarkGray)
        }

        IconButton(
            onClick = onRedoClick,
            enabled = hasRedo,
            modifier = Modifier.testTag("redo_button")
        ) {
            Icon(Icons.Filled.Redo, contentDescription = "Redo", tint = if (hasRedo) Color.White else Color.DarkGray)
        }

        VerticalDivider()

        IconButton(
            onClick = onWordWrapToggle,
            modifier = Modifier.testTag("word_wrap_toggle")
        ) {
            Icon(
                imageVector = if (wordWrap) Icons.Filled.WrapText else Icons.Outlined.WrapText,
                contentDescription = "Word Wrap",
                tint = if (wordWrap) MaterialTheme.colorScheme.primary else Color.White
            )
        }

        IconButton(
            onClick = onFindToggle,
            modifier = Modifier.testTag("find_replace_toggle")
        ) {
            Icon(Icons.Filled.FindInPage, contentDescription = "Find & Replace", tint = Color.White)
        }

        IconButton(
            onClick = { showGoToDialog = true },
            modifier = Modifier.testTag("go_to_line_button")
        ) {
            Icon(Icons.Filled.FormatListNumbered, contentDescription = "Go to line", tint = Color.White)
        }

        Spacer(modifier = Modifier.weight(1f))

        // Info tag
        Text(
            text = "Line: ${activeLineIndex?.let { it + 1 } ?: "-"} / $linesCount",
            fontSize = 11.sp,
            color = Color.LightGray,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(end = 4.dp)
        )
    }

    if (showGoToDialog) {
        var lineNumberInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGoToDialog = false },
            title = { Text("Go to Line Number", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = lineNumberInput,
                    onValueChange = { lineNumberInput = it.filter { char -> char.isDigit() } },
                    label = { Text("Line number (1-$linesCount)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
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
            containerColor = DarkSurface
        )
    }
}

@Composable
fun KeyboardShortcutBar(
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onInsertAbove: () -> Unit,
    onInsertBelow: () -> Unit,
    onDeselect: () -> Unit
) {
    Surface(
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = onInsertAbove,
                label = { Text("Insert Above", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(14.dp)) },
                colors = AssistChipDefaults.assistChipColors(labelColor = Color.White, leadingIconContentColor = Color.White)
            )

            AssistChip(
                onClick = onInsertBelow,
                label = { Text("Insert Below", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(14.dp)) },
                colors = AssistChipDefaults.assistChipColors(labelColor = Color.White, leadingIconContentColor = Color.White)
            )

            AssistChip(
                onClick = onDuplicate,
                label = { Text("Duplicate", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp)) },
                colors = AssistChipDefaults.assistChipColors(labelColor = Color.White, leadingIconContentColor = Color.White)
            )

            AssistChip(
                onClick = onDelete,
                label = { Text("Delete Line", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp)) },
                colors = AssistChipDefaults.assistChipColors(labelColor = Color.White, leadingIconContentColor = Color.White)
            )

            AssistChip(
                onClick = onMoveUp,
                label = { Text("Move Up", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Filled.ArrowUpward, contentDescription = null, modifier = Modifier.size(14.dp)) },
                colors = AssistChipDefaults.assistChipColors(labelColor = Color.White, leadingIconContentColor = Color.White)
            )

            AssistChip(
                onClick = onMoveDown,
                label = { Text("Move Down", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp)) },
                colors = AssistChipDefaults.assistChipColors(labelColor = Color.White, leadingIconContentColor = Color.White)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onDeselect,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("done_editing_button")
            ) {
                Text("Done", fontSize = 11.sp)
            }
        }
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
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(1.dp, DarkBorder)
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
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                IconButton(
                    onClick = onToggleRegex,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = if (isRegex) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                ) {
                    Text(".*", color = Color.White, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onToggleCase,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = if (isCase) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                ) {
                    Text("Aa", color = Color.White, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Find", tint = Color.White)
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
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
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
                    color = Color.LightGray,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onPrev, enabled = matchesCount > 0) {
                    Icon(Icons.Filled.NavigateBefore, contentDescription = "Previous Match", tint = if (matchesCount > 0) Color.White else Color.DarkGray)
                }

                IconButton(onClick = onNext, enabled = matchesCount > 0) {
                    Icon(Icons.Filled.NavigateNext, contentDescription = "Next Match", tint = if (matchesCount > 0) Color.White else Color.DarkGray)
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
            .background(DarkBorder)
    )
}
