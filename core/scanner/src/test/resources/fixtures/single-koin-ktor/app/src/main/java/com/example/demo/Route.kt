package com.example.demo

import kotlinx.serialization.Serializable

sealed interface Route {
    // 시작 화면
    @Serializable
    data object Home : Route

    /** 상세. id 로 연다 */
    @Serializable
    data class Detail(val id: String) : Route

    @Serializable
    data object Settings : Route
}

/** 화면 상태. @Serializable 이 없어 목적지로 잡히면 안 된다 */
sealed interface UiState {
    data object Loading : UiState
    data class Loaded(val items: List<String>) : UiState
    data class Failed(val message: String) : UiState
}
