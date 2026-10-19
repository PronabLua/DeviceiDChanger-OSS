package io.github.pronablua.deviceidchanger

import android.app.Application
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OperationNotice(val title: String, val message: String, val successful: Boolean) {
    companion object {
        fun fromReply(action: String, reply: HelperReply): OperationNotice {
            val rollbackFailed = reply.fields["error_code"] == "rollback_failed"
            val title = when {
                reply.rolledBack -> "Change rolled back"
                rollbackFailed -> "Rollback failed"
                reply.saved && reply.restarted -> if (action == "restore") "Override removed · app restarted" else "Saved · app restarted"
                reply.saved -> if (action == "restore") "Override removed · restart needs attention" else "Saved · restart needs attention"
                reply.fields["error_code"] == "busy" -> "Another operation is running"
                else -> "Change not confirmed"
            }
            val message = when {
                reply.rolledBack && reply.restoredLaunch -> "Your previous setting was restored, and Android confirmed reopening the app. Your entered ID was kept for editing."
                reply.rolledBack -> "Your previous setting was restored, but Android did not confirm reopening the app. Open it manually. Your entered ID was kept for editing."
                rollbackFailed -> "The previous setting could not be restored. The attempted change may still be saved. Check the saved override before making another change."
                else -> reply.message.ifBlank {
                    when {
                        reply.saved && reply.restarted -> "The new process can use this setting. Its Android ID has not been observed yet."
                        reply.saved && action == "restore" -> "The override was removed, but its app restart was not confirmed. Close and reopen the app manually."
                        reply.fields["error_code"] == "busy" -> "Wait for the current module operation to finish, then try again. Your entered ID has been kept."
                        else -> "Refresh the app settings before trying again."
                    }
                }
            }
            return OperationNotice(title, message, reply.successful && reply.saved && reply.restarted && !reply.rolledBack && !rollbackFailed)
        }
    }
}

fun draftAfterChange(currentDraft: String, reply: HelperReply, currentProfile: HelperReply): String {
    return if (reply.successful && reply.saved && reply.restarted && !reply.rolledBack && reply.fields["error_code"] != "rollback_failed" && currentProfile.successful) {
        currentProfile.fields["configured_id"].orEmpty()
    } else {
        currentDraft
    }
}

data class ManagerState(
    val ownerUser: Boolean = Process.myUid() / 100000 == 0,
    val apps: List<AppEntry> = emptyList(),
    val loadingApps: Boolean = true,
    val checkingRoot: Boolean = false,
    val status: HelperReply? = null,
    val catalogError: String? = null,
    val query: String = "",
    val selected: AppEntry? = null,
    val selectionLoading: Boolean = false,
    val selectionReply: HelperReply? = null,
    val editedId: String = "",
    val busy: Boolean = false,
    val notice: OperationNotice? = null
) {
    val configuredId: String get() = selectionReply?.fields?.get("configured_id").orEmpty()
    val overrideEnabled: Boolean get() = selectionReply?.fields?.get("enabled") == "true"
    val moduleReady: Boolean get() = status?.successful == true && status.fields["protocol_version"] == "1" && status.fields["module_state"] == "enabled" && status.fields["runtime"] !in setOf("pending_reboot", "unavailable")
    private val canChange: Boolean get() = ownerUser && !busy && !selectionLoading && selected != null && selected.abi != AppAbi.Unsupported32Bit && moduleReady
    val canEdit: Boolean get() = canChange && selectionReply?.successful == true
    val canRestore: Boolean get() = canChange && ((selectionReply?.successful == true && overrideEnabled) || (selectionReply?.fields?.get("status") == "error" && selectionReply.fields["error_code"] == "profile_invalid"))
}

class ManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val helper = RootHelper()
    private val catalog = AppCatalog(application)
    private val selectionRequests = SelectionRequests()
    private val mutableState = MutableStateFlow(ManagerState())
    val state = mutableState.asStateFlow()

    init {
        refreshApps()
        refreshStatus()
    }

    fun refreshApps() {
        if (!mutableState.value.ownerUser) {
            mutableState.update { it.copy(loadingApps = false) }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(loadingApps = true, catalogError = null) }
            try {
                val apps = catalog.read()
                mutableState.update { it.copy(apps = apps, loadingApps = false) }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                mutableState.update { it.copy(loadingApps = false, catalogError = "Apps could not be loaded. Try refreshing the list.") }
            }
        }
    }

    fun refreshStatus() {
        if (mutableState.value.checkingRoot || mutableState.value.busy || !mutableState.value.ownerUser) return
        viewModelScope.launch {
            mutableState.update { it.copy(checkingRoot = true) }
            val reply = runHelper("status")
            mutableState.update { it.copy(status = reply, checkingRoot = false) }
        }
    }

    fun search(query: String) {
        mutableState.update { it.copy(query = query) }
    }

    fun select(app: AppEntry) {
        if (mutableState.value.busy) return
        val request = selectionRequests.next()
        mutableState.update { it.copy(selected = app, selectionLoading = true, selectionReply = null, editedId = "", notice = null) }
        viewModelScope.launch {
            val reply = runHelper("get", app.packageName)
            mutableState.update {
                if (!selectionRequests.isCurrent(request) || it.selected?.packageName != app.packageName) it
                else it.copy(selectionLoading = false, selectionReply = reply, editedId = reply.fields["configured_id"].orEmpty())
            }
        }
    }

    fun back() {
        if (!mutableState.value.busy) {
            selectionRequests.next()
            mutableState.update { it.copy(selected = null, selectionReply = null, notice = null) }
        }
    }

    fun editId(identifier: String) {
        mutableState.update { it.copy(editedId = identifier.take(64), notice = null) }
    }

    fun randomize() {
        mutableState.update { it.copy(editedId = Identity.randomId(), notice = null) }
    }

    fun apply() {
        val current = mutableState.value
        if (!current.canEdit || !Identity.validId(Identity.cleanId(current.editedId))) return
        change("apply", Identity.cleanId(current.editedId))
    }

    fun restore() {
        if (!mutableState.value.canRestore) return
        change("restore", null)
    }

    private fun change(action: String, identifier: String?) {
        val app = mutableState.value.selected ?: return
        mutableState.update { it.copy(busy = true, notice = null) }
        viewModelScope.launch {
            val reply = runHelper(action, app.packageName, identifier)
            val after = runHelper("get", app.packageName)
            mutableState.update {
                it.copy(
                    busy = false,
                    selectionReply = after,
                    editedId = draftAfterChange(it.editedId, reply, after),
                    notice = OperationNotice.fromReply(action, reply)
                )
            }
        }
    }

    private suspend fun runHelper(action: String, packageName: String? = null, identifier: String? = null): HelperReply {
        return try {
            helper.run(action, packageName, identifier)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            HelperReply(-1, emptyMap(), "The operation could not be completed. Refresh to check the current state.")
        }
    }
}
