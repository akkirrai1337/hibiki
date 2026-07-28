package org.akkirrai.hibiki.shared.navigation

data class AppNavigationState(
    val currentTopLevel: AppTopLevelDestination = AppTopLevelDestination.HOME,
)

/** Applies only navigation state changes that are platform-neutral. */
fun AppNavigationState.reduce(event: AppNavigationEvent): AppNavigationState = when (event) {
    is AppNavigationEvent.SelectTopLevel -> copy(currentTopLevel = event.destination)
    AppNavigationEvent.Back,
    is AppNavigationEvent.OpenDetails,
    -> this
}
