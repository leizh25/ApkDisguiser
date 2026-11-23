package me.leizh25.apkdisguiser

import android.content.Context
import com.android.apksig.ApkSigner
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

internal object ShellApk {
    internal enum class Game(val activity: String) {
        TicTacToe("UninstallActivity"), Memory("MemoryActivity"), Merge2048("MergeActivity"),
        Mines("MineActivity"), Puzzle("PuzzleActivity"), Snake("SnakeActivity");

        val className get() = "me.leizh25.apkdisguiser.shell.$activity"
    }
    fun signingKey(context: Context): KeyStore.PrivateKeyEntry {
        val certificate = context.assets.open("shell-cert.der").use {
            java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(it)
        }
        val keySpec = context.assets.open("shell-key.pk8").use {
            java.security.spec.PKCS8EncodedKeySpec(it.readBytes())
        }
        val key = java.security.KeyFactory.getInstance(certificate.publicKey.algorithm).generatePrivate(keySpec)
        return KeyStore.PrivateKeyEntry(key, arrayOf(certificate))
    }

    fun create(context: Context, app: CatalogApp, key: KeyStore.PrivateKeyEntry, icon: android.graphics.Bitmap? = null, game: Game = Game.entries.random()): File {
        require(app.id.length <= 223 && packagePattern.matches(app.id)) { "包名无效" }
        val directory = File(context.cacheDir, "apks").apply { mkdirs() }
        // Some system installers read the content URI after the activity result returns.
        val cutoff = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(1)
        directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
        val unsigned = File.createTempFile("shell-", ".zip", context.cacheDir)
        val signed = File.createTempFile("shell-", ".apk", directory)
        try {
            ZipOutputStream(unsigned.outputStream()).use { zip ->
                if (icon != null) {
                    val table = context.assets.open("shell-icon.arsc").use { it.readBytes() }
                    // First ZIP entry: 30-byte header + 14-byte filename gives aligned resource data.
                    val entry = ZipEntry("resources.arsc").apply {
                        method = ZipEntry.STORED
                        size = table.size.toLong()
                        crc = java.util.zip.CRC32().apply { update(table) }.value
                    }
                    zip.putNextEntry(entry)
                    zip.write(table)
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("res/drawable-nodpi-v4/shell_icon.png"))
                    check(icon.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, zip))
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zip.write(manifest(app.id, app.name.take(32767), icon != null, game, app.versionName))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("classes.dex"))
                context.assets.open("classes.dex").use { it.copyTo(zip) }
                zip.closeEntry()
            }
            val config = ApkSigner.SignerConfig.Builder(
                "SHELL", key.privateKey, key.certificateChain.map { it as X509Certificate }
            ).build()
            ApkSigner.Builder(listOf(config))
                .setInputApk(unsigned)
                .setOutputApk(signed)
                .setMinSdkVersion(24)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(false)
                .build().sign()
            return signed
        } catch (exception: Exception) {
            signed.delete()
            throw exception
        } finally {
            unsigned.delete()
        }
    }

    // Android's binary XML format. Resource-backed attribute names occupy the first string indices.
    internal fun manifest(packageName: String, label: String, hasIcon: Boolean, game: Game, versionName: String = ""): ByteArray {
        val attributes = linkedMapOf(
            "icon" to 0x01010002, "label" to 0x01010001, "name" to 0x01010003, "exported" to 0x01010010,
            "minSdkVersion" to 0x0101020c, "versionCode" to 0x0101021b,
            "versionName" to 0x0101021c, "targetSdkVersion" to 0x01010270
        )
        val ns = "http://schemas.android.com/apk/res/android"
        val strings = (attributes.keys + listOf("android", ns, "manifest", "package",
            packageName, "uses-sdk", "application", label, "activity", game.className, "uses-permission", "android.permission.REQUEST_DELETE_PACKAGES",
            "intent-filter", "action", "android.intent.action.MAIN", "category",
            "android.intent.category.LAUNCHER", versionName)).toList()
        fun index(value: String) = strings.indexOf(value).also { check(it >= 0) }
        val stringData = ByteArrayOutputStream()
        val offsets = strings.map { value ->
            stringData.size().also {
                stringData.write(value.length and 255)
                stringData.write(value.length ushr 8)
                stringData.write(value.toByteArray(Charsets.UTF_16LE))
                stringData.write(0)
                stringData.write(0)
            }
        }
        while (stringData.size() % 4 != 0) stringData.write(0)
        val pool = chunk(1, 28, ints(strings.size, 0, 0, 28 + offsets.size * 4, 0),
            ints(*offsets.toIntArray()) + stringData.toByteArray())
        val resourceMap = chunk(0x180, 8, byteArrayOf(), ints(*attributes.values.toIntArray()))
        fun node(type: Int, body: ByteArray) = chunk(type, 16, ints(1, -1), body)
        fun attr(name: String, type: Int, data: Int) = ints(
            if (name in attributes) index(ns) else -1, index(name),
            if (type == 3) data else -1, (type shl 24) or 8, data
        )
        fun string(name: String, value: String) = attr(name, 3, index(value))
        fun element(name: String, vararg attrs: ByteArray, children: ByteArray = byteArrayOf()): ByteArray {
            val start = ints(-1, index(name), 20 or (20 shl 16), attrs.size, 0)
            return node(0x102, start + attrs.fold(byteArrayOf()) { result, value -> result + value }) +
                children + node(0x103, ints(-1, index(name)))
        }
        val root = element("manifest", string("package", packageName), attr("versionCode", 16, 1), string("versionName", versionName),
            children = element("uses-sdk", attr("minSdkVersion", 16, 24),
                attr("targetSdkVersion", 16, 35)) +
                element("uses-permission", string("name", "android.permission.REQUEST_DELETE_PACKAGES")) +
                element("application", string("label", label),
                    *if (hasIcon) arrayOf(attr("icon", 1, 0x7f010000)) else emptyArray(),
                    children = element("activity", string("name", game.className),
                        attr("exported", 18, -1), children = element("intent-filter",
                            children = element("action", string("name", "android.intent.action.MAIN")) +
                                element("category", string("name", "android.intent.category.LAUNCHER"))))))
        val namespace = ints(index("android"), index(ns))
        return chunk(3, 8, byteArrayOf(),
            pool + resourceMap + node(0x100, namespace) + root + node(0x101, namespace))
    }

    private fun ints(vararg values: Int): ByteArray =
        ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach { putInt(it) }
        }.array()

    private fun chunk(type: Int, headerSize: Int, header: ByteArray, body: ByteArray): ByteArray =
        ints(type or (headerSize shl 16), headerSize + body.size) + header + body
}
