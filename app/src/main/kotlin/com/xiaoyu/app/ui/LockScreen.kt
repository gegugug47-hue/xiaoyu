package com.xiaoyu.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * 锁屏页（执行书 5.6.3）：PIN 键盘 + 可选生物识别，解锁后才显示任何聊天内容。
 * 不展示消息预览、不显示会话标题，避免锁屏泄漏隐私。
 */
@Composable
fun LockScreen(
    biometricEnabled: Boolean,
    verifyPin: (String) -> Boolean,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val canUseBiometric = biometricEnabled && activity != null && canAuthenticate(context)

    fun submit() {
        if (pin.isEmpty()) return
        if (verifyPin(pin)) {
            onUnlocked()
        } else {
            error = "PIN 不对，再想想"
            pin = ""
        }
    }

    fun requestBiometric() {
        if (activity == null) return
        runCatching {
            val executor = ContextCompat.getMainExecutor(activity)
            BiometricPrompt.Builder(activity)
                .setTitle("解锁小鱼")
                .setSubtitle("用指纹或面容继续")
                .setNegativeButton("用 PIN", executor) { _, _ -> }
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build()
                .authenticate(
                    CancellationSignal(),
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                            error = null
                            pin = ""
                            onUnlocked()
                        }
                    },
                )
        }
    }

    // 开了生物识别就直接弹一次，省一次点击；失败仍可输 PIN
    LaunchedEffect(canUseBiometric) {
        if (canUseBiometric) requestBiometric()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("小鱼", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(
                "输入 PIN 解锁",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(6) { index ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                if (index < pin.length) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape,
                            )
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                error ?: " ",
                style = MaterialTheme.typography.labelMedium,
                color = if (error == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(18.dp))

            listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "⌫"),
            ).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { key ->
                        KeypadKey(key) {
                            when (key) {
                                "" -> Unit
                                "⌫" -> {
                                    pin = pin.dropLast(1)
                                    error = null
                                }
                                else -> if (pin.length < 6) {
                                    error = null
                                    pin += key
                                    if (pin.length >= 6) submit()
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (canUseBiometric) {
                    TextButton(onClick = { requestBiometric() }) { Text("指纹 / 面容解锁") }
                }
                TextButton(onClick = { submit() }, enabled = pin.length >= 4) { Text("解锁") }
            }
        }
    }
}

@Composable
private fun KeypadKey(label: String, onClick: () -> Unit) {
    if (label.isEmpty()) {
        Spacer(Modifier.size(72.dp))
        return
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = if (label == "⌫") 20.sp else 26.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun canAuthenticate(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    val manager = context.getSystemService(BiometricManager::class.java) ?: return false
    return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        BiometricManager.BIOMETRIC_SUCCESS
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
