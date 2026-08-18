package com.fitnessapp.android.ui.friends

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fitnessapp.android.FitnessApp
import com.fitnessapp.android.data.model.FriendProfile
import com.fitnessapp.android.data.model.PendingFriendRequest
import com.fitnessapp.android.data.model.UserPublicProfile
import com.fitnessapp.android.data.network.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
    val friends: List<FriendProfile> = emptyList(),
    val pendingRequests: List<PendingFriendRequest> = emptyList(),
    val searchResults: List<UserPublicProfile> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val actionLoadingId: Int? = null,
    val selectedUser: UserPublicProfile? = null,
    val message: String? = null,
    val error: String? = null,
)

class FriendsViewModel(app: Application) : AndroidViewModel(app) {
    private val container get() = (getApplication<FitnessApp>()).container
    private var searchJob: Job? = null

    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        val token = container.authStore.jwt ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            
            val friendsResult = container.apiClient.listFriends(token)
            val requestsResult = container.apiClient.listPendingRequests(token)

            val friends = if (friendsResult is ApiResult.Success) friendsResult.value else emptyList()
            val requests = if (requestsResult is ApiResult.Success) requestsResult.value else emptyList()

            _state.update {
                it.copy(
                    isLoading = false,
                    friends = friends,
                    pendingRequests = requests,
                )
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            val token = container.authStore.jwt ?: return@launch
            _state.update { it.copy(isSearching = true) }
            val result = container.apiClient.searchUsers(token, query.trim())
            if (result is ApiResult.Success) {
                _state.update { it.copy(searchResults = result.value, isSearching = false) }
            } else {
                _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
            }
        }
    }

    fun sendFriendRequest(targetUserId: Int) {
        val token = container.authStore.jwt ?: return
        viewModelScope.launch {
            _state.update { it.copy(actionLoadingId = targetUserId, error = null, message = null) }
            val result = container.apiClient.sendFriendRequest(token, targetUserId)
            when (result) {
                is ApiResult.Success -> {
                    // Update search results status
                    val updatedResults = _state.value.searchResults.map {
                        if (it.id == targetUserId) it.copy(friendshipStatus = "PENDING_SENT") else it
                    }
                    val updatedSelected = _state.value.selectedUser?.let {
                        if (it.id == targetUserId) it.copy(friendshipStatus = "PENDING_SENT") else it
                    }
                    _state.update {
                        it.copy(
                            actionLoadingId = null,
                            searchResults = updatedResults,
                            selectedUser = updatedSelected,
                            message = "Friend request sent!",
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(actionLoadingId = null, error = "Failed: ${result.detail}") }
                }
                else -> {
                    _state.update { it.copy(actionLoadingId = null, error = "Could not send friend request") }
                }
            }
        }
    }

    fun acceptFriendRequest(requestId: Int) {
        val token = container.authStore.jwt ?: return
        viewModelScope.launch {
            _state.update { it.copy(actionLoadingId = requestId, error = null, message = null) }
            val result = container.apiClient.acceptFriendRequest(token, requestId)
            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(actionLoadingId = null, message = "Friend request accepted!") }
                    loadData()
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(actionLoadingId = null, error = "Failed: ${result.detail}") }
                }
                else -> {
                    _state.update { it.copy(actionLoadingId = null, error = "Could not accept request") }
                }
            }
        }
    }

    fun rejectFriendRequest(requestId: Int) {
        val token = container.authStore.jwt ?: return
        viewModelScope.launch {
            _state.update { it.copy(actionLoadingId = requestId, error = null, message = null) }
            val result = container.apiClient.rejectFriendRequest(token, requestId)
            when (result) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            actionLoadingId = null,
                            pendingRequests = it.pendingRequests.filterNot { req -> req.requestId == requestId },
                            message = "Friend request rejected",
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(actionLoadingId = null, error = "Failed: ${result.detail}") }
                }
                else -> {
                    _state.update { it.copy(actionLoadingId = null, error = "Could not reject request") }
                }
            }
        }
    }

    fun removeFriend(friendId: Int) {
        val token = container.authStore.jwt ?: return
        viewModelScope.launch {
            _state.update { it.copy(actionLoadingId = friendId, error = null, message = null) }
            val result = container.apiClient.deleteFriend(token, friendId)
            when (result) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            actionLoadingId = null,
                            friends = it.friends.filterNot { f -> f.id == friendId },
                            message = "Friend removed",
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(actionLoadingId = null, error = "Failed: ${result.detail}") }
                }
                else -> {
                    _state.update { it.copy(actionLoadingId = null, error = "Could not remove friend") }
                }
            }
        }
    }

    fun selectUser(user: UserPublicProfile?) {
        _state.update { it.copy(selectedUser = user) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, error = null) }
    }
}
