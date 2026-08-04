package com.identify.sample.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.identify.sample.R
import com.identify.sample.MainViewModel
import com.identify.sdk.IdentifySdk
import com.identify.sdk.SdkConfig
import com.identify.sdk.core.model.SdkLanguage
import com.identify.sdk.ui.standard.StandardUiProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    activity: android.app.Activity,
    viewModel: MainViewModel = viewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isOptionsOpen by viewModel.isOptionsOpen.collectAsState()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsState()
    val isServerSettingsOpen by viewModel.isServerSettingsOpen.collectAsState()
    val isModuleSelectionOpen by viewModel.isModuleSelectionOpen.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val isLanguageMenuOpen by viewModel.isLanguageMenuOpen.collectAsState()
    val isNfcSettingsOpen by viewModel.isNfcSettingsOpen.collectAsState()

    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val startIdentifyProcess = { viewModel.startProcess(activity) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.identify_text_ic),
                            contentDescription = "Identify",
                            modifier = Modifier.height(24.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.isSettingsOpen.value = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, Color.LightGray, CircleShape)
                            .clickable { viewModel.isLanguageMenuOpen.value = true },
                        contentAlignment = Alignment.Center
                    ) {
                        val flagRes = when (currentLanguage) {
                            SdkLanguage.TR -> com.identify.sdk.R.drawable.tr_flag_ic
                            SdkLanguage.EN -> com.identify.sdk.R.drawable.en_flag_ic
                            SdkLanguage.AZ -> com.identify.sdk.R.drawable.az_flag_ic
                            SdkLanguage.DE -> com.identify.sdk.R.drawable.de_flag_ic
                        }
                        Image(
                            painter = painterResource(id = flagRes),
                            contentDescription = currentLanguage.name,
                            modifier = Modifier.size(20.dp)
                        )

                        DropdownMenu(
                            expanded = isLanguageMenuOpen,
                            onDismissRequest = { viewModel.isLanguageMenuOpen.value = false }
                        ) {
                            SdkLanguage.entries.forEach { language ->
                                DropdownMenuItem(
                                    text = { Text(language.name) },
                                    onClick = {
                                        viewModel.setLanguage(language)
                                        viewModel.isLanguageMenuOpen.value = false
                                    },
                                    leadingIcon = {
                                        val itemFlag = when (language) {
                                            SdkLanguage.TR -> com.identify.sdk.R.drawable.tr_flag_ic
                                            SdkLanguage.EN -> com.identify.sdk.R.drawable.en_flag_ic
                                            SdkLanguage.AZ -> com.identify.sdk.R.drawable.az_flag_ic
                                            SdkLanguage.DE -> com.identify.sdk.R.drawable.de_flag_ic
                                        }
                                        Image(painterResource(itemFlag), null, Modifier.size(20.dp))
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = startIdentifyProcess,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Hemen Bağlan", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Build No: v${com.identify.sample.BuildConfig.VERSION_NAME} (${com.identify.sample.BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Başlık Görseli
            Image(
                painter = painterResource(id = R.drawable.identify_mercek_ic),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Identify'a Hoş geldiniz!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Kimlik doğrulama süreci boyunca iyi bir ışığa sahip olmanız, kimliğiniz yanında olması ve tek başınıza olmanız gerekir.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Sekme Seçici
            TabSelector(
                selectedTab = selectedTab,
                onTabSelected = { viewModel.selectedTab.value = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Girdi Alanları
            if (selectedTab == 0) {
                InputField("Adınız", viewModel.name.collectAsState().value, { viewModel.name.value = it }, onImeAction = startIdentifyProcess)
                InputField("Soyadınız", viewModel.surname.collectAsState().value, { viewModel.surname.value = it }, onImeAction = startIdentifyProcess)
                InputField("T.C. Kimlik Numaranız", viewModel.tcId.collectAsState().value, { viewModel.tcId.value = it }, onImeAction = startIdentifyProcess)
                InputField("Kimlik Seri Numarası", viewModel.serialNumber.collectAsState().value, { viewModel.serialNumber.value = it }, onImeAction = startIdentifyProcess)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        InputField("Doğum Tarihi", viewModel.birthDate.collectAsState().value, { viewModel.birthDate.value = it }, onImeAction = startIdentifyProcess)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        InputField("Son Geçerlilik Tarihi", viewModel.expiryDate.collectAsState().value, { viewModel.expiryDate.value = it }, onImeAction = startIdentifyProcess)
                    }
                }
                InputField("Proje Seçimi", viewModel.project.collectAsState().value, { viewModel.project.value = it }, trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) }, onImeAction = startIdentifyProcess)
            } else {
                val lastConfigAvailable by viewModel.lastConfigAvailable.collectAsState()

                InputField("İdentifikasyon ID", viewModel.identId.collectAsState().value, { viewModel.onIdentIdChange(it) }, onImeAction = startIdentifyProcess)

                if (lastConfigAvailable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { viewModel.clearLastConfiguration() }
                        ) {
                            Text("Hafızayı Temizle", fontSize = 11.sp, color = Color.Red.copy(alpha = 0.6f))
                        }

                        Spacer(Modifier.width(8.dp))

                        TextButton(
                            onClick = { viewModel.loadLastConfiguration() }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.History, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Son Konfigürasyonu Uygula", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Genişletilebilir Seçenekler
            TextButton(onClick = { viewModel.isOptionsOpen.value = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Seçenekleri Göster", color = MaterialTheme.colorScheme.primary)
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Alt Sheet'ler
    if (isOptionsOpen) {
        OptionsBottomSheet(viewModel = viewModel, onDismiss = { viewModel.isOptionsOpen.value = false })
    }

    if (isSettingsOpen) {
        SettingsBottomSheet(
            onDismiss = { viewModel.isSettingsOpen.value = false },
            onOpenModuleSelection = {
                viewModel.isSettingsOpen.value = false
                viewModel.isModuleSelectionOpen.value = true
            },
            onOpenServerSettings = {
                viewModel.isSettingsOpen.value = false
                viewModel.isServerSettingsOpen.value = true
            },
            onOpenNfcSettings = {
                viewModel.isSettingsOpen.value = false
                viewModel.isNfcSettingsOpen.value = true
            }
        )
    }

    if (isServerSettingsOpen) {
        ServerSettingsBottomSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.isServerSettingsOpen.value = false }
        )
    }

    if (isModuleSelectionOpen) {
        ModuleSelectionScreen(
            activity = activity,
            viewModel = viewModel,
            onClose = { viewModel.isModuleSelectionOpen.value = false }
        )
    }

    if (isNfcSettingsOpen) {
        NfcSettingsBottomSheet(
            viewModel = viewModel,
            onDismiss = { viewModel.isNfcSettingsOpen.value = false }
        )
    }
}

@Composable
fun TabSelector(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(Color(0xFFF5F6F8), RoundedCornerShape(24.dp))
            .padding(4.dp)
    ) {
        Row(Modifier.fillMaxSize()) {
            val context = LocalContext.current
            TabButton(
                text = "Yeni Müşteri",
                isSelected = selectedTab == 0,
                enabled = true,
                onClick = {
                    android.widget.Toast.makeText(context, "Yapım aşamasında", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            )
            TabButton(
                text = "Ident ID",
                isSelected = selectedTab == 1,
                enabled = true,
                onClick = { onTabSelected(1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TabButton(text: String, isSelected: Boolean, enabled: Boolean = true, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> Color.LightGray
                isSelected -> Color.White
                else -> Color.Gray
            },
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

@Composable
fun InputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    trailingIcon: @Composable (() -> Unit)? = null,
    onImeAction: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(label, color = Color.LightGray, fontSize = 14.sp) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = Color(0xFFF5F6F8),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = Color(0xFFF5F6F8),
            focusedContainerColor = Color(0xFFF5F6F8)
        ),
        trailingIcon = trailingIcon,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onImeAction?.invoke() })
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsBottomSheet(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val useHookDemo by viewModel.useHookDemo.collectAsState()
    val useCustomUiProvider by viewModel.useCustomUiProvider.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Seçenekleri Yönet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OptionSwitch("Temsilci yayını büyük görünsün", true)
            OptionSwitch("İşaret dili seçeneği aktif olsun", false)
            OptionSwitch("Yeni canlılık testi ekranını dene", true)
            OptionSwitch("SSL Pinning", true)
            OptionSwitch(
                label = "Hook Demo (before/after/finished/cancelled/mesaj override — tüm senaryolar)",
                checked = useHookDemo,
                onCheckedChange = { viewModel.toggleHookDemo() }
            )
            OptionSwitch(
                label = "Custom UI Provider Demo (kendi Hazırlık + Selfie ekranımız)",
                checked = useCustomUiProvider,
                onCheckedChange = { viewModel.toggleCustomUiProvider() }
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun OptionSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    onDismiss: () -> Unit,
    onOpenModuleSelection: () -> Unit,
    onOpenServerSettings: () -> Unit,
    onOpenNfcSettings: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Ayarlar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            SettingItem(Icons.Default.Settings, "Modül Seçme Ekranı", onClick = onOpenModuleSelection)
            SettingItem(Icons.Default.BugReport, "Sunucu Ayarları", onClick = onOpenServerSettings)
            SettingItem(Icons.Default.Nfc, "NFC Bağımlılıkları", onClick = onOpenNfcSettings)
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsBottomSheet(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val currentUrl by viewModel.baseUrl.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Sunucu Ayarları", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            Spacer(modifier = Modifier.height(16.dp))

            EnvironmentItem("QA Server", "https://apiqa.identify.com.tr/", currentUrl) {
                viewModel.setEnvironment("QA")
            }
            EnvironmentItem("Live Server", "https://api.identify.com.tr/", currentUrl) {
                viewModel.setEnvironment("LIVE")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun EnvironmentItem(label: String, url: String, currentUrl: String, onClick: () -> Unit) {
    val isSelected = currentUrl == url
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color(0xFFF9FAFB), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(url, color = Color.LightGray, fontSize = 12.sp)
        }
        if (isSelected) {
            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SettingItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.Gray)
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, color = Color.Gray, fontSize = 14.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
    }
}

@Composable
fun ModuleSelectionScreen(activity: android.app.Activity, viewModel: MainViewModel, onClose: () -> Unit) {
    val activeModules by viewModel.activeModules.collectAsState()
    val moduleOrder by viewModel.moduleOrder.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null) }
                Spacer(modifier = Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Modül Seçimi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Modülleri check butonu ile seçip, yanındaki oklar ile sıralayabilirsiniz.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                if (activeModules != null) {
                    TextButton(onClick = { viewModel.resetModules() }) {
                        Text("Sıfırla", color = Color.Red)
                    }
                }
                Button(
                    onClick = {
                        if (!isEditMode) viewModel.ensureCustomMode()
                        viewModel.isEditMode.value = !isEditMode
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(if (isEditMode) Icons.Default.Check else Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isEditMode) "Bitti" else "Düzenle")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = if (activeModules == null) "Aktif Modüller (Web Varsayılanı)" else "Aktif Modüller (Özel Liste)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val lazyListState = rememberLazyListState()
            var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
            var draggingOffset by remember { mutableStateOf(0f) }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f).pointerInput(isEditMode) {
                    if (!isEditMode) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            lazyListState.layoutInfo.visibleItemsInfo
                                .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
                                ?.let { draggedItemIndex = it.index }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            draggingOffset += dragAmount.y

                            val currentDraggedIndex = draggedItemIndex ?: return@detectDragGesturesAfterLongPress
                            val layoutInfo = lazyListState.layoutInfo
                            val draggedItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentDraggedIndex } ?: return@detectDragGesturesAfterLongPress

                            val middleY = draggedItem.offset + draggedItem.size / 2 + draggingOffset
                            val targetItem = layoutInfo.visibleItemsInfo.find { item ->
                                item.index != currentDraggedIndex && middleY.toInt() in item.offset..(item.offset + item.size)
                            }

                            if (targetItem != null) {
                                viewModel.moveModule(currentDraggedIndex, targetItem.index)
                                draggedItemIndex = targetItem.index
                                draggingOffset = 0f
                            }
                        },
                        onDragEnd = {
                            draggedItemIndex = null
                            draggingOffset = 0f
                        },
                        onDragCancel = {
                            draggedItemIndex = null
                            draggingOffset = 0f
                        }
                    )
                }
            ) {
                itemsIndexed(moduleOrder) { index, module ->
                    val isChecked = activeModules?.contains(module) ?: true
                    val isEnabled = isEditMode
                    val isDragging = draggedItemIndex == index

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .then(
                                if (isDragging) Modifier.offset { IntOffset(0, draggingOffset.roundToInt()) }.shadow(8.dp, RoundedCornerShape(12.dp)).zIndex(1f)
                                else Modifier
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isChecked) Color(0xFFF0F7FF) else Color(0xFFF9FAFB))
                            .clickable(enabled = true) { viewModel.toggleModule(module) }
                            .padding(horizontal = 12.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isEditMode) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = if (isEditMode) { { viewModel.toggleModule(module) } } else null,
                            enabled = isEnabled,
                            colors = CheckboxDefaults.colors(
                                checkedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = module.replace("_", " "),
                            fontSize = 14.sp,
                            color = if (isChecked) Color.Black else Color.Gray,
                            fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.isEditMode.value = false
                    android.widget.Toast.makeText(activity, "Modül listesi güncellendi", android.widget.Toast.LENGTH_SHORT).show()
                    onClose()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Listeyi Kaydet")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Değişiklikler için kaydetmeyi unutmayın aksi halde web modül listesinden devam edilir.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcSettingsBottomSheet(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val savedDocNumber by viewModel.nfcDocumentNumber.collectAsState()
    val savedDob by viewModel.nfcDateOfBirth.collectAsState()
    val savedExpiry by viewModel.nfcDateOfExpiry.collectAsState()

    var documentNumber by remember { mutableStateOf(savedDocNumber) }
    var dateOfBirth by remember { mutableStateOf(savedDob) }
    var dateOfExpiry by remember { mutableStateOf(savedExpiry) }

    var showDobPicker by remember { mutableStateOf(false) }
    var showExpiryPicker by remember { mutableStateOf(false) }

    val hasSavedValues = savedDocNumber.isNotBlank() || savedDob.isNotBlank() || savedExpiry.isNotBlank()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "NFC Bağımlılıkları",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
            }
            Spacer(modifier = Modifier.height(16.dp))

            InputField(
                label = "Seri No (ör: A12B34567)",
                value = documentNumber,
                onValueChange = { documentNumber = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Doğum Tarihi
            OutlinedTextField(
                value = formatYymmddForDisplay(dateOfBirth),
                onValueChange = {},
                readOnly = true,
                enabled = false,
                placeholder = { Text("Doğum Tarihi", color = Color.LightGray, fontSize = 14.sp) },
                trailingIcon = { Icon(Icons.Default.DateRange, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { showDobPicker = true },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = Color(0xFFF5F6F8),
                    disabledContainerColor = Color(0xFFF5F6F8),
                    disabledTextColor = Color.Black,
                    disabledPlaceholderColor = Color.LightGray,
                    disabledTrailingIconColor = Color.Gray
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Son Geçerlilik Tarihi
            OutlinedTextField(
                value = formatYymmddForDisplay(dateOfExpiry),
                onValueChange = {},
                readOnly = true,
                enabled = false,
                placeholder = { Text("Son Geçerlilik Tarihi", color = Color.LightGray, fontSize = 14.sp) },
                trailingIcon = { Icon(Icons.Default.DateRange, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { showExpiryPicker = true },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = Color(0xFFF5F6F8),
                    disabledContainerColor = Color(0xFFF5F6F8),
                    disabledTextColor = Color.Black,
                    disabledPlaceholderColor = Color.LightGray,
                    disabledTrailingIconColor = Color.Gray
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.saveNfcDependency(documentNumber, dateOfBirth, dateOfExpiry)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Kaydet", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            if (hasSavedValues) {
                TextButton(
                    onClick = {
                        viewModel.clearNfcDependency()
                        documentNumber = ""
                        dateOfBirth = ""
                        dateOfExpiry = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Temizle", color = Color.Red)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDobPicker) {
        NfcDatePickerDialog(
            onDismiss = { showDobPicker = false },
            onDateSelected = { yymmdd ->
                dateOfBirth = yymmdd
                showDobPicker = false
            }
        )
    }

    if (showExpiryPicker) {
        NfcDatePickerDialog(
            onDismiss = { showExpiryPicker = false },
            onDateSelected = { yymmdd ->
                dateOfExpiry = yymmdd
                showExpiryPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcDatePickerDialog(onDismiss: () -> Unit, onDateSelected: (String) -> Unit) {
    val datePickerState = rememberDatePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val sdf = SimpleDateFormat("yyMMdd", Locale.getDefault())
                        onDateSelected(sdf.format(Date(millis)))
                    }
                }
            ) {
                Text("Tamam")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

private fun formatYymmddForDisplay(yymmdd: String): String {
    if (yymmdd.length != 6) return yymmdd
    return try {
        val input = SimpleDateFormat("yyMMdd", Locale.getDefault())
        val output = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        output.format(input.parse(yymmdd)!!)
    } catch (_: Exception) {
        yymmdd
    }
}
