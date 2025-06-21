package com.example.watertracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private val CHANNEL_ID = "water_reminder_channel"

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            setupNotifications()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("water_tracker", Context.MODE_PRIVATE)

        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                setupNotifications()
            }
        } else {
            setupNotifications()
        }

        setContent {
            WaterTrackerTheme {
                WaterTrackerApp(sharedPreferences, ::sendNotification)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Напоминания о воде"
            val descriptionText = "Уведомления для напоминания пить воду"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun setupNotifications() {

        Timer().schedule(object : TimerTask() {
            override fun run() {
                sendNotification()
            }
        }, 5000)
    }

    private fun sendNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Время пить воду! 💧")
            .setContentText("Не забудьте выпить стакан воды для здоровья")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED) {
                notify(1, builder.build())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterTrackerApp(
    sharedPreferences: SharedPreferences,
    sendNotification: () -> Unit
) {
    var currentWater by remember {
        mutableStateOf(sharedPreferences.getInt("current_water", 0))
    }
    var dailyGoal by remember {
        mutableStateOf(sharedPreferences.getInt("daily_goal", 2000))
    }
    var selectedTab by remember { mutableStateOf(0) }
    var showGoalDialog by remember { mutableStateOf(false) }

    val waterHistory = remember {
        mutableStateListOf<WaterEntry>().apply {
            loadWaterHistory(sharedPreferences, this)
        }
    }

    LaunchedEffect(currentWater, dailyGoal) {
        sharedPreferences.edit()
            .putInt("current_water", currentWater)
            .putInt("daily_goal", dailyGoal)
            .apply()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Трекер воды",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2196F3),
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { showGoalDialog = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Настройки",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFFF5F5F5)
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Главная") },
                    label = { Text("Главная") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "История") },
                    label = { Text("История") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = "Статистика") },
                    label = { Text("Статистика") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> MainScreen(
                modifier = Modifier.padding(paddingValues),
                currentWater = currentWater,
                dailyGoal = dailyGoal,
                onAddWater = { amount ->
                    currentWater += amount
                    waterHistory.add(0, WaterEntry(amount, System.currentTimeMillis()))
                    saveWaterHistory(sharedPreferences, waterHistory)
                },
                sendNotification = sendNotification
            )
            1 -> HistoryScreen(
                modifier = Modifier.padding(paddingValues),
                waterHistory = waterHistory,
                onRemoveEntry = { entry ->
                    waterHistory.remove(entry)
                    currentWater = maxOf(0, currentWater - entry.amount)
                    saveWaterHistory(sharedPreferences, waterHistory)
                }
            )
            2 -> StatisticsScreen(
                modifier = Modifier.padding(paddingValues),
                waterHistory = waterHistory,
                currentWater = currentWater,
                dailyGoal = dailyGoal
            )
        }
    }

    if (showGoalDialog) {
        GoalSettingsDialog(
            currentGoal = dailyGoal,
            onGoalChanged = { newGoal ->
                dailyGoal = newGoal
                showGoalDialog = false
            },
            onDismiss = { showGoalDialog = false }
        )
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    currentWater: Int,
    dailyGoal: Int,
    onAddWater: (Int) -> Unit,
    sendNotification: () -> Unit
) {
    val progress = (currentWater.toFloat() / dailyGoal).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFE3F2FD),
                        Color(0xFFF8F9FA)
                    )
                )
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 12.dp,
                color = Color(0xFF2196F3),
                trackColor = Color(0xFFE0E0E0)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${currentWater}мл",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
                Text(
                    text = "из ${dailyGoal}мл",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2196F3)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))


        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    progress >= 1f -> Color(0xFF4CAF50)
                    progress >= 0.7f -> Color(0xFF2196F3)
                    else -> Color(0xFFFF9800)
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text = when {
                    progress >= 1f -> "🎉 Отлично! Цель достигнута!"
                    progress >= 0.7f -> "💪 Почти готово! Осталось немного!"
                    progress >= 0.3f -> "👍 Хороший прогресс!"
                    else -> "🚀 Начните свой день с воды!"
                },
                modifier = Modifier.padding(16.dp),
                color = Color.White,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))


        Text(
            text = "Быстрое добавление",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            QuickAddButton(
                amount = 250,
                icon = Icons.Default.LocalDrink,
                label = "Стакан",
                onClick = { onAddWater(250) }
            )
            QuickAddButton(
                amount = 500,
                icon = Icons.Default.Coffee,
                label = "Бутылка",
                onClick = { onAddWater(500) }
            )
            QuickAddButton(
                amount = 1000,
                icon = Icons.Default.Sports,
                label = "Литр",
                onClick = { onAddWater(1000) }
            )
        }

        Spacer(modifier = Modifier.weight(1f))


        Button(
            onClick = sendNotification,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50)
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(Icons.Default.Notifications, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Тестовое напоминание", fontSize = 16.sp)
        }
    }
}

