package chat.paramvr.parameter

import chat.paramvr.prod
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


data class ParameterValue(val description: String?, val value: String, val key: String?, val parameterId: Long? = null)

fun ParameterValue.matches(keys: List<String>) = key.isNullOrEmpty() || keys.contains(key)

class GetParameter(
    private val description: String?, val name: String, private val key: String?, val type: Short, val dataType: Short,
    private val defaultValue: String?, private val minValue: String?, private val maxValue: String?,
    var avatarVrcUuid: String?, var avatarName: String?,
    var parameterId: Long? = null, var values: List<ParameterValue>? = null) {

    // Appears to be unused but is actually marshaled to JSON for the client-side.
    var image = parameterId?.let { getHref(it) }

    companion object {
        fun getDirectory(id: Long): Path = Paths.get("uploads/parameters/$id")

        fun getImage(id: Long): Path? {
            val dir = getDirectory(id)
            if (!Files.exists(dir)) {
                return null
            }

            val first = Files.list(dir).findFirst()
            return if (first.isPresent) first.get() else null
        }
        fun getHref(id: Long): String? {

            val img = getImage(id) ?: return null
            val path = Paths.get("uploads/parameters").relativize(img)
                .toString().replace('\\', '/')

            return if (prod) "/f/parameter/$path" else "http://localhost:8093/f/parameter/$path"
        }
    }
}