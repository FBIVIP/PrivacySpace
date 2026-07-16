package cn.geektang.privacyspace.hook.impl

import cn.geektang.privacyspace.hook.Hooker
import cn.geektang.privacyspace.util.XLog
import cn.geektang.privacyspace.util.loadClassSafe
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * Framework hook covering Android 11 (API 30) through Android 17 (API 37+).
 *
 * The core package-visibility gate in AOSP is AppsFilter#shouldFilterApplication.
 * Both its declaring class and its parameter list changed across releases:
 *
 *   Android 11-12 (API 30-32):
 *     com.android.server.pm.AppsFilter
 *     shouldFilterApplication(int callingUid, SettingBase callingSetting,
 *                             PackageSetting target, int userId)          // 4 args
 *
 *   Android 13 (API 33):
 *     com.android.server.pm.AppsFilter
 *     shouldFilterApplication(int callingUid, Object callingSetting,
 *                             PackageStateInternal target, int userId)    // 4 args
 *
 *   Android 14-17 (API 34-37):
 *     com.android.server.pm.AppsFilterBase  (AppsFilterImpl extends it)
 *     shouldFilterApplication(Computer snapshot, int callingUid, Object callingSetting,
 *                             PackageStateInternal target, int userId)    // 5 args
 *
 * Pinning exact parameter types breaks on every new release. Instead we locate
 * the method by name on whichever class declares it, and read its arguments
 * positionally from the END of the list, which is stable across all versions:
 *
 *     userId  = last argument
 *     target  = second-to-last argument
 *     calling = third-to-last argument
 */
object FrameworkHookerApi30Impl : XC_MethodHook(), Hooker {
    private lateinit var classLoader: ClassLoader

    // Ordered by preference: the class that DECLARES the method on each Android version.
    private val candidateClassNames = arrayOf(
        "com.android.server.pm.AppsFilterBase", // Android 14+ (declares the method)
        "com.android.server.pm.AppsFilter",     // Android 11-13
        "com.android.server.pm.AppsFilterImpl"  // fallback, in case a ROM moved it here
    )

    private const val METHOD_NAME = "shouldFilterApplication"

    override fun start(classLoader: ClassLoader) {
        this.classLoader = classLoader

        var hookedCount = 0
        for (className in candidateClassNames) {
            val clazz = classLoader.loadClassSafe(className) ?: continue
            for (method in clazz.declaredMethods) {
                if (method.name != METHOD_NAME) {
                    continue
                }
                try {
                    XposedBridge.hookMethod(method, this)
                    hookedCount++
                    XLog.i("Hooked $className#$METHOD_NAME (${method.parameterCount} args)")
                } catch (e: Throwable) {
                    XLog.e(e, "Failed to hook $className#$METHOD_NAME")
                }
            }
            // The method is declared on exactly one class; stop once we've hooked it.
            if (hookedCount > 0) {
                break
            }
        }

        if (hookedCount == 0) {
            XLog.e(
                RuntimeException("$METHOD_NAME not found"),
                "FrameworkHookerApi30Impl: no compatible AppsFilter method on this Android version/ROM."
            )
        }
    }

    override fun afterHookedMethod(param: MethodHookParam) {
        // Already filtered by the system - nothing to add.
        if (param.result == true) {
            return
        }
        val args = param.args ?: return
        val size = args.size
        // Every known signature ends with (..., target, userId): either 4 or 5 args.
        if (size < 4) {
            return
        }

        val userId = args[size - 1] as? Int ?: return
        val targetPackageName = args[size - 2]?.packageName ?: return
        val callingPackageName = args[size - 3]?.packageName ?: return

        val shouldIntercept = HookChecker.shouldIntercept(
            classLoader = classLoader,
            targetPackageName = targetPackageName,
            callingPackageName = callingPackageName,
            userId = userId
        )
        if (shouldIntercept) {
            param.result = true
        }
    }
}
