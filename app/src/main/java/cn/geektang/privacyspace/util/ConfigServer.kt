private fun updateConfig(configJson: String) {
        val configFile =
            File("${ConfigConstant.CONFIG_FILE_FOLDER}${ConfigConstant.CONFIG_FILE_JSON}")
        configFile.parentFile?.mkdirs()
        try {
            val configData = JsonHelper.configAdapter().fromJson(configJson)
            if (null != configData) {
                HookMain.updateConfigData(configData)
                configFile.writeText(configJson)
            }
        } catch (e: Exception) {
            XLog.e(e, "Update config error.")
        }
    }

    private fun getPackageUid(packageName: String): Int {
        return try {
            ActivityThread.getPackageManager()
                .getPackageUid(packageName, 0, 0)
        } catch (e: Throwable) {
            XLog.d("ConfigServer (${Binder.getCallingUid()}).getClientUid failed.")
            -1
        }
    }

    private fun tryMigrateOldConfig() {
        val originalFile =
            File("${ConfigConstant.CONFIG_FILE_FOLDER_ORIGINAL}${ConfigConstant.CONFIG_FILE_JSON}")
        val newConfigFile =
            File("${ConfigConstant.CONFIG_FILE_FOLDER}${ConfigConstant.CONFIG_FILE_JSON}")
        if (!newConfigFile.exists()) {
            newConfigFile.parentFile?.mkdirs()
            originalFile.copyTo(newConfigFile)
        }
    }

    private fun Method.checkIsGetUsersMethod(): Boolean {
        if (name != "getUsers") {
            return false
        }
        var isGetUsersMethod = false
        if (parameterCount == 1 && parameterTypes.first() == Boolean::class.javaPrimitiveType) {
            isGetUsersMethod = true
        } else if (parameterCount == 3) {
            isGetUsersMethod = true
            for (parameterType in parameterTypes) {
                if (parameterType != Boolean::class.javaPrimitiveType) {
                    isGetUsersMethod = false
                    break
                }
            }
        }
        return isGetUsersMethod
    }
}
