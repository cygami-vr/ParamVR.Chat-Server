package chat.paramvr.invite

data class Parameter(val id: Long, val name: String)

data class GetInvite(val url: String, val avatarId: Long, val expires: Long, var parameters: List<String> = mutableListOf())

data class Invite(val id: Long, val url: String, val avatarId: Long, val expires: Long, var parameters: List<Parameter> = mutableListOf())

fun List<Invite>.findId(id: Long?) = if (id == null) null else find { it.id == id }

fun List<Invite>.validForParameter(id: Long?, name: String) = findId(id)?.parameters?.find { it.name == name } != null