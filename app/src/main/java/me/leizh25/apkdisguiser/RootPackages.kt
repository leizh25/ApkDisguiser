package me.leizh25.apkdisguiser

import android.os.Process
import java.io.File
import java.util.concurrent.TimeUnit

internal object RootPackages {
    fun available(): Boolean = runCatching { run("id -u").trim() == "0" }.getOrDefault(false)

    fun install(apk: File) {
        checkSuccess(run("pm install --user $user -S ${apk.length()}", apk))
    }

    fun uninstall(id: String) {
        require(packagePattern.matches(id))
        checkSuccess(run("pm uninstall --user $user $id"))
    }

    private val user get() = Process.myUid() / 100000

    private fun checkSuccess(output: String) {
        check(output.lineSequence().any { it.trim() == "Success" }) {
            output.trim().take(200).ifEmpty { "系统未确认操作成功" }
        }
    }

    private fun run(command: String, input: File? = null): String {
        val builder = ProcessBuilder("su", "-c", command).redirectErrorStream(true)
        if (input != null) builder.redirectInput(input)
        val process = builder.start()
        try {
            if (input == null) process.outputStream.close()
            check(process.waitFor(90, TimeUnit.SECONDS)) { "Root 操作超时" }
            return process.inputStream.bufferedReader().use { it.readText() }
        } finally {
            process.destroy()
        }
    }
}
