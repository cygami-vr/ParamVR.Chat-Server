package chat.paramvr

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

abstract class Config(private val path: Path) {

    protected val props = Properties()

    init {
        load()
        populate()
        save()
    }

    private fun load() {
        try {
            Files.newInputStream(path).use { props.load(it) }
        } catch (ex: IOException) {
            ex.printStackTrace()
        }
    }

    protected abstract fun populate()

    fun getInt(prop: String) = props.getProperty(prop).toInt()

    fun getBoolean(prop: String) = props.getProperty(prop).toBooleanStrict()

    fun getString(prop: String): String = props.getProperty(prop)

    protected fun populateInt(prop: String, defaultValue: Int)
        = populate(prop, defaultValue.toString()) { it.toIntOrNull() != null }

    protected fun populateBoolean(prop: String, defaultValue: Boolean)
        = populate(prop, defaultValue.toString()) { it.toBooleanStrictOrNull() != null }

    protected fun populateString(prop: String, defaultValue: String) = populate(prop, defaultValue, null)

    protected fun populate(prop: String, defaultValue: String, test: ((prop: String) -> Boolean)?) {
        val obj = props.computeIfAbsent(prop) { defaultValue }
        if (test != null && !test(obj.toString()))
            props.setProperty(prop, defaultValue)
    }

    private fun save() {
        try {
            Files.newOutputStream(path).use { props.store(it, null) }
        } catch (ex: IOException) {
            ex.printStackTrace()
        }
    }
}