package chat.paramvr.ws

import chat.paramvr.invite.InviteDAO
import chat.paramvr.log
import chat.paramvr.ws.Sockets.listeners
import chat.paramvr.ws.Sockets.log
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import io.ktor.util.pipeline.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

object InviteExpirationHandler {

    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val futures = mutableListOf<ScheduledFuture<*>>()
    private val inviteDAO = InviteDAO()

    fun handleListener(session: DefaultWebSocketServerSession, con: ListenConnection) {
        handleListener({ session.log(it) }, con)
    }

    private fun handleListener(logger: (s: String) -> Unit, con: ListenConnection) {

        logger("Scheduling expiration for invites. Target = ${con.targetUser}," +
                " userId = ${con.userId}, # of invites = ${con.invites.size}")

        con.invites.forEach { inv ->

            val remaining = inv.expires - System.currentTimeMillis()
            logger("Scheduling expiration for invite. URL = ${inv.url}, Expires = ${inv.expires}, Delay = $remaining")

            if (inv.expires != -1L) {

                futures += executor.schedule({

                    logger("Expiring invite. URL = ${inv.url}")
                    inviteDAO.deleteInvite(con.userId, inv.url)

                }, remaining, TimeUnit.MILLISECONDS)
            }
        }
    }

    fun refresh(ctx: PipelineContext<Unit, ApplicationCall>) {
        ctx.log("Refreshing invite expiration scheduled commands.")
        futures.forEach { it.cancel(false) }
        listeners.forEach { con ->
            con.invites = inviteDAO.retrieveMinimalInvites(con.userId)
            handleListener({ ctx.log(it) }, con)
        }
    }
}