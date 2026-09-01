package dk.bandit.skema.data

import java.io.File

actual fun vault(): Vault {
    val home = System.getProperty("user.home") ?: "."
    val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share"
    return FileVault(File(base, "skema"))
}

/** See the note in Vault.android.kt: duplicated rather than hierarchised. */
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
