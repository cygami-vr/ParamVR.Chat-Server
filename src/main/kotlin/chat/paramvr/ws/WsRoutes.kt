package chat.paramvr.ws

import chat.paramvr.log
import chat.paramvr.tryPost
import chat.paramvr.ws.Sockets.getListener
import chat.paramvr.ws.Sockets.sessionDAO
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

data class TriggerHandshake(val target: String, val targetType: String, val clientId: String?)
data class Trigger(val sessionId: String, val targetUser: String, val clientId: String, val changeableAvatars: Map<String, String>?)
data class Invite(val id: Long, val targetUser: String)

val dao = TriggerSessionDAO()

fun Route.wsRoutes() {
    tryPost("trigger-connect") {
        val body = call.receive<TriggerHandshake>()
        log("clientId = ${body.clientId} target = ${body.target}")
        val targetUser: String
        var inviteId: Long? = null

        when (body.targetType) {
            "invite" -> {
                val invite = dao.getInvite(body.target)
                if (invite == null) {
                    call.respond((HttpStatusCode.BadRequest))
                    return@tryPost
                }
                targetUser = invite.targetUser
                inviteId = invite.id
            }
            "user" -> {
                val id = dao.getTargetUserId(body.target)
                if (id == null) {
                    call.respond((HttpStatusCode.BadRequest))
                    return@tryPost
                }
                targetUser = body.target
            }
            else -> {
                call.respond(HttpStatusCode.BadRequest)
                return@tryPost
            }
        }

        if (sessionDAO.countSessions(targetUser) >= 25) {
            call.respond(HttpStatusCode.BadRequest)
        }

        val clientId = if (body.clientId.isNullOrEmpty()) {
            UUID.randomUUID().toString()
        } else {
            body.clientId
        }

        val changeableAvatars = getListener(targetUser)?.getChangeableAvas()
        val uuid = dao.insertTriggerSession(clientId, targetUser, inviteId)
        call.respond(Trigger(uuid, targetUser, clientId, changeableAvatars))
    }
}