package com.kaushikkm.ekatrahub.ui.timeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.kaushikkm.ekatrahub.EkatraApp
import com.kaushikkm.ekatrahub.data.AppCount
import com.kaushikkm.ekatrahub.data.NotificationEntity
import com.kaushikkm.ekatrahub.service.NotificationActions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Holds the timeline's state and survives screen rotation. The UI only reads state from here
 * and sends events (typing, selecting an app, deleting) back - it never touches the database.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class TimelineViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = (app as EkatraApp).database.notificationDao()

    private val query = MutableStateFlow("")
    private val _selectedApp = MutableStateFlow<String?>(null)
    val selectedApp: StateFlow<String?> = _selectedApp.asStateFlow()

    val apps: StateFlow<List<AppCount>> = dao.observeApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Search waits 200 ms after you stop typing before querying, so typing stays smooth
     * instead of hitting the database on every key press.
     */
    val items: Flow<PagingData<NotificationEntity>> = combine(
        query.debounce { if (it.isEmpty()) 0L else 200L }.map { it.trim() }.distinctUntilChanged(),
        _selectedApp,
    ) { q, pkg -> q to pkg }
        .flatMapLatest { (q, pkg) ->
            Pager(PagingConfig(pageSize = 50, prefetchDistance = 30, enablePlaceholders = false)) {
                dao.timeline(pkg, q)
            }.flow
        }
        .cachedIn(viewModelScope)

    fun setQuery(text: String) {
        query.value = text
    }

    /** Tapping the selected chip again clears the filter. */
    fun selectApp(packageName: String?) {
        _selectedApp.value = if (_selectedApp.value == packageName) null else packageName
    }

    /** Swipe right: toggle read. Marking read also tells the source app, where it can. */
    fun toggleRead(item: NotificationEntity) {
        val nowRead = !item.isRead
        if (nowRead) NotificationActions.markReadAtSource(item)
        viewModelScope.launch { dao.setRead(item.id, nowRead) }
    }

    /** Tap: open it in its app, and count it as read. */
    fun open(item: NotificationEntity) {
        NotificationActions.open(getApplication(), item)
        if (!item.isRead) viewModelScope.launch { dao.setRead(item.id, true) }
    }

    fun canReply(item: NotificationEntity): Boolean = NotificationActions.canReply(item)

    /** Inline reply. Returns false if the app no longer accepts replies to this notification. */
    fun reply(item: NotificationEntity, text: String): Boolean {
        val sent = NotificationActions.reply(getApplication(), item, text)
        if (sent && !item.isRead) viewModelScope.launch { dao.setRead(item.id, true) }
        return sent
    }

    /** Full swipe right: remove from Ekatra (and from the notification shade if it's still there). */
    fun remove(item: NotificationEntity) {
        NotificationActions.dismissFromShade(item.sbnKey)
        viewModelScope.launch { dao.deleteById(item.id) }
    }

    /**
     * Swipe bar, partway: mark everything shown read - one app's, or all ([packageName] = null).
     * Also presses each app's own "Mark as read" button where it has one.
     */
    fun markAllRead(packageName: String?) {
        viewModelScope.launch {
            val unread = if (packageName == null) dao.allUnread() else dao.unreadForApp(packageName)
            unread.forEach(NotificationActions::markReadAtSource)
            if (packageName == null) dao.markAllRead() else dao.markAppRead(packageName)
        }
    }

    /** Swipe bar, all the way: remove everything shown - one app's, or all ([packageName] = null). */
    fun removeAll(packageName: String?) {
        viewModelScope.launch {
            val keys = if (packageName == null) dao.allKeys() else dao.keysForApp(packageName)
            keys.forEach(NotificationActions::dismissFromShade)
            if (packageName == null) dao.clearAll() else dao.clearApp(packageName)
            if (packageName != null && _selectedApp.value == packageName) _selectedApp.value = null
        }
    }

    /** Clears the selected app's history, or everything if no app is selected. */
    fun clear() {
        viewModelScope.launch {
            val pkg = _selectedApp.value
            if (pkg == null) {
                dao.clearAll()
            } else {
                dao.clearApp(pkg)
                _selectedApp.value = null
            }
        }
    }
}
