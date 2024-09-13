package chat.paramvr.invite

data class Parameter(val id: Long, val name: String)

data class Invite(val id: Long, val url: String, val expires: Long, var parameterIds: List<Long> = mutableListOf())

fun List<Invite>.findId(id: Long?) = if (id == null) null else find { it.id == id }

fun List<Invite>.validForParameter(id: Long?, parameterId: Long?) = findId(id)?.parameterIds?.find { it == parameterId } != null