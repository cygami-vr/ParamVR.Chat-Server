package chat.paramvr.invite

data class PostInviteParameter(val parameterId: Long)

data class PostInviteAvatarChange(val avatarId: Long)

data class PostInvite(val url: String?, val expires: Long, val allowMuteLock: Boolean,
                      val parameters: List<PostInviteParameter>?,
                      val changeableAvatars: List<PostInviteAvatarChange>?)

data class GetInviteParameter(val avatarName: String, val avatarId: Long, val parameterName: String, val parameterId: Long)

data class GetInviteAvatarChange(val avatarName: String, val avatarId: Long)

data class GetInvite(val id: Long, val url: String, val expires: Long, val allowMuteLock: Boolean,
                  var parameters: List<GetInviteParameter> = mutableListOf(),
                  var changeableAvatars: List<GetInviteAvatarChange> = mutableListOf())

data class DeleteInvite(val url: String)

data class EligibleForInvite(
    var parameters: List<GetInviteParameter> = mutableListOf(),
    var changeableAvatars: List<GetInviteAvatarChange> = mutableListOf())