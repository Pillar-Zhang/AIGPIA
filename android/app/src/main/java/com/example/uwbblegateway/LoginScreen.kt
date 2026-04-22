package com.example.uwbblegateway

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private const val TAG = "AI-Glasses-Login"

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("login_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val apiService = remember { ApiService.create() }
    
    var username by remember { mutableStateOf(sharedPrefs.getString("saved_username", "") ?: "") }
    var password by remember { mutableStateOf(sharedPrefs.getString("saved_password", "") ?: "") }
    var rememberMe by remember { mutableStateOf(sharedPrefs.getBoolean("remember_me", false)) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Logo
        Icon(
            painter = painterResource(id = android.R.drawable.ic_menu_myplaces),
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "AI 眼镜电力助手",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Text(
            text = "欢迎登录系统",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { 
                username = it 
                errorMessage = null
            },
            label = { Text("账号") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { 
                password = it 
                errorMessage = null
            },
            label = { Text("密码") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    val iconLabel = if (passwordVisible) "隐藏" else "显示"
                    Text(iconLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading
        )

        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = Color.Red, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = rememberMe,
                onCheckedChange = { rememberMe = it },
                enabled = !isLoading
            )
            Text(text = "记住密码", fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (username.isNotBlank() && password.isNotBlank()) {
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            Log.d(TAG, "Attempting login for user: $username")
                            val response = apiService.login(mapOf("username" to username, "password" to password))
                            Log.d(TAG, "Login response: $response")
                            
                            val status = (response["status"] ?: response["code"]) as? Number
                            if (status?.toInt() == 1) {
                                // 登录成功
                                val editor = sharedPrefs.edit()
                                editor.putBoolean("remember_me", rememberMe)
                                if (rememberMe) {
                                    editor.putString("saved_username", username)
                                    editor.putString("saved_password", password)
                                } else {
                                    editor.remove("saved_username")
                                    editor.remove("saved_password")
                                }
                                editor.apply()
                                
                                // 根据最新接口结构解析用户信息
                                // 结构: data -> userInfo -> name
                                val data = response["data"] as? Map<String, Any>
                                val userInfo = data?.get("userInfo") as? Map<String, Any>
                                val displayName = userInfo?.get("name")?.toString() ?: username

                                Log.d(TAG, "Login successful, user: $displayName")
                                onLoginSuccess(displayName)
                            } else {
                                // 登录失败，显示接口返回的错误信息或状态码
                                val msg = response["message"] ?: response["msg"] ?: "未知错误"
                                errorMessage = "登录失败(错误码: $status): $msg"
                                Log.w(TAG, "Login failed with status: $status, message: $msg")
                            }
                        } catch (e: Exception) {
                            errorMessage = "网络连接异常: ${e.message}"
                            Log.e(TAG, "Login exception: ${e.message}")
                        } finally {
                            isLoading = false
                        }
                    }
                } else {
                    errorMessage = "请输入账号和密码"
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("正在登录...")
            } else {
                Text("登录", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
