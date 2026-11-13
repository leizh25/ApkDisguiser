package me.leizh25.apkdisguiser

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShellApkTest {
    @Test
    @Suppress("DEPRECATION")
    fun systemParsesShellSignedWithProjectDebugKey() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = CatalogApp("me.leizh25.shellfixture", "测试空壳")
        val key = ShellApk.signingKey(context)
        val apk = ShellApk.create(context, app, key)
        try {
            val info = context.packageManager.getPackageArchiveInfo(
                apk.absolutePath, android.content.pm.PackageManager.GET_SIGNATURES or
                    android.content.pm.PackageManager.GET_ACTIVITIES
            )
            assertNotNull(info)
            assertEquals(app.id, info!!.packageName)
            assertEquals(ShellApk.activityName, info.activities!!.single().name)
            assertEquals(app.name, info.applicationInfo!!.nonLocalizedLabel.toString())
            assertEquals(android.content.pm.Signature(key.certificate.encoded), info.signatures!!.single())
            apk.copyTo(java.io.File(context.cacheDir, "shell-fixture.apk"), overwrite = true)
        } finally {
            apk.delete()
        }
    }
}
