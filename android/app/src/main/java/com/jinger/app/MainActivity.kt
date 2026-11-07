package com.jinger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jinger.app.data.AppContainer
import com.jinger.app.ui.apply.ApplyScreen
import com.jinger.app.ui.feed.FeedScreen
import com.jinger.app.ui.onboarding.OnboardingScreen
import com.jinger.app.ui.theme.JingerTheme
import com.jinger.app.ui.tracker.TrackerScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val FEED = "feed"
    const val TRACKER = "tracker"
    const val APPLY = "apply/{jobId}"
    fun apply(jobId: String) = "apply/${java.net.URLEncoder.encode(jobId, "UTF-8")}"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as JingerApp).container
        setContent {
            JingerTheme { JingerNavHost(container) }
        }
    }
}

@Composable
fun JingerNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val profile by container.profile.profile.collectAsState()
    val start = if (profile == null) Routes.ONBOARDING else Routes.FEED

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                container = container,
                onDone = { nav.navigate(Routes.FEED) { popUpTo(Routes.ONBOARDING) { inclusive = true } } },
            )
        }
        composable(Routes.FEED) {
            FeedScreen(
                container = container,
                onApply = { job -> nav.navigate(Routes.apply(job.id)) },
                onOpenTracker = { nav.navigate(Routes.TRACKER) },
                onEditProfile = { nav.navigate(Routes.ONBOARDING) },
            )
        }
        composable(Routes.TRACKER) {
            TrackerScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onOpen = { job -> nav.navigate(Routes.apply(job.id)) },
            )
        }
        composable(Routes.APPLY, arguments = listOf(navArgument("jobId") { type = NavType.StringType })) { entry ->
            val jobId = java.net.URLDecoder.decode(entry.arguments!!.getString("jobId")!!, "UTF-8")
            ApplyScreen(container = container, jobId = jobId, onBack = { nav.popBackStack() })
        }
    }
}
