package cn.geektang.privacyspace.util

import android.content.Context
import android.util.Log
import cn.geektang.privacyspace.bean.ConfigData
import cn.geektang.privacyspace.bean.SystemUserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConfigClient(context: Context) {
    private val packageManager = context.packageManager

    fun serverVersion(): Int {
        return connectServer(ConfigServer.QUERY_SERVER_VERSION)?.toIntOrNull() ?: -1
    }

    fun rebootTheSystem() {
        connectServer(ConfigServer.REBOOT_THE_SYSTEM)
    }

    suspend fun migrateOldConfig() {
        withContext(Dispatchers.IO) {
            connectServer(ConfigServer.MIGRATE_OLD_CONFIG_FILE)
        }
    }

    suspend fun queryConfig(): ConfigData? {
        return withContext(Dispatchers.IO) {
            val configJson = connectServer(ConfigServer.QUERY_CONFIG)
            if (configJson.isNullOrBlank()) {
                return@withContext null
            }
            return@withContext try {
                JsonHelper.configAdapter().fromJson(configJson)
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d("PrivacySpace", "Config is invalid.")
                null
            }
        }
    }

    suspend fun updateConfig(configData: ConfigData) {
        withContext(Dispatchers.IO) {
            val configJson = JsonHelper.configAdapter().toJson(configData)
            connectServer("${ConfigServer.UPDATE_CONFIG}$configJson")
        }
    }

    fun forceStop(packageName: String): Boolean {
        return connectServer("${ConfigServer.FORCE_STOP}$packageName") == ConfigServer.EXEC_SUCCEED
    }

    suspend fun querySystemUserList(): List<SystemUserInfo>? {
        return withContext(Dispatchers.IO) {
            val userListJson = connectServer(ConfigServer.GET_USERS)
            try {
                JsonHelper.systemUserInfoListAdapter().fromJson(userListJson)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun connectServer(methodName: String): String? {
        // On Android 13+ the app-side PackageManager#getInstallerPackageName is
        // served from a local InstallSourceInfo cache and never reaches
        // system_server, so the module's hook on IPackageManager never sees our
        // magic argument. Call IPackageManager directly (the same binder the hook
        // sits on) via ActivityThread.getPackageManager() so the request actually
        // crosses into system_server and hits ConfigServer.
        val direct = try {
            val am = Class.forName("android.app.ActivityThread")
            val ipm = am.getMethod("getPackageManager").invoke(null) ?: return null
            val method = ipm.javaClass.methods.firstOrNull {
                it.name == "getInstallerPackageName" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == String::class.java
            }
            method?.invoke(ipm, methodName) as? String
        } catch (e: Throwable) {
            null
        }
        if (direct != null) {
            return direct
        }
        // Fallback for older platforms where the app-side call still reaches the hook.
        return try {
            packageManager.getInstallerPackageName(methodName)
        } catch (e: Exception) {
            null
        }
    }
}
