package chat.paramvr.invite

data class PostInvite(val url: String?, val expires: Long, val parameterIds: List<Long>?)

data class DeleteInvite(val url: String)