package uk.co.zenithal.carrel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import uk.co.zenithal.carrel.auth.Session
import uk.co.zenithal.carrel.data.GenreLink
import uk.co.zenithal.carrel.data.ReadingStatus
import uk.co.zenithal.carrel.data.Profile
import uk.co.zenithal.carrel.ui.book.BookScreen
import uk.co.zenithal.carrel.ui.book.ShelfPanelSignedOut
import uk.co.zenithal.carrel.ui.browse.GenreScreen
import uk.co.zenithal.carrel.ui.browse.GenresScreen
import uk.co.zenithal.carrel.ui.browse.SeriesScreen
import uk.co.zenithal.carrel.ui.home.HomeScreen
import uk.co.zenithal.carrel.ui.you.ProfileSection
import uk.co.zenithal.carrel.ui.library.LibraryScreen
import uk.co.zenithal.carrel.ui.library.ShelfPanel
import uk.co.zenithal.carrel.ui.library.ShelfScreen
import uk.co.zenithal.carrel.ui.search.SearchScreen
import uk.co.zenithal.carrel.ui.auth.SignInMode
import uk.co.zenithal.carrel.ui.auth.SignInScreen
import uk.co.zenithal.carrel.ui.components.Gap
import uk.co.zenithal.carrel.ui.components.LinkButton
import uk.co.zenithal.carrel.ui.components.SectionTitle
import uk.co.zenithal.carrel.ui.components.SignInPrompt
import uk.co.zenithal.carrel.ui.theme.Carrel
import kotlin.reflect.KClass

@Serializable data object HomeRoute
@Serializable data object SearchRoute
@Serializable data object LibraryRoute
@Serializable data object YouRoute
// The mode goes by name: release builds rename classes, and navigation couldn't then find an enum argument's class.
@Serializable data class SignInRoute(val mode: String) {
    constructor(mode: SignInMode) : this(mode.name)
}
/** A book by the API path that loads it: /books/12, /books/hardcover/123, or /books/openlibrary/OL1W. */
@Serializable data class BookRoute(val path: String)
@Serializable data class SeriesRoute(val hardcoverId: Int, val fromBook: Int? = null)
@Serializable data class GenreRoute(val slug: String)
@Serializable data object GenresRoute
/** One of the reader's shelves, by its slug (want-to-read), not the enum, for the same reason as SignInRoute. */
@Serializable data class ShelfRoute(val slug: String)

private data class Tab(val route: Any, val type: KClass<*>, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(HomeRoute, HomeRoute::class, "Home", Icons.Outlined.Home),
    Tab(SearchRoute, SearchRoute::class, "Search", Icons.Outlined.Search),
    Tab(LibraryRoute, LibraryRoute::class, "Library", Icons.AutoMirrored.Outlined.List),
    Tab(YouRoute, YouRoute::class, "You", Icons.Outlined.Person),
)