@Composable
fun QuickAddButton(
    amount: Int,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = Color(0xFF2196F3),
            contentColor = Color.White,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "${amount}мл",
            fontSize = 10.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    waterHistory: List<WaterEntry>,
    onRemoveEntry: (WaterEntry) -> Unit
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "История приёма воды",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        items(waterHistory) { entry ->
            HistoryItem(
                entry = entry,
                onRemove = { onRemoveEntry(entry) }
            )
        }

        if (waterHistory.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF5F5F5)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "История пуста",
                            fontSize = 16.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Начните отслеживать потребление воды",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(
    entry: WaterEntry,
    onRemove: () -> Unit
) {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocalDrink,
                contentDescription = null,
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${entry.amount} мл",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatter.format(Date(entry.timestamp)),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = Color(0xFFE57373)
                )
            }
        }
    }
}

@Composable
fun StatisticsScreen(
    modifier: Modifier = Modifier,
    waterHistory: List<WaterEntry>,
    currentWater: Int,
    dailyGoal: Int
) {
    val today = Calendar.getInstance()
    val todayEntries = waterHistory.filter { entry ->
        val entryDate = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
        today.get(Calendar.DAY_OF_YEAR) == entryDate.get(Calendar.DAY_OF_YEAR) &&
                today.get(Calendar.YEAR) == entryDate.get(Calendar.YEAR)
    }

    val weeklyTotal = waterHistory.filter { entry ->
        val entryDate = Calendar.getInstance().apply { timeInMillis = entry.timestamp }
        val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
        entryDate.after(weekAgo)
    }.sumOf { it.amount }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Статистика",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        StatCard(
            title = "Сегодня выпито",
            value = "${currentWater} мл",
            subtitle = "Цель: ${dailyGoal} мл",
            progress = (currentWater.toFloat() / dailyGoal).coerceIn(0f, 1f),
            color = Color(0xFF2196F3)
        )

        StatCard(
            title = "За неделю",
            value = "${weeklyTotal} мл",
            subtitle = "Среднее: ${weeklyTotal / 7} мл/день",
            progress = null,
            color = Color(0xFF4CAF50)
        )

        StatCard(
            title = "Количество приёмов",
            value = "${todayEntries.size}",
            subtitle = "Сегодня",
            progress = null,
            color = Color(0xFFFF9800)
        )

        if (todayEntries.isNotEmpty()) {
            StatCard(
                title = "Средний объём",
                value = "${todayEntries.sumOf { it.amount } / todayEntries.size} мл",
                subtitle = "За приём сегодня",
                progress = null,
                color = Color(0xFF9C27B0)
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    progress: Float?,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = Color.Gray
            )
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color.Gray
            )

            progress?.let {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = it,
                    modifier = Modifier.fillMaxWidth(),
                    color = color,
                    trackColor = Color.Gray.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalSettingsDialog(
    currentGoal: Int,
    onGoalChanged: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var goalText by remember { mutableStateOf(currentGoal.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройка цели") },
        text = {
            Column {
                Text("Установите дневную цель потребления воды (мл):")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = goalText,
                    onValueChange = { goalText = it },
                    label = { Text("Цель в мл") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newGoal = goalText.toIntOrNull()
                    if (newGoal != null && newGoal > 0) {
                        onGoalChanged(newGoal)
                    }
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun WaterTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2196F3),
            secondary = Color(0xFF4CAF50),
            background = Color(0xFFFAFAFA),
            surface = Color.White
        ),
        content = content
    )
}

data class WaterEntry(
    val amount: Int,
    val timestamp: Long
)

fun saveWaterHistory(sharedPreferences: SharedPreferences, history: List<WaterEntry>) {
    val editor = sharedPreferences.edit()
    editor.putInt("history_size", history.size)

    history.forEachIndexed { index, entry ->
        editor.putInt("entry_${index}_amount", entry.amount)
        editor.putLong("entry_${index}_timestamp", entry.timestamp)
    }

    editor.apply()
}

fun loadWaterHistory(sharedPreferences: SharedPreferences, history: MutableList<WaterEntry>) {
    val size = sharedPreferences.getInt("history_size", 0)

    for (i in 0 until size) {
        val amount = sharedPreferences.getInt("entry_${i}_amount", 0)
        val timestamp = sharedPreferences.getLong("entry_${i}_timestamp", 0)

        if (amount > 0 && timestamp > 0) {
            history.add(WaterEntry(amount, timestamp))
        }
    }

    history.sortByDescending { it.timestamp }
}