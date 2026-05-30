package com.lin1118.jiaojinlaile

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.lin1118.jiaojinlaile.ui.theme.交警来了Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            交警来了Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MonitoringScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoringScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isRunning by MonitoringService.isRunning
    val isAlarming by MonitoringService.isAlarming
    var isNotificationAccessGranted by remember { mutableStateOf(isNotificationServiceEnabled(context)) }
    val scrollState = rememberScrollState()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            isNotificationAccessGranted = isNotificationServiceEnabled(context)
        }
    }

    val permissionsToRequest = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            toggleService(context, isRunning)
        } else {
            Toast.makeText(context, "请授予所需权限以启用监控功能", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("交警来了", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isAlarming) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = if (isAlarming) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 状态指示器
            StatusCard(isRunning, isAlarming)

            Spacer(modifier = Modifier.height(24.dp))

            // 警报中的紧急按钮
            if (isAlarming) {
                Button(
                    onClick = { MonitoringService.stopAlarm(context) },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("停止警报", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // 权限警示
            if (!isNotificationAccessGranted) {
                PermissionWarningCard(onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                })
                Spacer(modifier = Modifier.height(24.dp))
            }

            // 功能介绍
            FeatureList()

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(32.dp))

            // 启动按钮
            Button(
                onClick = {
                    if (!isRunning) {
                        val needsPermission = permissionsToRequest.any {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (needsPermission) {
                            launcher.launch(permissionsToRequest.toTypedArray())
                        } else {
                            toggleService(context, isRunning)
                        }
                    } else {
                        toggleService(context, isRunning)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    if (isRunning) "停止监控" else "开始监控",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                text = "建议将应用加入手机省电白名单以保持后台运行",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun StatusCard(isRunning: Boolean, isAlarming: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isAlarming -> MaterialTheme.colorScheme.errorContainer
                isRunning -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = when {
            isAlarming -> androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.error)
            isRunning -> androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            else -> null
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = when {
                    isAlarming -> MaterialTheme.colorScheme.error
                    isRunning -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    imageVector = if (isAlarming) Icons.Default.Warning else if (isRunning) Icons.Default.Refresh else Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when {
                    isAlarming -> "检测到交警提醒"
                    isRunning -> "监控运行中"
                    else -> "监控已停止"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = when {
                    isAlarming -> MaterialTheme.colorScheme.error
                    isRunning -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center
            )
            Text(
                text = when {
                    isAlarming -> "正在为您播放强力警报，请立即处理！"
                    isRunning -> "App正在后台默默守护您"
                    else -> "请点击下方按钮启动监控"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isAlarming) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PermissionWarningCard(onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "缺少通知读取权限", 
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "无法监控《交管12123》的推送通知，请手动开启。", 
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                TextButton(
                    onClick = onOpenSettings,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("立即去开启 >", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun FeatureList() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "监控范围", 
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        FeatureItem(
            icon = Icons.Default.Sms,
            title = "短信监控",
            description = "当短信包含“交警”关键字时触发警报"
        )
        FeatureItem(
            icon = Icons.Default.Notifications,
            title = "12123通知",
            description = "监控交管12123 App发出的所有实时推送"
        )
        FeatureItem(
            icon = Icons.Default.CheckCircle,
            title = "强力提醒",
            description = "自动调至最大音量播放警报铃声"
        )
    }
}

@Composable
fun FeatureItem(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(10.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (!TextUtils.isEmpty(flat)) {
        val names = flat.split(":")
        for (name in names) {
            val cn = ComponentName.unflattenFromString(name)
            if (cn != null && TextUtils.equals(pkgName, cn.packageName)) {
                return true
            }
        }
    }
    return false
}

private fun toggleService(context: Context, isRunning: Boolean) {
    val intent = Intent(context, MonitoringService::class.java)
    if (isRunning) {
        context.stopService(intent)
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MonitoringScreenPreview() {
    交警来了Theme {
        MonitoringScreen()
    }
}
