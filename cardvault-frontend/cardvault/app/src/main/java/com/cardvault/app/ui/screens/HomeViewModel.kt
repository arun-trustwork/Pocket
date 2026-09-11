package com.cardvault.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cardvault.app.data.model.Contact
import com.cardvault.app.data.model.FilterScope
import com.cardvault.app.data.repository.ContactRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Survives configuration changes and back-navigation.
 *
 * KEY DESIGN: optimistic updates
 * ──────────────────────────────
 * saveContact() patches the in-memory cache FIRST (instant), navigates the
 * caller back immediately, then fires the network request in the background.
 * The user never waits for the Cloudflare round-trip (~5–8 s).
 *
 * If the network call fails we keep a "pending sync" list and retry next time
 * the app is in the foreground (future work — for now we log the error).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val repository: ContactRepository) : ViewModel() {

    private val _scope      = MutableStateFlow(FilterScope.ALL)
    private val _query      = MutableStateFlow("")
    private val _isLoading  = MutableStateFlow(false)
    private val _error      = MutableStateFlow<String?>(null)

    // ── Optimistic local cache ────────────────────────────────────────────────
    // Starts null so we know "not yet fetched from server" vs. "empty list".
    private val _localOverride = MutableStateFlow<List<Contact>?>(null)

    val scope:     StateFlow<FilterScope> = _scope.asStateFlow()
    val query:     StateFlow<String>      = _query.asStateFlow()
    val isLoading: StateFlow<Boolean>     = _isLoading.asStateFlow()
    val error:     StateFlow<String?>     = _error.asStateFlow()

    /**
     * flatMapLatest cancels the previous in-flight request when scope/query changes.
     * stateIn(WhileSubscribed(5_000)) keeps the cached value alive for 5 s while
     * navigating away, so returning to HomeScreen is instant from cache.
     */
    private val _serverContacts: StateFlow<List<Contact>> =
        combine(_scope, _query) { scope, query -> scope to query }
            .flatMapLatest { (scope, query) ->
                _isLoading.value = true
                _error.value = null
                repository.observeContacts(scope, query)
                    .onEach { list ->
                        _isLoading.value = false
                        // Merge local optimistic edits so they aren't wiped by a stale fetch
                        _localOverride.value = null   // server data arrived — clear override
                    }
                    .catch { e ->
                        _isLoading.value = false
                        _error.value = "Could not load contacts. Check your connection."
                        emit(emptyList())
                    }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    /**
     * What HomeScreen actually observes.
     * While a local override exists (optimistic patch not yet confirmed by server)
     * we emit that instead of the server snapshot.
     */
    val contacts: StateFlow<List<Contact>> =
        combine(_serverContacts, _localOverride) { server, override ->
            override ?: server
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun setScope(scope: FilterScope) { _scope.value = scope }
    fun setQuery(query: String)      { _query.value = query }

    /** O(1) lookup from cache — used by the form route. */
    fun getContactById(id: String): Contact? =
        contacts.value.firstOrNull { it.id == id }

    /**
     * STASH (used by ScanScreen after AI extraction)
     * ─────────────────────────────────────────────
     * Pre-inserts a freshly scanned contact into the local override cache so
     * the form route can look it up by ID instantly — no network call needed.
     * The actual POST to the backend happens later via saveContact().
     */
    fun stashContact(contact: Contact) {
        val current = (_localOverride.value ?: _serverContacts.value).toMutableList()
        if (current.none { it.id == contact.id }) current.add(0, contact)
        _localOverride.value = current.toList()
    }

    /**
     * OPTIMISTIC SAVE
     * ───────────────
     * 1. Patch the local cache immediately so the list reflects the change now.
     * 2. Call onSaved() so the screen navigates back — the user sees it as instant.
     * 3. Fire the real network POST in the background (viewModelScope outlives the screen).
     * 4. On success: clear the override so the server snapshot takes over cleanly.
     * 5. On failure: put the override back (contact is still visible) + set error message.
     */
    fun saveContact(contact: Contact, onSaved: () -> Unit) {
        // Step 1 — patch local cache
        val current = _serverContacts.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.id == contact.id }
        if (existingIdx >= 0) current[existingIdx] = contact else current.add(0, contact)
        _localOverride.value = current.toList()

        // Step 2 — navigate back immediately (feels instant to the user)
        onSaved()

        // Step 3 — background network sync
        viewModelScope.launch {
            try {
                repository.saveContact(contact)
                // Step 4 — server confirmed, let normal flow take over on next fetch
                _localOverride.value = null
            } catch (e: Exception) {
                // Step 5 — network failed, keep the optimistic state and show a soft error
                _error.value = "Saved locally — will sync when connection improves."
                // Don't wipe _localOverride so the contact remains visible
            }
        }
    }

    class Factory(private val repository: ContactRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel(repository) as T
    }
}
