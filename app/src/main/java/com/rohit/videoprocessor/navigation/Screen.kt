package com.rohit.videoprocessor.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Processing : Screen("processing")
    data object Result : Screen("result")

    /** Optional Person Detail screen - route carries the tapped person's id. */
    data object PersonDetail : Screen("person_detail/{personId}") {
        const val ARG_PERSON_ID = "personId"
        fun route(personId: Int) = "person_detail/$personId"
    }

    /** Debug-build-only accuracy-inspection screens - see BuildConfig.DEBUG gating in HomeScreen/ResultScreen. */
    data object DebugSettings : Screen("debug_settings")
    data object Debug : Screen("debug")
}
