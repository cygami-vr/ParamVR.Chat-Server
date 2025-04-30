package chat.paramvr.avatar

import chat.paramvr.dao.DAO

class AvatarDAO: DAO() {

    fun validateAvatarUserId(userId: Long, avatarId: Long): Boolean {
        connect().use { c ->
            c.prepareStatement("select 1 from avatar where id = ? and user_id = ?").use {
                it.setLong(1, avatarId)
                it.setLong(2, userId)
                return it.executeQuery().next()
            }
        }
    }

    fun retrieveAvatars(userId: Long): List<Avatar> {
        val avatars = mutableListOf<Avatar>()
        connect().use { c ->
            c.prepareStatement("select id, vrc_uuid, name, allow_change, change_requires_invite, title from avatar where user_id = ? order by id").use {
                it.setLong(1, userId)
                val rs = it.executeQuery()
                while (rs.next()) {
                    avatars += Avatar(rs.getLong(1), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6))
                }
            }
        }
        return avatars
    }

    fun retrieveAvatar(userId: Long, name: String? = null, vrcUuid: String? = null): Avatar? {
        connect().use { c ->
            c.prepareStatement("select id, ${if (name == null) "name" else "vrc_uuid"}, allow_change, change_requires_invite, title" +
                    " from avatar where user_id = ? and ${if (name == null) "vrc_uuid" else "name"} = ?").use {
                it.setLong(1, userId)
                it.setString(2, name ?: vrcUuid)
                val rs = it.executeQuery()
                if (rs.next()) {
                    return Avatar(rs.getLong(1),
                        vrcUuid ?: rs.getString(2),
                        name ?: rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5))
                }
            }
        }
        return null
    }

    fun insertAvatar(userId: Long, avatar: PostAvatar) {
        connect().use { c ->
            c.prepareStatement("insert into avatar(user_id, vrc_uuid, name) values(?, ?, ?)").use {
                it.setLong(1, userId)
                it.setString(2, avatar.vrcUuid)
                it.setString(3, avatar.name)
                it.executeUpdate()
            }
        }
    }

    fun updateAvatar(userId: Long, avatar: PostAvatar) {
        connect().use { c ->
            c.prepareStatement("update avatar set vrc_uuid = ?, name = ?, allow_change = ?, change_requires_invite = ?, title = ?" +
                    " where id = ? and user_id = ?").use {
                it.setString(1, avatar.vrcUuid)
                it.setString(2, avatar.name)
                it.setString(3, avatar.allowChange ?: "N")
                it.setString(4, avatar.changeRequiresInvite ?: "N")
                it.setString(5, avatar.title)
                it.setLong(6, avatar.id!!)
                it.setLong(7, userId)
                it.executeUpdate()
            }
        }
    }

    fun deleteAvatar(userId: Long, id: Long): Boolean {
        connect().use { c ->
            c.prepareStatement("delete from avatar where id = ? and user_id = ?").use {
                it.setLong(1, id)
                it.setLong(2, userId)
                return it.executeUpdate() > 0
            }
        }
    }
}