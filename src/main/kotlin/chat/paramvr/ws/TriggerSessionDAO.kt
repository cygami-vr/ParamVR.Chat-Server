package chat.paramvr.ws

import chat.paramvr.dao.DAO
import java.sql.Types
import java.util.*

class TriggerSessionDAO : DAO() {

    fun insertTriggerSession(clientId: String, targetUser: String, inviteId: Long?): String {
        val uuid = UUID.randomUUID().toString()
        connect().use { c ->
            c.prepareStatement("insert into trigger_session(uuid, client_id, target_user, invite_id)" +
                    " values (?, ?, ?, ?)").use {
                it.setString(1, uuid)
                it.setString(2, clientId)
                it.setString(3, targetUser)
                if (inviteId == null) {
                    it.setNull(4, Types.BIGINT)
                } else {
                    it.setLong(4, inviteId)
                }
                it.executeUpdate()
            }
        }
        return uuid
    }

    fun getTargetUserId(targetUser: String): Long? {
        connect().use { c ->
            c.prepareStatement("select id from user where name = ?").use {
                it.setString(1, targetUser)
                val rs = it.executeQuery()
                return if (rs.next()) rs.getLong(1) else null
            }
        }
    }

    fun getInvite(url: String): Invite? {
        connect().use { c ->
            c.prepareStatement("select invite.id, name from user" +
                    " join invite on user.id = user_id and url = ?").use {
                it.setString(1, url)
                val rs = it.executeQuery()
                return if (rs.next()) Invite(rs.getLong(1),
                    rs.getString(2)) else null
            }
        }
    }

    fun countSessions(targetUser: String): Int {
        connect().use { c ->
            c.prepareStatement("select count(1) from trigger_session where target_user = ?").use {
                it.setString(1, targetUser)
                val rs = it.executeQuery()
                rs.next()
                return rs.getInt(1)
            }
        }
    }

    fun retrieveTriggerSession(uuid: String): TriggerSession? {
        connect().use { c ->
            c.prepareStatement("select client_id, target_user, invite_id from trigger_session where uuid = ?").use {
                it.setString(1, uuid)
                val rs = it.executeQuery()
                if (rs.next()) {
                    var inviteId: Long? = rs.getLong(3)
                    if (rs.wasNull()) {
                        inviteId = null
                    }
                    return TriggerSession(uuid, rs.getString(1), rs.getString(2), inviteId)
                } else {
                    return null
                }
            }
        }
    }

    fun deleteTriggerSession(uuid: String) {
        connect().use { c ->
            c.prepareStatement("delete from trigger_session where uuid = ?").use {
                it.setString(1, uuid)
                it.executeUpdate()
            }
        }
    }
}