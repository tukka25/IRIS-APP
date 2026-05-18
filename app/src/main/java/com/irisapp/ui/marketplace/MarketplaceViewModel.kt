package com.irisapp.ui.marketplace

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.irisapp.data.repository.MarketplaceEntry
import com.irisapp.data.repository.MarketplaceRepository
import com.irisapp.data.repository.WorkflowRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** ViewModel for the Marketplace tab. Handles browse, publish, import, and delete. */
class MarketplaceViewModel(application: Application) : AndroidViewModel(application) {

    private val marketplaceRepo = MarketplaceRepository(application)
    private val workflowRepo = WorkflowRepository(application)

    private val _state = MutableStateFlow(MarketplaceUiState())
    val state: StateFlow<MarketplaceUiState> = _state.asStateFlow()

    init {
        loadUsername()
        browseMarketplace()
        loadMyWorkflows()
    }

    // ── Username ─────────────────────────────────────────────────────────────

    private fun loadUsername() {
        val username = marketplaceRepo.getUsername()
        if (username.isBlank()) {
            _state.value = _state.value.copy(showUsernameDialog = true)
        } else {
            _state.value = _state.value.copy(username = username)
        }
    }

    fun setUsername(name: String) {
        val trimmed = name.trim()
        if (trimmed.length < 2) return
        marketplaceRepo.saveUsername(trimmed)
        _state.value = _state.value.copy(
            username = trimmed,
            showUsernameDialog = false,
            error = null
        )
        refreshMyEntries()
    }

    // ── Browse ────────────────────────────────────────────────────────────────

    private fun browseMarketplace() {
        _state.value = _state.value.copy(isLoading = true)
        viewModelScope.launch {
            marketplaceRepo.browse().collect { entries ->
                val myAuthor = _state.value.username
                val myEntries = entries.filter { it.author == myAuthor }
                _state.value = _state.value.copy(
                    entries = entries,
                    myEntries = myEntries,
                    isLoading = false,
                    error = null
                )
            }
        }
        // Safety timeout — clear loading if Firebase silently fails
        viewModelScope.launch {
            kotlinx.coroutines.delay(15_000)
            if (_state.value.isLoading) {
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    private fun refreshMyEntries() {
        val myAuthor = _state.value.username
        val myEntries = _state.value.entries.filter { it.author == myAuthor }
        _state.value = _state.value.copy(myEntries = myEntries)
    }

    fun refresh() {
        browseMarketplace()
    }

    // ── Select for preview ───────────────────────────────────────────────────

    fun selectEntry(entry: MarketplaceEntry) {
        _state.value = _state.value.copy(selectedEntry = entry)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedEntry = null, showImportConfirm = false)
    }

    // ── Publish ─────────────────────────────────────────────────────────────

    private fun loadMyWorkflows() {
        val workflows = workflowRepo.loadAll()
        _state.value = _state.value.copy(publishWorkflows = workflows)
    }

    fun showPublishDialog() {
        loadMyWorkflows()
        _state.value = _state.value.copy(
            showPublishDialog = true,
            selectedPublishWorkflow = null,
            publishTags = ""
        )
    }

    fun dismissPublishDialog() {
        _state.value = _state.value.copy(showPublishDialog = false)
    }

    fun selectPublishWorkflow(workflow: com.irisapp.domain.model.PlannedWorkflow) {
        _state.value = _state.value.copy(selectedPublishWorkflow = workflow)
    }

    fun setPublishTags(tags: String) {
        _state.value = _state.value.copy(publishTags = tags)
    }

    fun publish() {
        val workflow = _state.value.selectedPublishWorkflow ?: return
        val author = _state.value.username
        if (author.isBlank()) return

        val tags = _state.value.publishTags
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                marketplaceRepo.publish(workflow = workflow, author = author, tags = tags)
                _state.value = _state.value.copy(
                    showPublishDialog = false,
                    publishSuccess = true,
                    isLoading = false
                )
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to publish: ${e.message}"
                )
            }
        }
    }

    // ── Import ───────────────────────────────────────────────────────────────

    fun showImportConfirm() {
        _state.value = _state.value.copy(showImportConfirm = true)
    }

    fun confirmImport() {
        val entry = _state.value.selectedEntry ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val success = marketplaceRepo.importToLocal(getApplication(), entry)
            if (success) {
                _state.value = _state.value.copy(
                    selectedEntry = null,
                    showImportConfirm = false,
                    isLoading = false,
                    error = null,
                    importCompleted = true
                )
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to import workflow"
                )
            }
        }
    }

    fun dismissImportConfirm() {
        _state.value = _state.value.copy(showImportConfirm = false)
    }

    // ── Delete own ───────────────────────────────────────────────────────────

    fun deleteEntry(entry: MarketplaceEntry) {
        viewModelScope.launch {
            marketplaceRepo.deleteEntry(entry.id, _state.value.username)
            refresh()
        }
    }

    // ── Error ───────────────────────────────────────────────────────────────

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun dismissPublishSuccess() {
        _state.value = _state.value.copy(publishSuccess = false)
    }

    /** Called by MainActivity after it has processed the importCompleted event. */
    fun dismissImportCompleted() {
        _state.value = _state.value.copy(importCompleted = false)
    }
}