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
        val apk = ShellApk.create(context, app, key, game = ShellApk.Game.TicTacToe)
        try {
            val info = context.packageManager.getPackageArchiveInfo(
                apk.absolutePath, android.content.pm.PackageManager.GET_SIGNATURES or
                    android.content.pm.PackageManager.GET_ACTIVITIES
            )
            assertNotNull(info)
            assertEquals(app.id, info!!.packageName)
            assertEquals(ShellApk.Game.TicTacToe.className, info.activities!!.single().name)
            assertEquals(app.name, info.applicationInfo!!.nonLocalizedLabel.toString())
            assertEquals(android.content.pm.Signature(key.certificate.encoded), info.signatures!!.single())
            apk.copyTo(java.io.File(context.cacheDir, "shell-fixture.apk"), overwrite = true)
        } finally {
            apk.delete()
        }
    }
    @Test
    fun systemLoadsEmbeddedLauncherIcon() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        val apk = ShellApk.create(context, CatalogApp("me.leizh25.shellfixture", "测试空壳"),
            ShellApk.signingKey(context), bitmap)
        try {
            val manager = context.packageManager
            val info = checkNotNull(manager.getPackageArchiveInfo(apk.absolutePath, 0)).applicationInfo!!
            info.sourceDir = apk.absolutePath
            info.publicSourceDir = apk.absolutePath
            val drawable = manager.getDrawable(info.packageName, info.icon, info)
                as android.graphics.drawable.BitmapDrawable
            assertEquals(android.graphics.Color.MAGENTA, drawable.bitmap.getPixel(0, 0))
        } finally {
            apk.delete()
            bitmap.recycle()
        }
    }
}
