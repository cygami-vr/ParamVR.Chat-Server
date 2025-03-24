package chat.paramvr.usersettings

import chat.paramvr.dao.DAO

class UserSettingsDAO: DAO() {

    fun retrieveSettings(userId: Long): UserSettings {

        connect().use { c ->
            c.prepareStatement("select avatar_change_cooldown, color_primary from user_settings where user_id = ?").use {
                it.setLong(1, userId)
                val rs = it.executeQuery()
                rs.next()
                return UserSettings(rs.getInt(1), rs.getString(2))
            }
        }
    }

    fun updateSettings(userId: Long, settings: UserSettings) {
        connect().use { c ->
            c.prepareStatement("update user_settings set avatar_change_cooldown = ?, color_primary = ? where user_id = ?").use {
                it.setInt(1, settings.avatarChangeCooldown)
                it.setString(2, settings.colorPrimary)
                it.setLong(3, userId)
                it.executeUpdate()
            }
        }
    }
}