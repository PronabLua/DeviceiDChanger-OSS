package io.github.pronablua.deviceidchanger

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class AppAbi { Arm64, Unsupported32Bit, Unknown }

data class AppEntry(val packageName: String, val label: String, val uid: Int, val abi: AppAbi, val icon: Bitmap?)

class AppCatalog(private val context: Context) {
    @Suppress("DEPRECATION")
    suspend fun read(): List<AppEntry> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { result ->
                val application = result.activityInfo?.applicationInfo ?: return@mapNotNull null
                if (application.packageName == context.packageName || !application.enabled) return@mapNotNull null
                if (application.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) return@mapNotNull null
                if (application.uid !in 10000..19999 || !Identity.validPackage(application.packageName)) return@mapNotNull null
                val packageInfo = try {
                    packageManager.getPackageInfo(application.packageName, 0)
                } catch (failure: PackageManager.NameNotFoundException) {
                    return@mapNotNull null
                }
                if (packageInfo.sharedUserId != null) return@mapNotNull null
                val libraryPath = application.nativeLibraryDir.orEmpty().lowercase(Locale.ROOT).trimEnd('/')
                val abi = when (libraryPath.substringAfterLast('/')) {
                    "arm64", "arm64-v8a" -> AppAbi.Arm64
                    "arm", "armeabi", "armeabi-v7a", "x86" -> AppAbi.Unsupported32Bit
                    else -> AppAbi.Unknown
                }
                AppEntry(
                    application.packageName,
                    application.loadLabel(packageManager).toString(),
                    application.uid,
                    abi,
                    runCatching { application.loadIcon(packageManager).toBitmap(96, 96) }.getOrNull()
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }
}
