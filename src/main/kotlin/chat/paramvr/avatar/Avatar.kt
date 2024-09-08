package chat.paramvr.avatar

import chat.paramvr.conf
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class Avatar(val id: Long, val vrcUuid: String, val name: String) {

    val image = getHref(id)

    companion object {
        fun getDirectory(id: Long): Path = Paths.get("uploads/avatars/$id")

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
            val path = Paths.get("uploads/avatars").relativize(img)
                .toString().replace('\\', '/')

            return if (conf.isProduction()) "/f/avatar/$path"
                else "http://localhost:${conf.getPort()}/f/avatar/$path"
        }
    }
}