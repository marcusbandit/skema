package com.marcusrosado.skemafordtu.data

import android.content.Context
import java.io.File

/**
 * Android hands out its storage through a Context, and there is no way to
 * reach one from common code. So the Activity has to pass it in before any
 * of this runs. That is the whole of the platform glue.
 */
object SkemaAndroid {
    @Volatile
    internal var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}

actual fun vault(): Vault {
    val ctx = SkemaAndroid.appContext
        ?: error("Call SkemaAndroid.init(applicationContext) before showing App().")
    return FileVault(File(ctx.filesDir, "skema"))
}

/**
 * Duplicated in jvmMain on purpose. Sharing it needs a custom source-set
 * hierarchy, which is a lot of build machinery to save fifteen lines.
 */
internal class FileVault(private val dir: File) : Vault {
    override val location: String get() = dir.absolutePath

    override fun read(name: String): String? {
        val f = File(dir, name)
        return if (f.isFile) runCatching { f.readText() }.getOrNull() else null
    }

    override fun write(name: String, content: String) {
        dir.mkdirs()
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(content)
        tmp.renameTo(File(dir, name))
    }

    override fun delete(name: String) {
        File(dir, name).delete()
    }
}
