package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.authStateFlow
import com.example.auth.signOutGoogleAndFirebase
import com.example.data.local.entity.UserRole
import com.example.data.remote.FirestorePosRepository
import com.example.data.remote.ProductDoc
import com.example.data.remote.StaffPinProfileDoc
import com.example.ui.components.LowStockAlertCenterDialog
import com.example.ui.components.PosQrScannerDialog
import com.example.ui.components.RestockDialog
import com.example.ui.screens.GoogleAuthScreen
import com.example.ui.screens.KasirScreen
import com.example.ui.screens.LaporanScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.StaffManagementDialog
import com.example.ui.screens.StokScreen
import com.example.ui.screens.StoreLinkingScreen
import com.example.ui.theme.DangerCrimson
import com.example.ui.theme.KasirBarokahTheme
import com.example.ui.viewmodel.ActiveStoreContext
import com.example.ui.viewmodel.PosViewModel
import com.example.util.StockNotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private const val ROUTE_KASIR = "kasir"
private const val ROUTE_STOK = "stok"
private const val ROUTE_LAPORAN = "laporan"

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

class MainActivity : ComponentActivity() {
    private var openOwnerAlertsTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra(StockNotificationHelper.EXTRA_OPEN_OWNER_LOW_STOCK, false) == true) {
            openOwnerAlertsTrigger++
        }

        setContent {
            KasirBarokahTheme {
                TajirRoot(openOwnerAlertsTrigger = openOwnerAlertsTrigger)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(StockNotificationHelper.EXTRA_OPEN_OWNER_LOW_STOCK, false)) {
            openOwnerAlertsTrigger++
        }
    }
}

