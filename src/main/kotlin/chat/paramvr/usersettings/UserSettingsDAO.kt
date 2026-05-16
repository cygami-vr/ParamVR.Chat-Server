package chat.paramvr.usersettings

import chat.paramvr.dao.DAO

class UserSettingsDAO: DAO() {

    fun retrieveSettings(userId: Long): UserSettings {

        connect().use { c ->
            c.prepareStatement("select avatar_change_cooldown, min_eye_height, max_eye_height, color_primary, color_secondary, " +
                    "dark_mode_color_primary, dark_mode_color_secondary from user_settings where user_id = ?").use {
                it.setLong(1, userId)
                val rs = it.executeQuery()
                rs.next()
                return UserSettings(rs.getInt(1), rs.getFloat(2), rs.getFloat(3),
                    rs.getString(4), rs.getString(5),
                    rs.getString(6), rs.getString(7))
            }
        }
    }

    fun updateSettings(userId: Long, settings: UserSettings) {
        connect().use { c ->
            c.prepareStatement("update user_settings set avatar_change_cooldown = ?, min_eye_height = ?, max_eye_height = ?, color_primary = ?, " +
                    "color_secondary = ?, dark_mode_color_primary = ?, dark_mode_color_secondary = ? where user_id = ?").use {
                it.setInt(1, settings.avatarChangeCooldown)
                it.setFloat(2, settings.minEyeHeight)
                it.setFloat(3, settings.maxEyeHeight)
                it.setString(4, settings.colorPrimary)
                it.setString(5, settings.colorSecondary)
                it.setString(6, settings.darkModeColorPrimary)
                it.setString(7, settings.darkModeColorSecondary)
                it.setLong(8, userId)
                it.executeUpdate()
            }
        }
    }
}