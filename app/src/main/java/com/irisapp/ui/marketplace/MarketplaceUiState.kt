package com.irisapp.ui.marketplace

import com.irisapp.data.repository.MarketplaceEntry
import com.irisapp.domain.model.PlannedWorkflow

/** UI state for the Marketplace tab. */
data class MarketplaceUiState(
    val entries: List<MarketplaceEntry> = emptyList(),
    val myEntries: List<MarketplaceEntry> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedEntry: MarketplaceEntry? = null,
    val showPublishDialog: Boolean = false,
    val showImportConfirm: Boolean = false,
    val username: String = "",
    val showUsernameDialog: Boolean = false,
    val publishWorkflows: List<PlannedWorkflow> = emptyList(),
    val selectedPublishWorkflow: PlannedWorkflow? = null,
    val publishTags: String = "",
    val publishSuccess: Boolean = false,
    /** Set to true by the ViewModel after a successful import; observed by MainActivity to refresh workflows. */
    val importCompleted: Boolean = false
)

/** Preview data shown in the bottom sheet before import. */
data class MarketplacePreviewState(
    val workflowName: String,
    val author: String,
    val triggerLabel: String,
    val actionCount: Int,
    val tags: List<String>,
    val createdAt: Long
)