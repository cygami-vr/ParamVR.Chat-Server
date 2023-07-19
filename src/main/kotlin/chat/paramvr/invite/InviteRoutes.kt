package chat.paramvr.invite

import chat.paramvr.auth.userId
import chat.paramvr.log
import chat.paramvr.tryDelete
import chat.paramvr.tryGet
import chat.paramvr.tryPost
import chat.paramvr.ws.InviteExpirationHandler
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

val dao = InviteDAO()

fun Route.inviteRoutes() {
    route("invite") {
        tryGet {
            log("")
            val invites = dao.retrieveInvites(userId())
            val getInvites = mutableListOf<GetInvite>()

            invites.forEach { inv ->
                val params = mutableListOf<String>()
                inv.parameters.forEach {
                    params.add(it.name)
                }
                getInvites += GetInvite(inv.url, inv.avatarId, inv.expires, params)
            }

            call.respond(getInvites)
        }
        tryPost {
            val postInvite = call.receive<PostInvite>()
            log("inviteUrl = ${postInvite.url}")
            if (postInvite.url == null) {
                dao.insertInvite(userId(), postInvite)
            } else {
                dao.updateInvite(userId(), postInvite)
            }
            InviteExpirationHandler.refresh(this)
            call.respond(HttpStatusCode.NoContent)
        }
        tryDelete {
            val deleteInvite = call.receive<DeleteInvite>()
            log("inviteUrl = ${deleteInvite.url}")
            dao.deleteInvite(userId(), deleteInvite.url)
            InviteExpirationHandler.refresh(this)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}