/** The four tabs, and the pages opened from them. `profile` is null when signed out. */
@Composable
fun MainScreen(nav: NavHostController, session: Session, profile: Profile?) {
    val context = LocalContext.current
    val paper = Carrel.colors.paper
    val openLegal = { path: String -> openWebsitePage(context, path, paper) }
    val signIn = { mode: SignInMode -> nav.navigate(SignInRoute(mode)) }
    val entry by nav.currentBackStackEntryAsState()
    val signingIn = entry?.destination?.hasRoute(SignInRoute::class) == true
    val openBook = { path: String -> nav.navigate(BookRoute(path)) }
    val openSeries = { id: Int, fromBook: Int? -> nav.navigate(SeriesRoute(id, fromBook)) }
    val openGenre = { genre: GenreLink -> nav.navigate(GenreRoute(genre.slug)) }

    Scaffold(
        containerColor = paper,
        bottomBar = { if (!signingIn) TabBar(nav) },
    ) { padding ->
        NavHost(nav, startDestination = HomeRoute, modifier = Modifier.padding(padding)) {
            composable<HomeRoute> {
                HomeScreen(signedIn = profile != null, openBook)
            }
            composable<SearchRoute> {
                SearchScreen(openBook, openSeries, openGenre) { nav.navigate(GenresRoute) }
            }
            composable<BookRoute> { backStackEntry ->
                BookScreen(backStackEntry.toRoute<BookRoute>().path, openBook, openSeries, openGenre) {
                    if (profile == null) {
                        ShelfPanelSignedOut({ signIn(SignInMode.SignIn) }, { signIn(SignInMode.SignUp) })
                    } else {
                        ShelfPanel(it)
                    }
                }
            }
            composable<SeriesRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<SeriesRoute>()
                SeriesScreen(route.hardcoverId, route.fromBook, openBook)
            }
            composable<GenreRoute> { backStackEntry ->
                GenreScreen(backStackEntry.toRoute<GenreRoute>().slug, openBook)
            }
            composable<GenresRoute> {
                GenresScreen(openGenre)
            }
            composable<LibraryRoute> {
                if (profile == null) {
                    TabPage {
                        SignInPrompt(
                            "Your library",
                            "Sign in to keep track of what you’re reading, what you’ve read, and what you want to read next.",
                            { signIn(SignInMode.SignIn) },
                            { signIn(SignInMode.SignUp) },
                        )
                    }
                } else {
                    LibraryScreen(openBook) { status -> nav.navigate(ShelfRoute(status.slug)) }
                }
            }
            composable<ShelfRoute> { backStackEntry ->
                val status = ReadingStatus.fromSlug(backStackEntry.toRoute<ShelfRoute>().slug)
                if (profile != null && status != null) ShelfScreen(status, openBook)
            }
            composable<YouRoute> {
                TabPage { YouPage(session, profile, signIn, openLegal) }
            }
            composable<SignInRoute> { backStackEntry ->
                // Signing in changes the session; then this page has done its job.
                LaunchedEffect(session) {
                    if (session is Session.SignedIn) nav.popBackStack()
                }
                SignInScreen(SignInMode.valueOf(backStackEntry.toRoute<SignInRoute>().mode), openLegal)
            }
        }
    }
}

@Composable
private fun TabBar(nav: NavHostController) {
    val colors = Carrel.colors
    // The tab a page was opened from stays selected while it's showing.
    val stack by nav.currentBackStack.collectAsState()
    val current = stack.lastOrNull { e -> TABS.any { e.destination.hasRoute(it.type) } }?.destination
    Column {
        HorizontalDivider(color = colors.rule)
        NavigationBar(containerColor = colors.paper, tonalElevation = 0.dp) {
            TABS.forEach { tab ->
                NavigationBarItem(
                    selected = current?.hasRoute(tab.type) == true,
                    onClick = {
                        nav.navigate(tab.route) {
                            // One copy of each tab, keeping its own page as it was (a search, a scroll position).
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                        // Always the tab's own page, not a book or shelf that was open on top of it.
                        nav.popBackStack(tab.route, inclusive = false)
                    },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(tab.label.uppercase(), style = Carrel.type.mono.copy(letterSpacing = 0.1.em)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.accent,
                        selectedTextColor = colors.ink,
                        indicatorColor = colors.paperRaised,
                        unselectedIconColor = colors.inkSoft,
                        unselectedTextColor = colors.inkSoft,
                    ),
                )
            }
        }
    }
}

/** A tab's page: paper, the website's 16px gutter, scrolling. */
@Composable
private fun TabPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Carrel.colors.paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        content = content,
    )
}

/** The reader's profile, and the privacy and copyright pages. Settings, with the account and signing out, join it later. */
@Composable
private fun YouPage(session: Session, profile: Profile?, signIn: (SignInMode) -> Unit, openLegal: (String) -> Unit) {
    val colors = Carrel.colors
    if (profile != null && session is Session.SignedIn) {
        ProfileSection(profile)
    } else {
        SignInPrompt(
            "Your account",
            "Sign in or create an account to keep a library, set reading goals, and share your profile.",
            { signIn(SignInMode.SignIn) },
            { signIn(SignInMode.SignUp) },
        )
    }
    Gap(48)
    SectionTitle("About Carrel")
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinkButton("Privacy", { openLegal("/privacy") })
        LinkButton("Copyright", { openLegal("/copyright") })
    }
    Gap(16)
    Text("Book data from Hardcover and Open Library.", style = Carrel.type.mono, color = colors.inkSoft)
}