@Composable
private fun TajirRoot(openOwnerAlertsTrigger: Int = 0) {
    val context = LocalContext.current
    val isFirebaseInitialized = remember(context) {
        FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
    }
    if (!isFirebaseInitialized) {
        MissingFirebaseConfigScreen()
        return
    }

    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val authUser by remember {
        Firebase.auth.authStateFlow()
    }.collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val user = authUser
    if (user == null) {
        GoogleAuthScreen(
            onAuthSuccess = { }
        )
    } else {
        key(user.uid) {
            val viewModel: PosViewModel = viewModel(
                key = user.uid,
                factory = viewModelFactory {
                    initializer {
                        val dbId = context.getString(R.string.firestore_database_id)
                        val firestore = FirebaseFirestore.getInstance(dbId)
                        val repo = FirestorePosRepository(firestore, Firebase.auth)
                        PosViewModel(
                            repository = repo,
                            currentUserId = user.uid,
                            currentUserEmail = user.email.orEmpty(),
                            currentUserDisplayName = user.displayName.orEmpty()
                        )
                    }
                }
            )

            var showLogoutConfirmDialog by remember { mutableStateOf(false) }

            val handleGoogleSignOut: () -> Unit = {
                viewModel.lockPinSession()
                signOutGoogleAndFirebase(credentialManager, scope)
            }

            CloudStoreGate(
                userEmail = user.email.orEmpty(),
                userDisplayName = user.displayName.orEmpty(),
                openOwnerAlertsTrigger = openOwnerAlertsTrigger,
                viewModel = viewModel,
                onSignOutGoogle = { showLogoutConfirmDialog = true }
            )

            if (showLogoutConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutConfirmDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            tint = DangerCrimson
                        )
                    },
                    title = {
                        Text(
                            text = "Yakin ingin keluar dari akun?",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    text = {
                        val accountLabel = user.email?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                        Text(
                            text = "Anda akan keluar dari akun Google$accountLabel dan harus masuk kembali untuk mengakses data toko.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showLogoutConfirmDialog = false
                                handleGoogleSignOut()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerCrimson),
                            modifier = Modifier.testTag("confirm_logout_button")
                        ) {
                            Text("Ya, Keluar")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showLogoutConfirmDialog = false },
                            modifier = Modifier.testTag("cancel_logout_button")
                        ) {
                            Text("Batal")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MissingFirebaseConfigScreen() {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.widthIn(max = 460.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(28.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Konfigurasi Firebase belum terpasang pada APK ini.",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Tambahkan file app/google-services.json (atau atur GitHub Secret GOOGLE_SERVICES_JSON_B64) lalu build ulang APK agar login Google dan sinkronisasi Cloud Firestore aktif.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudStoreGate(
    userEmail: String,
    userDisplayName: String,
    openOwnerAlertsTrigger: Int,
    viewModel: PosViewModel,
    onSignOutGoogle: () -> Unit
) {
    val activeStoreContext by viewModel.activeStoreContext.collectAsStateWithLifecycle()
    val ownedStores by viewModel.ownedStores.collectAsStateWithLifecycle()
    val acceptedInvites by viewModel.acceptedInvites.collectAsStateWithLifecycle()
    val pendingInvites by viewModel.pendingInvitesForUser.collectAsStateWithLifecycle()
    val isDiscoveryLoading by viewModel.isDiscoveryLoading.collectAsStateWithLifecycle()
    val onboardingBusy by viewModel.onboardingBusy.collectAsStateWithLifecycle()
    val onboardingError by viewModel.onboardingError.collectAsStateWithLifecycle()

    val currentPinProfile by viewModel.currentPinProfile.collectAsStateWithLifecycle()
    val activeCashiers by viewModel.activeCashierPins.collectAsStateWithLifecycle()
    val loginError by viewModel.loginError.collectAsStateWithLifecycle()

    val storeCtx = activeStoreContext
    when {
        storeCtx == null && isDiscoveryLoading && ownedStores.isEmpty() && acceptedInvites.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Menghubungkan ke toko online…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        storeCtx == null -> {
            StoreLinkingScreen(
                userDisplayName = userDisplayName,
                userEmail = userEmail,
                pendingInvites = pendingInvites,
                isBusy = onboardingBusy,
                errorMessage = onboardingError,
                onAcceptInvite = viewModel::acceptStoreInvite,
                onCreateOwnerStore = viewModel::createOwnerStore,
                onSignOutGoogle = onSignOutGoogle
            )
        }
        currentPinProfile == null -> {
            val activeStoreDoc by viewModel.activeStoreDoc.collectAsStateWithLifecycle()
            LoginScreen(
                storeName = storeCtx.storeName,
                storeTypeLabel = activeStoreDoc?.resolvedStoreTypeLabel ?: storeCtx.storeTypeLabel,
                googleEmail = userEmail,
                cloudMemberRole = storeCtx.cloudMemberRole,
                activeCashiers = activeCashiers,
                loginError = loginError,
                onClearError = viewModel::clearLoginError,
                onLogin = viewModel::loginWithPin,
                onSignOutGoogle = onSignOutGoogle
            )
        }
        else -> {
            AuthenticatedPosShell(
                storeContext = storeCtx,
                currentUser = currentPinProfile!!,
                openOwnerAlertsTrigger = openOwnerAlertsTrigger,
                viewModel = viewModel,
                onSignOutGoogle = onSignOutGoogle
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedPosShell(
    storeContext: ActiveStoreContext,
    currentUser: StaffPinProfileDoc,
    openOwnerAlertsTrigger: Int,
    viewModel: PosViewModel,
    onSignOutGoogle: () -> Unit
) {
    val context = LocalContext.current
    val isOwner = currentUser.roleEnum == UserRole.OWNER && storeContext.cloudMemberRole == UserRole.OWNER
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ROUTE_KASIR

    val activeStore by viewModel.activeStoreDoc.collectAsStateWithLifecycle()
    val categories by viewModel.storeCategories.collectAsStateWithLifecycle()
    val products by viewModel.allProducts.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val lastReceipt by viewModel.lastReceipt.collectAsStateWithLifecycle()
    val stockHistories by viewModel.stockHistories.collectAsStateWithLifecycle()
    val allStaffPins by viewModel.allStaffPins.collectAsStateWithLifecycle()
    val storeInvites by viewModel.storeInvites.collectAsStateWithLifecycle()
    val reportState by viewModel.dailyReportState.collectAsStateWithLifecycle()
    val allOwnerTransactions by viewModel.allOwnerTransactions.collectAsStateWithLifecycle()
    val selectedDateKey by viewModel.selectedReportDateKey.collectAsStateWithLifecycle()
    val periodMode by viewModel.reportPeriodMode.collectAsStateWithLifecycle()
    val sevenDayTrend by viewModel.sevenDayTrend.collectAsStateWithLifecycle()
    val cashierTodayTxs by viewModel.cashierTodayTransactions.collectAsStateWithLifecycle()
    val cashierRecapSales by viewModel.cashierRecapSales.collectAsStateWithLifecycle()
    val selectedCashierRecapDateKey by viewModel.selectedCashierRecapDateKey.collectAsStateWithLifecycle()
    val stockAlertState by viewModel.stockAlertState.collectAsStateWithLifecycle()

    LaunchedEffect(isOwner, storeContext.storeId) {
        if (isOwner) {
            val prefs = context.getSharedPreferences("um_tazkiyah_prefs", Context.MODE_PRIVATE)
            val key = "cat_bootstrapped_${storeContext.storeId}"
            if (!prefs.getBoolean(key, false)) {
                viewModel.bootstrapDefaultCategoriesIfEmpty {
                    prefs.edit().putBoolean(key, true).apply()
                }
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var showQrScanner by remember { mutableStateOf(false) }
    var showStaffDialog by remember { mutableStateOf(false) }
    var showStockAlertDialog by remember { mutableStateOf(false) }
    var restockTargetFromAlert by remember { mutableStateOf<ProductDoc?>(null) }
    var prefilledSkuForStok by remember { mutableStateOf<String?>(null) }
    var openLowStockFilterTrigger by remember { mutableIntStateOf(0) }
    var openCashierRecapTrigger by remember { mutableIntStateOf(0) }

    var hasNotifPermission by remember {
        mutableStateOf(StockNotificationHelper.hasNotificationPermission(context))
    }
    var hasAutoPromptedNotifPermission by remember { mutableStateOf(false) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted || StockNotificationHelper.hasNotificationPermission(context)
        if (hasNotifPermission && isOwner && stockAlertState.hasAlerts) {
            StockNotificationHelper.notifyLowStockAlerts(
                context = context,
                alerts = stockAlertState.alerts,
                storeName = storeContext.storeName
            )
        }
    }
    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(isOwner) {
        if (isOwner) {
            StockNotificationHelper.ensureChannel(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasNotifPermission &&
                !hasAutoPromptedNotifPermission
            ) {
                hasAutoPromptedNotifPermission = true
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(isOwner, storeContext.storeName) {
        if (!isOwner) return@LaunchedEffect
        viewModel.lowStockNotificationEvent.collect { triggeredAlerts ->
            StockNotificationHelper.notifyLowStockAlerts(
                context = context,
                alerts = triggeredAlerts,
                storeName = storeContext.storeName
            )
        }
    }

    LaunchedEffect(isOwner, stockAlertState.hasAlerts) {
        if (isOwner && !stockAlertState.hasAlerts) {
            StockNotificationHelper.cancelLowStockNotification(context)
        }
    }

    LaunchedEffect(openOwnerAlertsTrigger, isOwner) {
        if (openOwnerAlertsTrigger > 0 && isOwner) {
            navController.navigate(ROUTE_LAPORAN) {
                popUpTo(ROUTE_KASIR) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            viewModel.markStockAlertsRead()
            showStockAlertDialog = true
        }
    }

    val destinations = remember(isOwner) {
        if (isOwner) {
            listOf(
                NavDestination(
                    route = ROUTE_KASIR,
                    label = "Kasir",
                    selectedIcon = Icons.Rounded.PointOfSale,
                    unselectedIcon = Icons.Outlined.PointOfSale
                ),
                NavDestination(
                    route = ROUTE_STOK,
                    label = "Kelola Stok",
                    selectedIcon = Icons.Rounded.Inventory2,
                    unselectedIcon = Icons.Outlined.Inventory2
                ),
                NavDestination(
                    route = ROUTE_LAPORAN,
                    label = "Laporan",
                    selectedIcon = Icons.Rounded.Assessment,
                    unselectedIcon = Icons.Outlined.Assessment
                )
            )
        } else {
            listOf(
                NavDestination(
                    route = ROUTE_KASIR,
                    label = "Kasir",
                    selectedIcon = Icons.Rounded.PointOfSale,
                    unselectedIcon = Icons.Outlined.PointOfSale
                ),
                NavDestination(
                    route = ROUTE_STOK,
                    label = "Cek Stok",
                    selectedIcon = Icons.Rounded.Inventory2,
                    unselectedIcon = Icons.Outlined.Inventory2
                )
            )
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(ROUTE_KASIR) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = storeContext.storeName.ifBlank { stringResource(R.string.app_name) },
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isOwner) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    }
                                ) {
                                    Text(
                                        text = currentUser.roleEnum.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isOwner) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            val resolvedTypeLabel = activeStore?.resolvedStoreTypeLabel ?: storeContext.storeTypeLabel
                            Text(
                                text = "${currentUser.fullName} • $resolvedTypeLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        if (isOwner) {
                            IconButton(
                                onClick = {
                                    viewModel.markStockAlertsRead()
                                    showStockAlertDialog = true
                                },
                                modifier = Modifier.testTag("owner_notifications_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (stockAlertState.unreadCount > 0) {
                                            Badge(containerColor = DangerCrimson) {
                                                Text("${stockAlertState.unreadCount}")
                                            }
                                        } else if (stockAlertState.hasAlerts) {
                                            Badge(containerColor = DangerCrimson)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (stockAlertState.hasAlerts) {
                                            Icons.Rounded.NotificationsActive
                                        } else {
                                            Icons.Outlined.Notifications
                                        },
                                        contentDescription = "Notifikasi stok minimum"
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showStaffDialog = true },
                                modifier = Modifier.testTag("manage_staff_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ManageAccounts,
                                    contentDescription = "Kelola Pegawai & PIN"
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    navigateTo(ROUTE_KASIR)
                                    openCashierRecapTrigger++
                                },
                                modifier = Modifier.testTag("cashier_topbar_recap_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                                    contentDescription = "Kirim Rekap Transaksi Pegawai"
                                )
                            }
                        }
                        IconButton(
                            onClick = viewModel::lockPinSession,
                            modifier = Modifier.testTag("switch_pin_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LockReset,
                                contentDescription = "Ganti petugas / kunci PIN"
                            )
                        }
                        IconButton(
                            onClick = onSignOutGoogle,
                            modifier = Modifier.testTag("logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Keluar akun Google"
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val badgeCount = if (isOwner && dest.route == ROUTE_STOK) stockAlertState.totalAlertCount else 0
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    if (badgeCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(containerColor = DangerCrimson) {
                                                    Text("$badgeCount")
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    }
                                },
                                label = { Text(dest.label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Spacer(Modifier.height(8.dp))
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            val badgeCount = if (isOwner && dest.route == ROUTE_STOK) stockAlertState.totalAlertCount else 0
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    if (badgeCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(containerColor = DangerCrimson) {
                                                    Text("$badgeCount")
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    }
                                },
                                label = { Text(dest.label) }
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_KASIR,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    composable(ROUTE_KASIR) {
                        KasirScreen(
                            currentUser = currentUser,
                            cloudMemberRole = storeContext.cloudMemberRole,
                            activeStore = activeStore,
                            products = products,
                            categories = categories,
                            cartItems = cartItems,
                            lastReceipt = lastReceipt,
                            cashierTodayTransactions = cashierTodayTxs,
                            cashierRecapSales = cashierRecapSales,
                            selectedCashierRecapDateKey = selectedCashierRecapDateKey,
                            onSelectCashierRecapDate = viewModel::selectCashierRecapDate,
                            externalOpenCashierRecapTrigger = openCashierRecapTrigger,
                            onAddToCart = viewModel::addToCart,
                            onUpdateCartQty = viewModel::updateCartQuantity,
                            onSetCartItemCustomPrice = viewModel::setCartItemCustomPrice,
                            onClearCart = viewModel::clearCart,
                            onOpenQrScanner = { showQrScanner = true },
                            onUpdateStoreQris = viewModel::updateStoreQrisPayload,
                            onCheckout = viewModel::checkout,
                            onDismissReceipt = viewModel::dismissReceipt,
                            onSeedStarterCatalog = viewModel::seedStarterCatalog,
                            onOpenAddProduct = { navigateTo(ROUTE_STOK) },
                            onShowMessage = viewModel::emitToast
                        )
                    }
                    composable(ROUTE_STOK) {
                        StokScreen(
                            storeName = storeContext.storeName,
                            userRole = currentUser.roleEnum,
                            products = products,
                            categories = categories,
                            stockHistories = stockHistories,
                            prefilledSkuRequest = prefilledSkuForStok,
                            onClearPrefilledSkuRequest = { prefilledSkuForStok = null },
                            openLowStockFilterTrigger = openLowStockFilterTrigger,
                            generateNextSku = viewModel::generateNextSkuSuggestion,
                            onSaveProduct = viewModel::saveProduct,
                            onUpdateProductPricing = viewModel::updateProductPricing,
                            onRestockProduct = viewModel::restockProduct,
                            onAdjustStock = viewModel::adjustProductStock,
                            onDeleteProduct = viewModel::deleteProduct,
                            onCreateCategory = viewModel::createCategory,
                            onDeleteCategory = viewModel::deleteCategory,
                            onRestoreDefaultCategories = viewModel::restoreDefaultCategories,
                            onSeedStarterCatalog = viewModel::seedStarterCatalog,
                            onShowMessage = viewModel::emitToast
                        )
                    }
                    if (isOwner) {
                        composable(ROUTE_LAPORAN) {
                            LaporanScreen(
                                storeName = storeContext.storeName,
                                reportState = reportState,
                                allOwnerTransactions = allOwnerTransactions,
                                stockHistories = stockHistories,
                                selectedDateKey = selectedDateKey,
                                periodMode = periodMode,
                                sevenDayTrend = sevenDayTrend,
                                stockAlertState = stockAlertState,
                                totalCatalogProducts = products.size,
                                hasSystemNotificationPermission = hasNotifPermission,
                                onRequestNotificationPermission = requestNotificationPermission,
                                onRestockProduct = viewModel::restockProduct,
                                onOpenLowStockTab = {
                                    openLowStockFilterTrigger++
                                    navigateTo(ROUTE_STOK)
                                },
                                onSelectDateKey = viewModel::selectReportDate,
                                onSelectPeriodMode = viewModel::setReportPeriodMode,
                                onShowMessage = viewModel::emitToast
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQrScanner) {
        PosQrScannerDialog(
            userRole = currentUser.roleEnum,
            onDismiss = { showQrScanner = false },
            onScanCode = { code -> viewModel.handleQrCodeScanned(code) },
            onRequestAddUnregisteredProduct = { unknownCode ->
                showQrScanner = false
                prefilledSkuForStok = unknownCode
                navigateTo(ROUTE_STOK)
            },
            onSwitchToOwnerForAdd = {
                showQrScanner = false
                viewModel.lockPinSession()
            }
        )
    }

    if (showStaffDialog && isOwner) {
        StaffManagementDialog(
            staffPins = allStaffPins,
            storeInvites = storeInvites,
            onDismiss = { showStaffDialog = false },
            onCreateStaffPin = viewModel::createStaffPinAccount,
            onUpdateStaffPin = viewModel::updateStaffPinAccount,
            onTogglePinActive = viewModel::toggleStaffPinActive,
            onInviteCashierEmail = viewModel::inviteCashierByEmail
        )
    }

    if (showStockAlertDialog && isOwner) {
        LowStockAlertCenterDialog(
            alertState = stockAlertState,
            hasNotificationPermission = hasNotifPermission,
            onRequestNotificationPermission = requestNotificationPermission,
            onQuickRestock = { product ->
                showStockAlertDialog = false
                restockTargetFromAlert = product
            },
            onOpenLowStockTab = {
                showStockAlertDialog = false
                openLowStockFilterTrigger++
                navigateTo(ROUTE_STOK)
            },
            onDismiss = { showStockAlertDialog = false }
        )
    }

    restockTargetFromAlert?.let { product ->
        RestockDialog(
            product = product,
            onDismiss = { restockTargetFromAlert = null },
            onConfirm = { addQty, newCost, note ->
                viewModel.restockProduct(product, addQty, newCost, note) {
                    restockTargetFromAlert = null
                }
            }
        )
    }
}
