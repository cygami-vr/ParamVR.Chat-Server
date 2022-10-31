package chat.paramvr.auth

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import io.ktor.util.pipeline.PipelineContext

data class VrcParametersSession(val userId: Long, val userName: String)

fun PipelineContext<Unit, ApplicationCall>.vrcParametersSession(): VrcParametersSession = call.sessions.get()!!
fun PipelineContext<Unit, ApplicationCall>.userId() = vrcParametersSession().userId

data class AuthUser(val id: Long, val name: String, var failedLogins: Int, var salt: ByteArray, var saltedHash: ByteArray, val listenKey: String?)

fun AuthUser.newSession() = VrcParametersSession(id, name)