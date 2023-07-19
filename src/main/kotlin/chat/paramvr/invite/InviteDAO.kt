package chat.paramvr.invite

import chat.paramvr.dao.DAO
import java.security.SecureRandom
import java.sql.PreparedStatement

class InviteDAO: DAO() {

    fun retrieveInvites(userId: Long): List<Invite> {
        val invites = mutableListOf<Invite>()

        connect().use { c ->
            c.prepareStatement("select id, url, avatar_id, expires from invite where user_id = ?").use {

                it.setLong(1, userId)
                val rs = it.executeQuery()
                while (rs.next()) {
                    val id = rs.getLong(1)
                    val url = rs.getString(2)
                    val avatarId = rs.getLong(3)
                    val expires = rs.getLong(4)
                    invites.add(Invite(id, url, avatarId, expires))
                }
            }

            invites.forEach { inv ->
                c.prepareStatement("select p.id, p.name from invite i" +
                        " join invite_permission ip on i.id = ip.invite_id" +
                        " join parameter p on p.id = ip.parameter_id" +
                        " where url = ?").use {

                    it.setString(1, inv.url)
                    val params = mutableListOf<Parameter>()
                    val rs = it.executeQuery()
                    while (rs.next()) {
                        params.add(Parameter(rs.getLong(1), rs.getString(2)))
                    }
                    inv.parameters += params

                }
            }
        }

        return invites
    }

    fun insertInvite(userId: Long, invite: PostInvite) {

        val urlBuilder = StringBuilder()
        val rand = SecureRandom()
        repeat(8) {
            val case = rand.nextBoolean()
            val z = (if (case) 'Z' else 'z').code
            val a = (if (case) 'A' else 'a').code
            val c: Char = (rand.nextInt(z - a) + a).toChar()
            urlBuilder.append(c)
        }

        val inviteId: Long

        connect().use { c ->
            c.prepareStatement("insert into invite(url, user_id, avatar_id, expires) values (?, ?, ?, ?)", PreparedStatement.RETURN_GENERATED_KEYS).use {
                it.setString(1, urlBuilder.toString())
                it.setLong(2, userId)
                it.setLong(3, invite.avatarId)
                it.setLong(4, invite.expires)
                it.executeUpdate()
                val keys = it.generatedKeys
                keys.next()
                inviteId = keys.getLong(1)
            }

            insertInvitePermissions(userId, invite, c, inviteId)
        }
    }

    fun updateInvite(userId: Long, invite: PostInvite) {
        connect().use { c ->
            val inviteId: Long
            c.prepareStatement("select id from invite where user_id = ? and url = ?").use {
                it.setLong(1, userId)
                it.setString(2, invite.url)
                val rs = it.executeQuery()
                rs.next()
                inviteId = rs.getLong(1)
            }
            c.prepareStatement("delete from invite_permission where invite_id = ?").use {
                it.setLong(1, inviteId)
                it.executeUpdate()
            }
            insertInvitePermissions(userId, invite, c, inviteId)
        }
    }

    private fun insertInvitePermissions(userId: Long, invite: PostInvite, c: ConnectionWrapper, inviteId: Long) {
        invite.parameters?.let { params ->
            c.prepareStatement("insert into invite_permission(invite_id, parameter_id) values (?, (select id from parameter where name = ? and avatar_id = ? and user_id = ?))").use {
                for (name in params) {
                    it.setLong(1, inviteId)
                    it.setString(2, name)
                    it.setLong(3, invite.avatarId)
                    it.setLong(4, userId)
                    it.addBatch()
                }
                it.executeBatch()
            }
        }
    }

    fun deleteInvite(userId: Long, url: String) {
        connect().use { c ->
            c.prepareStatement("delete from invite where url = ? and user_id = ?").use {
                it.setString(1, url)
                it.setLong(2, userId)
                it.executeUpdate()
            }
        }
    }

    fun lookupInvite(url: String): String? {
        connect().use { c ->
            c.prepareStatement("select name from user join invite on user.id = user_id where url = ?").use {
                it.setString(1, url)
                val rs = it.executeQuery()
                return if (rs.next()) rs.getString(1) else null
            }
        }
    }
}