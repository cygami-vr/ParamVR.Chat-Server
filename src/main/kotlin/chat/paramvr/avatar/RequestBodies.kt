package chat.paramvr.avatar

data class PostAvatar(val id: Long?, val vrcUuid: String, val name: String, val allowChange: String?, val changeRequiresInvite: String?)

data class DeleteAvatar(val id: Long)