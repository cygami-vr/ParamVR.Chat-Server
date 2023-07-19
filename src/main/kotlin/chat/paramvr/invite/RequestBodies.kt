package chat.paramvr.invite

data class PostInvite(val url: String?, val avatarId: Long, val expires: Long, val parameters: List<String>?)

data class DeleteInvite(val url: String)