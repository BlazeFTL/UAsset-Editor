package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.CommitRequest
import com.example.api.GitHubService
import com.example.api.GitTreeEntry
import com.example.api.UserResponse
import com.example.data.AppDatabase
import com.example.data.FilterFile
import com.example.data.FilterFileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TabState(
    val path: String,
    val lines: List<String>,
    val originalSha: String,
    val isModified: Boolean = false,
    val undoStack: List<List<String>> = emptyList(),
    val redoStack: List<List<String>> = emptyList(),
    val activeLineIndex: Int? = null,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0
)

data class SearchMatch(
    val lineIndex: Int,
    val startChar: Int,
    val endChar: Int
)

enum class FileSortOrder {
    ALPHABETICAL,
    PINNED_FIRST,
    MODIFIED_FIRST
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("filter_editor_prefs", Context.MODE_PRIVATE)
    private val repository = FilterFileRepository(AppDatabase.getDatabase(application).filterFileDao())
    private val gitHubService = GitHubService.create()

    // Screen navigation state
    // "SPLASH", "LOGIN", "WORKSPACE"
    private val _currentScreen = MutableStateFlow("SPLASH")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    // Storage permission handled flag
    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    // Auth state
    private val _githubToken = MutableStateFlow<String?>(null)
    val githubToken: StateFlow<String?> = _githubToken.asStateFlow()

    private val _authenticatedUser = MutableStateFlow<UserResponse?>(null)
    val authenticatedUser: StateFlow<UserResponse?> = _authenticatedUser.asStateFlow()

    // Repository configurations (Default to uBlockOrigin/uAssets)
    private val _repoOwner = MutableStateFlow("uBlockOrigin")
    val repoOwner: StateFlow<String> = _repoOwner.asStateFlow()

    private val _repoName = MutableStateFlow("uAssets")
    val repoName: StateFlow<String> = _repoName.asStateFlow()

    private val _branchName = MutableStateFlow("master")
    val branchName: StateFlow<String> = _branchName.asStateFlow()

    // Syncing state
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // File tree state
    private val _allRepoFiles = MutableStateFlow<List<GitTreeEntry>>(emptyList())
    val allRepoFiles: StateFlow<List<GitTreeEntry>> = _allRepoFiles.asStateFlow()

    // Database flow connection
    private val _localFiles = MutableStateFlow<List<FilterFile>>(emptyList())
    val localFiles: StateFlow<List<FilterFile>> = _localFiles.asStateFlow()

    // Active Workspace state
    private val _openTabs = MutableStateFlow<List<TabState>>(emptyList())
    val openTabs: StateFlow<List<TabState>> = _openTabs.asStateFlow()

    private val _activeTabPath = MutableStateFlow<String?>(null)
    val activeTabPath: StateFlow<String?> = _activeTabPath.asStateFlow()

    // Search and Replace state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _replaceQuery = MutableStateFlow("")
    val replaceQuery: StateFlow<String> = _replaceQuery.asStateFlow()

    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()

    private val _isRegexSearch = MutableStateFlow(false)
    val isRegexSearch: StateFlow<Boolean> = _isRegexSearch.asStateFlow()

    private val _isCaseSensitiveSearch = MutableStateFlow(false)
    val isCaseSensitiveSearch: StateFlow<Boolean> = _isCaseSensitiveSearch.asStateFlow()

    private val _searchMatches = MutableStateFlow<List<SearchMatch>>(emptyList())
    val searchMatches: StateFlow<List<SearchMatch>> = _searchMatches.asStateFlow()

    private val _currentMatchIndex = MutableStateFlow(-1)
    val currentMatchIndex: StateFlow<Int> = _currentMatchIndex.asStateFlow()

    // Editor settings
    private val _wordWrap = MutableStateFlow(true)
    val wordWrap: StateFlow<Boolean> = _wordWrap.asStateFlow()

    private val _fontSize = MutableStateFlow(13f)
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    fun setFontSize(size: Float) {
        _fontSize.value = size.coerceIn(8f, 30f)
    }

    private val _autoIndent = MutableStateFlow(false) // Filter lists shouldn't auto-indent
    val autoIndent: StateFlow<Boolean> = _autoIndent.asStateFlow()

    // Sorting and Pinning
    private val _pinnedFiles = MutableStateFlow<Set<String>>(emptySet())
    val pinnedFiles: StateFlow<Set<String>> = _pinnedFiles.asStateFlow()

    private val _fileSortOrder = MutableStateFlow(FileSortOrder.PINNED_FIRST)
    val fileSortOrder: StateFlow<FileSortOrder> = _fileSortOrder.asStateFlow()

