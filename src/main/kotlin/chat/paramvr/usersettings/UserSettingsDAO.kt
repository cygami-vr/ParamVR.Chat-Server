package chat.paramvr.usersettings

import chat.paramvr.dao.DAO

class UserSettingsDAO: DAO() {

    fun retrieveSettings(userId: Long): UserSettings {

        connect().use { c ->
            c.prepareStatement("select avatar_change_cooldown, color_primary, color_secondary, " +
                    "dark_mode_color_primary, dark_mode_color_secondary from user_settings where user_id = ?").use {
                it.setLong(1, userId)
                val rs = it.executeQuery()
                rs.next()
                return UserSettings(rs.getInt(1), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5))
            }
        }
    }

    fun updateSettings(userId: Long, settings: UserSettings) {
        connect().use { c ->
            c.prepareStatement("update user_settings set avatar_change_cooldown = ?, color_primary = ?, " +
                    "color_secondary = ?, dark_mode_color_primary = ?, dark_mode_color_secondary = ? where user_id = ?").use {
                it.setInt(1, settings.avatarChangeCooldown)
                it.setString(2, settings.colorPrimary)
                it.setString(3, settings.colorSecondary)
                it.setString(4, settings.darkModeColorPrimary)
                it.setString(5, settings.darkModeColorSecondary)
                it.setLong(6, userId)
                it.executeUpdate()
            }
        }
    }
}