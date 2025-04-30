package chat.paramvr.usersettings

data class UserSettings(val avatarChangeCooldown: Int, val colorPrimary: String?, val colorSecondary: String?,
                        val darkModeColorPrimary: String?, val darkModeColorSecondary: String?)

fun validateColor(color: String?): Boolean {
    color?.let {
        if (it.isNotEmpty()) {
            val regex = "^[0-9a-fA-F]{6}$".toRegex()
            val matches = regex.matches(it)
            return matches
        }
    }
    return true
}

fun UserSettings.validateColors(): Boolean {
    return validateColor(colorPrimary) && validateColor(colorSecondary)
            && validateColor(darkModeColorPrimary) && validateColor(darkModeColorSecondary)
}