    val modifiedFiles: StateFlow<Set<String>> = repository.allFiles
        .map { files -> files.filter { it.isModified }.map { it.path }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun togglePinFile(path: String) {
        val current = _pinnedFiles.value.toMutableSet()
        if (current.contains(path)) {
            current.remove(path)
        } else {
            current.add(path)
        }
        _pinnedFiles.value = current
        sharedPrefs.edit().putStringSet("pinned_files", current).apply()
    }

    fun setFileSortOrder(order: FileSortOrder) {
        _fileSortOrder.value = order
        sharedPrefs.edit().putString("file_sort_order", order.name).apply()
    }

    // GitHub Device flow properties
    private val _deviceUserCode = MutableStateFlow<String?>(null)
    val deviceUserCode: StateFlow<String?> = _deviceUserCode.asStateFlow()

    private val _deviceVerificationUri = MutableStateFlow<String?>(null)
    val deviceVerificationUri: StateFlow<String?> = _deviceVerificationUri.asStateFlow()

    private val _deviceAuthPolling = MutableStateFlow(false)
    val deviceAuthPolling: StateFlow<Boolean> = _deviceAuthPolling.asStateFlow()

    private val _deviceAuthError = MutableStateFlow<String?>(null)
    val deviceAuthError: StateFlow<String?> = _deviceAuthError.asStateFlow()

    private var deviceFlowJob: kotlinx.coroutines.Job? = null

    init {
        // Load saved pinned files
        val savedPinned = sharedPrefs.getStringSet("pinned_files", emptySet()) ?: emptySet()
        _pinnedFiles.value = savedPinned

        // Load saved sort order
        val savedSort = sharedPrefs.getString("file_sort_order", FileSortOrder.PINNED_FIRST.name)
        _fileSortOrder.value = try {
            FileSortOrder.valueOf(savedSort ?: FileSortOrder.PINNED_FIRST.name)
        } catch (e: Exception) {
            FileSortOrder.PINNED_FIRST
        }

        // Load token and repository configurations from shared preferences
        val savedToken = sharedPrefs.getString("github_token", null)
        val savedOwner = sharedPrefs.getString("repo_owner", "uBlockOrigin") ?: "uBlockOrigin"
        val savedName = sharedPrefs.getString("repo_name", "uAssets") ?: "uAssets"
        val savedBranch = sharedPrefs.getString("repo_branch", "master") ?: "master"

        _githubToken.value = savedToken
        _repoOwner.value = savedOwner
        _repoName.value = savedName
        _branchName.value = savedBranch

        // Check if user is authenticated if token exists
        savedToken?.let { token ->
            testTokenAndFetchUser(token)
        }

        // Connect database to state
        viewModelScope.launch {
            repository.allFiles.collect { files ->
                _localFiles.value = files
                // Sync tabs with database state (if a file was modified elsewhere or loaded)
                updateTabsFromDatabase(files)
            }
        }

        // Determine initial screen
        val hasLoggedInBefore = sharedPrefs.getBoolean("logged_in_before", false)
        if (hasLoggedInBefore) {
            _currentScreen.value = "WORKSPACE"
        } else {
            _currentScreen.value = "LOGIN"
        }
    }

    // --- Authentication & Initialization ---

    fun skipLogin() {
        sharedPrefs.edit().putBoolean("logged_in_before", true).apply()
        _currentScreen.value = "WORKSPACE"
    }

    fun setStoragePermissionGranted(granted: Boolean) {
        _hasStoragePermission.value = granted
    }

    fun saveGitHubCredentials(token: String, owner: String, repo: String, branch: String) {
        _isSyncing.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val formattedHeader = if (token.startsWith("token ") || token.startsWith("Bearer ")) token else "token $token"
                // Test authentication
                val user = withContext(Dispatchers.IO) {
                    gitHubService.getAuthenticatedUser(formattedHeader)
                }

                sharedPrefs.edit().apply {
                    putString("github_token", token)
                    putString("repo_owner", owner)
                    putString("repo_name", repo)
                    putString("repo_branch", branch)
                    putBoolean("logged_in_before", true)
                }.apply()

                _githubToken.value = token
                _repoOwner.value = owner
                _repoName.value = repo
                _branchName.value = branch
                _authenticatedUser.value = user
                _successMessage.value = "Welcome, ${user.login}! Authenticated successfully."
                _currentScreen.value = "WORKSPACE"

                // Start directory tree synchronization automatically
                syncRepositoryTree()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Authentication failed", e)
                _errorMessage.value = "Authentication failed: ${e.localizedMessage}. Please verify your Personal Access Token."
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun testTokenAndFetchUser(token: String) {
        viewModelScope.launch {
            try {
                val formattedHeader = if (token.startsWith("token ") || token.startsWith("Bearer ")) token else "token $token"
                val user = withContext(Dispatchers.IO) {
                    gitHubService.getAuthenticatedUser(formattedHeader)
                }
                _authenticatedUser.value = user
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to retrieve user details", e)
            }
        }
    }

    fun logout() {
        sharedPrefs.edit().apply {
            remove("github_token")
            putBoolean("logged_in_before", false)
        }.apply()
        _githubToken.value = null
        _authenticatedUser.value = null
        _currentScreen.value = "LOGIN"
        _openTabs.value = emptyList()
        _activeTabPath.value = null
    }

    fun startDeviceFlowAuth(clientId: String = "Ov23ct4F0VqT2U9Ld8p2") {
        deviceFlowJob?.cancel()
        _deviceAuthPolling.value = true
        _deviceAuthError.value = null
        _deviceUserCode.value = null
        _deviceVerificationUri.value = null

        deviceFlowJob = viewModelScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    gitHubService.getDeviceCode(clientId = clientId)
                }
                
                _deviceUserCode.value = response.userCode
                _deviceVerificationUri.value = response.verificationUri
                
                val intervalMs = (response.interval * 1000L).coerceAtLeast(1000L)
                val expiresAt = System.currentTimeMillis() + (response.expiresIn * 1000L)
                
                // Start polling
                while (System.currentTimeMillis() < expiresAt) {
                    kotlinx.coroutines.delay(intervalMs)
                    try {
                        val tokenResponse = withContext(Dispatchers.IO) {
                            gitHubService.getAccessToken(clientId = clientId, deviceCode = response.deviceCode)
                        }
                        
                        if (tokenResponse.accessToken != null) {
                            // Succeeded! Save credentials
                            saveGitHubCredentials(
                                token = tokenResponse.accessToken,
                                owner = _repoOwner.value,
                                repo = _repoName.value,
                                branch = _branchName.value
                            )
                            break
                        } else if (tokenResponse.error != "authorization_pending") {
                            _deviceAuthError.value = tokenResponse.errorDescription ?: "Auth failed: ${tokenResponse.error}"
                            break
                        }
                    } catch (e: Exception) {
                        // ignore network transient errors during polling, or if cancelled
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Device flow failed", e)
                _deviceAuthError.value = "Failed to start login: ${e.localizedMessage}"
            } finally {
                _deviceAuthPolling.value = false
            }
        }
    }

    fun cancelDeviceFlowAuth() {
        deviceFlowJob?.cancel()
        deviceFlowJob = null
        _deviceAuthPolling.value = false
        _deviceUserCode.value = null
        _deviceVerificationUri.value = null
    }

    fun updateRepositorySelection(owner: String, repo: String, branch: String) {
        _repoOwner.value = owner
        _repoName.value = repo
        _branchName.value = branch
        
        sharedPrefs.edit().apply {
            putString("repo_owner", owner)
            putString("repo_name", repo)
            putString("repo_branch", branch)
        }.apply()
        
        // Sync new repository files
        syncRepositoryTree()
    }

    fun clearMessage() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    // --- Settings Toggles ---

    fun toggleWordWrap() {
        _wordWrap.value = !_wordWrap.value
    }

    fun toggleAutoIndent() {
        _autoIndent.value = !_autoIndent.value
    }

    // --- Caching & Smart Synchronization (Differential Updates) ---

    fun syncRepositoryTree() {
        val owner = _repoOwner.value
        val repo = _repoName.value
        var branch = _branchName.value
        val token = _githubToken.value

        _isSyncing.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val authHeader = token?.let { if (it.startsWith("token ")) it else "token $it" }

                if (branch.isEmpty()) {
                    try {
                        val repoInfo = withContext(Dispatchers.IO) {
                            gitHubService.getRepositoryInfo(authHeader, owner, repo)
                        }
                        branch = repoInfo.defaultBranch
                        _branchName.value = branch
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Failed to resolve default branch, falling back to master", e)
                        branch = "master"
                    }
                }

                val response = try {
                    withContext(Dispatchers.IO) {
                        gitHubService.getGitTree(authHeader, owner, repo, branch, recursive = 1)
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Fetch git tree failed for branch $branch. Trying to auto-resolve default branch...", e)
                    val repoInfo = withContext(Dispatchers.IO) {
                        gitHubService.getRepositoryInfo(authHeader, owner, repo)
                    }
                    branch = repoInfo.defaultBranch
                    _branchName.value = branch
                    withContext(Dispatchers.IO) {
                        gitHubService.getGitTree(authHeader, owner, repo, branch, recursive = 1)
                    }
                }

                // Filter files of interest dynamically based on user requirements
                val filteredFiles = response.tree.filter { entry ->
                    if (entry.type != "blob") return@filter false
                    val path = entry.path
                    val isUblock = owner.equals("uBlockOrigin", ignoreCase = true) && repo.equals("uAssets", ignoreCase = true)
                    
                    if (isUblock) {
                        // "Also It Should Be Only The Contents Of filter Folder Only" -> filters/
                        path.startsWith("filters/", ignoreCase = true)
                    } else {
                        // "I Wish To Add https://github.com/BlazeFTL/My-Filters (only .txt and .html .yaml files"
                        path.endsWith(".txt", ignoreCase = true) ||
                        path.endsWith(".html", ignoreCase = true) ||
                        path.endsWith(".yaml", ignoreCase = true) ||
                        path.endsWith(".yml", ignoreCase = true)
                    }
                }

                _allRepoFiles.value = filteredFiles

                // DIFFERENTIAL UPDATE CHECK
                // Match with current local files
                val localMap = _localFiles.value.associateBy { it.path }
                
                withContext(Dispatchers.IO) {
                    for (gitEntry in filteredFiles) {
                        val localFile = localMap[gitEntry.path]
                        if (localFile == null) {
                            // New file on remote, create entry in SQLite
                            val placeholder = FilterFile(
                                path = gitEntry.path,
                                content = "", // empty, loaded on-demand
                                sha = gitEntry.sha,
                                lastSynced = System.currentTimeMillis()
                            )
                            repository.insertFile(placeholder)
                        } else if (localFile.sha != gitEntry.sha) {
                            // Content changed on remote, if the user didn't modify it locally,
                            // we update the SHA and clear content so it's downloaded on demand.
                            // If user DID modify it, we keep localContent safe but update SHA to indicate a potential merge conflict.
                            if (!localFile.isModified) {
                                val updatedPlaceholder = localFile.copy(
                                    sha = gitEntry.sha,
                                    content = "", // clear content so we re-fetch the new raw on opening
                                    lastSynced = System.currentTimeMillis()
                                )
                                repository.insertFile(updatedPlaceholder)
                            } else {
                                val updatedPlaceholder = localFile.copy(
                                    sha = gitEntry.sha, // updated base
                                    lastSynced = System.currentTimeMillis()
                                    // keep localContent intact
                                )
                                repository.insertFile(updatedPlaceholder)
                            }
                        }
                    }

                    // Delete files from local database that are no longer in remote tree
                    val gitPaths = filteredFiles.map { it.path }.toSet()
                    for (localFile in _localFiles.value) {
                        if (!gitPaths.contains(localFile.path)) {
                            repository.deleteFileByPath(localFile.path)
                        }
                    }
                }

                _successMessage.value = "File tree synchronized successfully! (${filteredFiles.size} files ready)"
            } catch (e: Exception) {
                Log.e("MainViewModel", "Differential Sync failed", e)
                _errorMessage.value = "Sync failed: ${e.localizedMessage}. Using offline cache."
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private fun updateTabsFromDatabase(dbFiles: List<FilterFile>) {
        val dbMap = dbFiles.associateBy { it.path }
        val currentTabs = _openTabs.value
        val updatedTabs = currentTabs.map { tab ->
            val dbFile = dbMap[tab.path]
            if (dbFile != null) {
                // If modified flag changes, or if database has actual content and tab content was empty
                val activeContent = dbFile.localContent ?: dbFile.content
                val tabLinesJoined = tab.lines.joinToString("\n")
                if (activeContent != tabLinesJoined) {
                    val newLines = activeContent.split("\n")
                    tab.copy(
                        lines = newLines,
                        originalSha = dbFile.sha,
                        isModified = dbFile.isModified
                    )
                } else {
                    tab.copy(
                        originalSha = dbFile.sha,
                        isModified = dbFile.isModified
                    )
                }
            } else {
                tab
            }
        }
        if (updatedTabs != currentTabs) {
            _openTabs.value = updatedTabs
        }
    }

    // --- Tab & Workspace Management ---

    fun openFileInTab(path: String) {
        val currentTabs = _openTabs.value
        val existingTab = currentTabs.find { it.path == path }

        if (existingTab != null) {
            _activeTabPath.value = path
            clearSearch()
            return
        }

        _isSyncing.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                // Load from local Room database cache
                var cachedFile = repository.getFileByPath(path)
                var fileContent = ""
                var fileSha = ""

                if (cachedFile == null || cachedFile.content.isEmpty()) {
                    // Fetch on-demand from remote
                    val owner = _repoOwner.value
                    val repo = _repoName.value
                    val branch = _branchName.value
                    val token = _githubToken.value
                    val authHeader = token?.let { if (it.startsWith("token ")) it else "token $it" }

                    val contentResponse = withContext(Dispatchers.IO) {
                        gitHubService.getFileContent(authHeader, owner, repo, path, branch)
                    }

                    // Decode base64 content
                    val base64Content = contentResponse.content?.replace("\n", "")?.replace("\r", "") ?: ""
                    val decodedBytes = Base64.decode(base64Content, Base64.DEFAULT)
                    fileContent = String(decodedBytes, Charsets.UTF_8)
                    fileSha = contentResponse.sha

                    // Save to Room cache
                    val updatedFile = FilterFile(
                        path = path,
                        content = fileContent,
                        sha = fileSha,
                        lastSynced = System.currentTimeMillis(),
                        isModified = false,
                        localContent = null
                    )
                    repository.insertFile(updatedFile)
                } else {
                    fileContent = cachedFile.localContent ?: cachedFile.content
                    fileSha = cachedFile.sha
                }

                // Split into lines and auto-format comment spacing
                val rawLines = fileContent.split("\n")
                val lines = rawLines.map { line ->
                    if (line.startsWith("!") && !line.startsWith("! ") && !line.startsWith("!#")) {
                        "! " + line.substring(1)
                    } else {
                        line
                    }
                }

                val isTxtFile = path.endsWith(".txt", ignoreCase = true)
                val lastLineIndex = (lines.size - 1).coerceAtLeast(0)

                val newTab = TabState(
                    path = path,
                    lines = lines,
                    originalSha = fileSha,
                    isModified = cachedFile?.isModified ?: false,
                    activeLineIndex = if (isTxtFile) lastLineIndex else null,
                    scrollIndex = if (isTxtFile) lastLineIndex else 0
                )

                _openTabs.value = currentTabs + newTab
                _activeTabPath.value = path
                clearSearch()
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to open file", e)
                _errorMessage.value = "Failed to load file content: ${e.localizedMessage}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun closeTab(path: String) {
        val currentTabs = _openTabs.value
        val updatedTabs = currentTabs.filter { it.path != path }
        _openTabs.value = updatedTabs

        if (_activeTabPath.value == path) {
            _activeTabPath.value = if (updatedTabs.isNotEmpty()) updatedTabs.last().path else null
            clearSearch()
        }
    }

    fun selectTab(path: String) {
        _activeTabPath.value = path
        clearSearch()
    }

    // --- Line-by-Line Editing Operations (High Performance) ---

    private fun updateActiveTab(lines: List<String>, updateUndo: Boolean = true, activeLine: Int? = null) {
        val activePath = _activeTabPath.value ?: return
        val currentTabs = _openTabs.value
        val updatedTabs = currentTabs.map { tab ->
            if (tab.path == activePath) {
                var undo = tab.undoStack
                if (updateUndo) {
                    undo = (tab.undoStack + listOf(tab.lines)).takeLast(50) // limit stack size
                }
                tab.copy(
                    lines = lines,
                    undoStack = undo,
                    redoStack = if (updateUndo) emptyList() else tab.redoStack,
                    isModified = true,
                    activeLineIndex = activeLine ?: tab.activeLineIndex
                )
            } else {
                tab
            }
        }
        _openTabs.value = updatedTabs

        // Save local modifications to Room Database cache
        viewModelScope.launch(Dispatchers.IO) {
            val file = repository.getFileByPath(activePath)
            if (file != null) {
                val updatedFile = file.copy(
                    isModified = true,
                    localContent = lines.joinToString("\n")
                )
                repository.insertFile(updatedFile)
            }
        }
    }

    fun selectLine(index: Int?) {
        val activePath = _activeTabPath.value ?: return
        _openTabs.value = _openTabs.value.map { tab ->
            if (tab.path == activePath) {
                tab.copy(activeLineIndex = index)
            } else {
                tab
            }
        }
    }

    fun updateLine(index: Int, newText: String) {
        val tab = getActiveTabState() ?: return
        if (index < 0 || index >= tab.lines.size) return

        val processedText = if (newText.startsWith("!") && !newText.startsWith("! ") && !newText.startsWith("!#")) {
            "! " + newText.substring(1)
        } else {
            newText
        }

        if (tab.lines[index] == processedText) return

        val mutableLines = tab.lines.toMutableList()
        mutableLines[index] = processedText

        // On typing, we edit the line text. To prevent spamming undo stack for every single character,
        // we only update undo stack when character count changes significantly or when enter/space is pressed,
        // but simple line-level typing updates without pushing undo for every keystroke can be handled by
        // checking the previous stack value.
        val lastUndoLines = tab.undoStack.lastOrNull()
        val lineDiffersCount = lastUndoLines?.let { last ->
            last.size != mutableLines.size || last[index].length - processedText.length > 5 || last[index].length - processedText.length < -5
        } ?: true

        updateActiveTab(mutableLines, updateUndo = lineDiffersCount, activeLine = index)
    }

    fun insertLineAbove() {
        val tab = getActiveTabState() ?: return
        val idx = tab.activeLineIndex ?: 0
        val mutableLines = tab.lines.toMutableList()
        mutableLines.add(idx, "")
        updateActiveTab(mutableLines, updateUndo = true, activeLine = idx)
    }

    fun insertLineBelow() {
        val tab = getActiveTabState() ?: return
        val idx = tab.activeLineIndex ?: (tab.lines.size - 1)
        val mutableLines = tab.lines.toMutableList()
        val insertIdx = if (mutableLines.isEmpty()) 0 else idx + 1
        mutableLines.add(insertIdx, "")
        updateActiveTab(mutableLines, updateUndo = true, activeLine = insertIdx)
    }

    fun insertLineBelowWithText(index: Int, lineText: String) {
        val tab = getActiveTabState() ?: return
        val mutableLines = tab.lines.toMutableList()
        val insertIdx = index + 1
        if (insertIdx <= mutableLines.size) {
            mutableLines.add(insertIdx, lineText)
            updateActiveTab(mutableLines, updateUndo = true, activeLine = insertIdx)
        }
    }

    fun mergeLineWithPrevious(index: Int) {
        if (index <= 0) return
        val tab = getActiveTabState() ?: return
        if (index >= tab.lines.size) return
        
        val mutableLines = tab.lines.toMutableList()
        val currentLineText = mutableLines[index]
        val prevLineText = mutableLines[index - 1]
        
        // Merge current line into previous line
        mutableLines[index - 1] = prevLineText + currentLineText
        mutableLines.removeAt(index)
        
        updateActiveTab(mutableLines, updateUndo = true, activeLine = index - 1)
    }

    fun deleteActiveLine() {
        val tab = getActiveTabState() ?: return
        val idx = tab.activeLineIndex ?: return
        if (idx < 0 || idx >= tab.lines.size) return

        val mutableLines = tab.lines.toMutableList()
        mutableLines.removeAt(idx)
        val newActiveIdx = if (mutableLines.isEmpty()) null else if (idx >= mutableLines.size) mutableLines.size - 1 else idx
        updateActiveTab(mutableLines, updateUndo = true, activeLine = newActiveIdx)
    }

    fun duplicateActiveLine() {
        val tab = getActiveTabState() ?: return
        val idx = tab.activeLineIndex ?: return
        if (idx < 0 || idx >= tab.lines.size) return

        val mutableLines = tab.lines.toMutableList()
        val lineToDuplicate = mutableLines[idx]
        mutableLines.add(idx + 1, lineToDuplicate)
        updateActiveTab(mutableLines, updateUndo = true, activeLine = idx + 1)
    }

    fun moveActiveLineUp() {
        val tab = getActiveTabState() ?: return
        val idx = tab.activeLineIndex ?: return
        if (idx <= 0 || idx >= tab.lines.size) return

        val mutableLines = tab.lines.toMutableList()
        val temp = mutableLines[idx]
        mutableLines[idx] = mutableLines[idx - 1]
        mutableLines[idx - 1] = temp
        updateActiveTab(mutableLines, updateUndo = true, activeLine = idx - 1)
    }

    fun moveActiveLineDown() {
        val tab = getActiveTabState() ?: return
        val idx = tab.activeLineIndex ?: return
        if (idx < 0 || idx >= tab.lines.size - 1) return

        val mutableLines = tab.lines.toMutableList()
        val temp = mutableLines[idx]
        mutableLines[idx] = mutableLines[idx + 1]
        mutableLines[idx + 1] = temp
        updateActiveTab(mutableLines, updateUndo = true, activeLine = idx + 1)
    }

    // --- Undo & Redo System ---

    fun undo() {
        val activePath = _activeTabPath.value ?: return
        val currentTabs = _openTabs.value
        val tab = currentTabs.find { it.path == activePath } ?: return
        if (tab.undoStack.isEmpty()) return

        val previousState = tab.undoStack.last()
        val newUndoStack = tab.undoStack.subList(0, tab.undoStack.size - 1)
        val newRedoStack = tab.redoStack + listOf(tab.lines)

        val updatedTabs = currentTabs.map {
            if (it.path == activePath) {
                it.copy(
                    lines = previousState,
                    undoStack = newUndoStack,
                    redoStack = newRedoStack,
                    isModified = true
                )
            } else {
                it
            }
        }
        _openTabs.value = updatedTabs

        // Save local modifications to Room
        viewModelScope.launch(Dispatchers.IO) {
            val file = repository.getFileByPath(activePath)
            if (file != null) {
                val updatedFile = file.copy(
                    localContent = previousState.joinToString("\n"),
                    isModified = true
                )
                repository.insertFile(updatedFile)
            }
        }
    }

    fun redo() {
        val activePath = _activeTabPath.value ?: return
        val currentTabs = _openTabs.value
        val tab = currentTabs.find { it.path == activePath } ?: return
        if (tab.redoStack.isEmpty()) return

        val nextState = tab.redoStack.last()
        val newRedoStack = tab.redoStack.subList(0, tab.redoStack.size - 1)
        val newUndoStack = tab.undoStack + listOf(tab.lines)

        val updatedTabs = currentTabs.map {
            if (it.path == activePath) {
                it.copy(
                    lines = nextState,
                    undoStack = newUndoStack,
                    redoStack = newRedoStack,
                    isModified = true
                )
            } else {
                it
            }
        }
        _openTabs.value = updatedTabs

        // Save local modifications to Room
        viewModelScope.launch(Dispatchers.IO) {
            val file = repository.getFileByPath(activePath)
            if (file != null) {
                val updatedFile = file.copy(
                    localContent = nextState.joinToString("\n"),
                    isModified = true
                )
                repository.insertFile(updatedFile)
            }
        }
    }

    // --- Commit Back to GitHub ---

    fun commitAndPushActiveTab() {
        val tab = getActiveTabState() ?: return
        val token = _githubToken.value
        if (token.isNullOrEmpty()) {
            _errorMessage.value = "GitHub Personal Access Token (PAT) is required to push commits."
            return
        }

        _isSyncing.value = true
        _errorMessage.value = null
        _successMessage.value = null

        val owner = _repoOwner.value
        val repo = _repoName.value
        val branch = _branchName.value
        val fullContent = tab.lines.joinToString("\n")

        viewModelScope.launch {
            try {
                val base64Content = Base64.encodeToString(fullContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                val commitRequest = CommitRequest(
                    message = "Update filters: edit in Filter Editor via PAT authorization",
                    content = base64Content,
                    sha = tab.originalSha,
                    branch = branch
                )

                val authHeader = if (token.startsWith("token ") || token.startsWith("Bearer ")) token else "token $token"
                val response = withContext(Dispatchers.IO) {
                    gitHubService.commitFileChange(authHeader, owner, repo, tab.path, commitRequest)
                }

                val newSha = response.content?.sha ?: tab.originalSha

                // Update Room Database to reflect successful commit
                val dbFile = repository.getFileByPath(tab.path)
                if (dbFile != null) {
                    val syncedFile = dbFile.copy(
                        content = fullContent,
                        sha = newSha,
                        isModified = false,
                        localContent = null,
                        lastSynced = System.currentTimeMillis()
                    )
                    repository.insertFile(syncedFile)
                }

                // Update Local Tab state
                val updatedTabs = _openTabs.value.map {
                    if (it.path == tab.path) {
                        it.copy(
                            originalSha = newSha,
                            isModified = false
                        )
                    } else {
                        it
                    }
                }
                _openTabs.value = updatedTabs

                _successMessage.value = "Commit successful! SHA: ${newSha.take(7)}"
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to commit changes", e)
                _errorMessage.value = "Failed to commit changes: ${e.localizedMessage}. Check your token scopes (must have 'repo' permissions)."
            } finally {
                _isSyncing.value = false
            }
        }
    }

    // --- Find & Replace Engine ---

    fun toggleSearch() {
        _isSearchOpen.value = !_isSearchOpen.value
        if (!_isSearchOpen.value) {
            clearSearch()
        } else {
            performSearch()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        performSearch()
    }

    fun setReplaceQuery(query: String) {
        _replaceQuery.value = query
    }

    fun toggleRegexSearch() {
        _isRegexSearch.value = !_isRegexSearch.value
        performSearch()
    }

    fun toggleCaseSensitiveSearch() {
        _isCaseSensitiveSearch.value = !_isCaseSensitiveSearch.value
        performSearch()
    }

    private fun performSearch() {
        val query = _searchQuery.value
        val tab = getActiveTabState() ?: return

        if (query.isEmpty()) {
            _searchMatches.value = emptyList()
            _currentMatchIndex.value = -1
            return
        }

        val matches = mutableListOf<SearchMatch>()
        try {
            if (_isRegexSearch.value) {
                val options = if (_isCaseSensitiveSearch.value) emptySet() else setOf(RegexOption.IGNORE_CASE)
                val regex = Regex(query, options)
                for (i in tab.lines.indices) {
                    val line = tab.lines[i]
                    regex.findAll(line).forEach { result ->
                        matches.add(SearchMatch(lineIndex = i, startChar = result.range.first, endChar = result.range.last + 1))
                    }
                }
            } else {
                for (i in tab.lines.indices) {
                    val line = tab.lines[i]
                    var startIdx = line.indexOf(query, 0, ignoreCase = !_isCaseSensitiveSearch.value)
                    while (startIdx != -1) {
                        matches.add(SearchMatch(lineIndex = i, startChar = startIdx, endChar = startIdx + query.length))
                        startIdx = line.indexOf(query, startIdx + 1, ignoreCase = !_isCaseSensitiveSearch.value)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Regex search error", e)
        }

        _searchMatches.value = matches
        _currentMatchIndex.value = if (matches.isNotEmpty()) 0 else -1
    }

    fun nextMatch(): SearchMatch? {
        val matches = _searchMatches.value
        if (matches.isEmpty()) return null
        val nextIdx = (_currentMatchIndex.value + 1) % matches.size
        _currentMatchIndex.value = nextIdx
        return matches[nextIdx]
    }

    fun prevMatch(): SearchMatch? {
        val matches = _searchMatches.value
        if (matches.isEmpty()) return null
        val prevIdx = if (_currentMatchIndex.value - 1 < 0) matches.size - 1 else _currentMatchIndex.value - 1
        _currentMatchIndex.value = prevIdx
        return matches[prevIdx]
    }

    fun replaceCurrentMatch() {
        val matches = _searchMatches.value
        val currentIdx = _currentMatchIndex.value
        if (matches.isEmpty() || currentIdx < 0 || currentIdx >= matches.size) return

        val match = matches[currentIdx]
        val tab = getActiveTabState() ?: return
        if (match.lineIndex < 0 || match.lineIndex >= tab.lines.size) return

        val line = tab.lines[match.lineIndex]
        val replacement = _replaceQuery.value

        val newLine = line.substring(0, match.startChar) + replacement + line.substring(match.endChar)
        val mutableLines = tab.lines.toMutableList()
        mutableLines[match.lineIndex] = newLine

        updateActiveTab(mutableLines, updateUndo = true, activeLine = match.lineIndex)
        performSearch() // refresh match indices
    }

    fun replaceAllMatches() {
        val matches = _searchMatches.value
        if (matches.isEmpty()) return
        val tab = getActiveTabState() ?: return

        val mutableLines = tab.lines.toMutableList()
        val query = _searchQuery.value
        val replacement = _replaceQuery.value

        try {
            if (_isRegexSearch.value) {
                val options = if (_isCaseSensitiveSearch.value) emptySet() else setOf(RegexOption.IGNORE_CASE)
                val regex = Regex(query, options)
                for (i in mutableLines.indices) {
                    mutableLines[i] = regex.replace(mutableLines[i], replacement)
                }
            } else {
                for (i in mutableLines.indices) {
                    var line = mutableLines[i]
                    var startIdx = line.indexOf(query, 0, ignoreCase = !_isCaseSensitiveSearch.value)
                    while (startIdx != -1) {
                        line = line.substring(0, startIdx) + replacement + line.substring(startIdx + query.length)
                        startIdx = line.indexOf(query, startIdx + replacement.length, ignoreCase = !_isCaseSensitiveSearch.value)
                    }
                    mutableLines[i] = line
                }
            }
            updateActiveTab(mutableLines, updateUndo = true)
            clearSearch()
            _successMessage.value = "Replaced ${matches.size} matches successfully."
        } catch (e: Exception) {
            _errorMessage.value = "Replace all failed: ${e.localizedMessage}"
        }
    }

    private fun clearSearch() {
        _searchQuery.value = ""
        _replaceQuery.value = ""
        _searchMatches.value = emptyList()
        _currentMatchIndex.value = -1
    }

    // --- Helper Getters ---

    fun getActiveTabState(): TabState? {
        val activePath = _activeTabPath.value ?: return null
        return _openTabs.value.find { it.path == activePath }
    }

    fun saveTabScrollState(path: String, index: Int, offset: Int) {
        _openTabs.value = _openTabs.value.map { tab ->
            if (tab.path == path) {
                tab.copy(scrollIndex = index, scrollOffset = offset)
            } else {
                tab
            }
        }
    }
}
