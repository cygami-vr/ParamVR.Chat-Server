package chat.paramvr.auth

import chat.paramvr.dao.DAO

class SessionAuthDAO : DAO() {

    fun retrieveUser(name: String? = null, id: Long? = null): AuthUser? {
        connect().use { c ->
            c.prepareStatement("select id, name, failed_logins, salt, salted_hash, listen_key from user" +
                    " where ${if (name == null) "id" else "name"} = ?").use {
                if (name == null) {
                    it.setLong(1, id!!)
                } else {
                    it.setString(1, name)
                }
                val rs = it.executeQuery()
                if (rs.next()) {
                    return AuthUser(rs.getLong(1), rs.getString(2),
                        rs.getInt(3), rs.getBytes(4), rs.getBytes(5), rs.getString(6))
                }
            }
        }
        return null
    }

    fun updateUser(user: AuthUser) {
        connect().use { c ->
            c.prepareStatement("update user set failed_logins = ?, salt = ?, salted_hash = ? where name = ?").use {
                it.setInt(1, user.failedLogins)
                it.setBytes(2, user.salt)
                it.setBytes(3, user.saltedHash)
                it.setString(4, user.name)
                it.executeUpdate()
            }
        }
    }

    fun createQuickAuth(user: AuthUser, authKey: String) {
        connect().use { c ->
            c.prepareStatement("insert into quick_auth(user_id, auth_key) values (?, ?)").use {
                it.setLong(1, user.id)
                it.setString(2, authKey)
                it.executeUpdate()
            }
            // delete all but 3 latest records
            var quickAuthKeys = 0
            c.prepareStatement("select count(1) from quick_auth where user_id = ?").use {
                it.setLong(1, user.id)
                val rs = it.executeQuery()
                if (rs.next()) {
                    quickAuthKeys = rs.getInt(1)
                }
            }
            if (quickAuthKeys > 3) {
                c.prepareStatement("delete from quick_auth where user_id = ? order by date_created asc limit ?").use {
                    it.setLong(1, user.id)
                    it.setInt(2, quickAuthKeys - 3)
                    it.executeUpdate()
                }
            }
        }
    }

    fun retrieveUserForQuickAuth(authKey: String): AuthUser? {
        var id: Long? = null
        connect().use { c ->
            c.prepareStatement("select user_id from quick_auth where auth_key = ?").use {
                it.setString(1, authKey)
                val rs = it.executeQuery()
                if (rs.next()) {
                    id = rs.getLong(1)
                }
            }
        }
        id?.let {
            return retrieveUser(id = id)
        }
        return null
    }
}