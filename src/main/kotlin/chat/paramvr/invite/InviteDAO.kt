package chat.paramvr.invite

import chat.paramvr.dao.DAO
import java.security.SecureRandom
import java.sql.PreparedStatement

class InviteDAO: DAO() {

    fun retrieveMinimalInvites(userId: Long): List<Invite> {
        val invites = mutableListOf<Invite>()

        connect().use { c ->
            c.prepareStatement("select id, url, expires from invite where user_id = ?").use {

                it.setLong(1, userId)
                val rs = it.executeQuery()
                while (rs.next()) {
                    val id = rs.getLong(1)
                    val url = rs.getString(2)
                    val expires = rs.getLong(3)
                    invites.add(Invite(id, url, expires))
                }
            }

            invites.forEach { inv ->
                c.prepareStatement("select p.id from invite i" +
                        " join invite_permission ip on i.id = ip.invite_id" +
                        " join parameter p on p.id = ip.parameter_id" +
                        " where url = ?").use {

                    it.setString(1, inv.url)
                    val rs = it.executeQuery()
                    while (rs.next()) {
                        inv.parameterIds += rs.getLong(1)
                    }

                }
                c.prepareStatement("select a.id from invite i" +
                        " join invite_avatar_change iac on i.id = iac.invite_id" +
                        " join avatar a on a.id = iac.avatar_id" +
                        " where url = ?").use {

                    it.setString(1, inv.url)
                    val rs = it.executeQuery()
                    while (rs.next()) {
                        inv.changeableAvatarIds += rs.getLong(1)
                    }
                }
            }
        }

        return invites
    }

    fun retrieveInvites(userId: Long): List<GetInvite> {
        val invites = mutableListOf<GetInvite>()

        connect().use { c ->
            c.prepareStatement("select id, url, expires from invite where user_id = ?").use {

                it.setLong(1, userId)
                val rs = it.executeQuery()
                while (rs.next()) {
                    val id = rs.getLong(1)
                    val url = rs.getString(2)
                    val expires = rs.getLong(3)
                    invites.add(GetInvite(id, url, expires))
                }
            }

            invites.forEach { inv ->
                c.prepareStatement("select a.name, a.id, p.name, p.id from invite i" +
                        " join invite_permission ip on i.id = ip.invite_id" +
                        " join parameter p on p.id = ip.parameter_id" +
                        " join avatar a on a.id = p.avatar_id" +
                        " where url = ?").use {

                    it.setString(1, inv.url)
                    val rs = it.executeQuery()
                    while (rs.next()) {
                        inv.parameters += GetInviteParameter(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getLong(4))
                    }

                }
                c.prepareStatement("select a.name, a.id from invite i" +
                        " join invite_avatar_change iac on i.id = iac.invite_id" +
                        " join avatar a on a.id = iac.avatar_id" +
                        " where url = ?").use {

                    it.setString(1, inv.url)
                    val rs = it.executeQuery()
                    while (rs.next()) {
                        inv.changeableAvatars += GetInviteAvatarChange(rs.getString(1), rs.getLong(2))
                    }
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
            c.prepareStatement("insert into invite(url, user_id, expires) values (?, ?, ?)", PreparedStatement.RETURN_GENERATED_KEYS).use {
                it.setString(1, urlBuilder.toString())
                it.setLong(2, userId)
                it.setLong(3, invite.expires)
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
            c.prepareStatement("delete from invite_avatar_change where invite_id = ?").use {
                it.setLong(1, inviteId)
                it.executeUpdate()
            }
            insertInvitePermissions(userId, invite, c, inviteId)
            insertInviteAvatarChangePermissions(userId, invite, c, inviteId)
        }
    }

    private fun insertInvitePermissions(userId: Long, invite: PostInvite, c: ConnectionWrapper, inviteId: Long) {
        invite.parameters?.let { params ->
            c.prepareStatement("insert into invite_permission(invite_id, parameter_id) values (?, (select id from parameter where id = ? and user_id = ?))").use {
                for (param in params) {
                    it.setLong(1, inviteId)
                    it.setLong(2, param.parameterId)
                    it.setLong(3, userId)
                    it.addBatch()
                }
                it.executeBatch()
            }
        }
    }

    private fun insertInviteAvatarChangePermissions(userId: Long, invite: PostInvite, c: ConnectionWrapper, inviteId: Long) {
        invite.changeableAvatars?.let { avas ->
            c.prepareStatement("insert into invite_avatar_change(invite_id, avatar_id) values (?, (select id from avatar where id = ? and user_id = ?))").use {
                for (ava in avas) {
                    it.setLong(1, inviteId)
                    it.setLong(2, ava.avatarId)
                    it.setLong(3, userId)
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

    fun getEligible(userId: Long): EligibleForInvite {
        connect().use { c ->
            val eligible = EligibleForInvite()
            c.prepareStatement("select a.name, a.id, p.name, p.id from avatar a" +
                    " join parameter p on a.id = p.avatar_id" +
                    " where p.requires_invite = 'Y' and p.user_id = ?").use {
                it.setLong(1, userId)
                val rs = it.executeQuery()
                while (rs.next()) {
                    eligible.parameters += GetInviteParameter(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getLong(4))
                }
            }
            c.prepareStatement("select name, id from avatar" +
                    " where allow_change = 'Y' and change_requires_invite = 'Y' and user_id = ?").use {
                it.setLong(1, userId)
                val rs = it.executeQuery()
                while (rs.next()) {
                    eligible.changeableAvatars += GetInviteAvatarChange(rs.getString(1), rs.getLong(2))
                }
            }
            return eligible
        }
    }
}