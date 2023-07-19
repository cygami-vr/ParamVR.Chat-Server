package chat.paramvr.parameter

data class PostParameter(
    val description: String?, val name: String, val requiresInvite: String?,
    val type: Short, val dataType: Short,
    val defaultValue: String?, val minValue: String?, val maxValue: String?,
    val saved: String?, val lockable: String?,
    val pressValue: String?,
    val order: Int?, var avatarId: Long? = null, var parameterId: Long? = null)
fun PostParameter.requiresInvite() = if (requiresInvite == "Y" || requiresInvite == "true") "Y" else "N"

data class BasicPostParameter(val name: String, val type: Short, val dataType: Short, val values: List<String>?)

data class DeleteParameter(val parameterId: Long)

data class PostParameterValue(val parameterId: Long, val description: String?, val value: String, val requiresInvite: String?)
fun PostParameterValue.requiresInvite() = if (requiresInvite == "Y" || requiresInvite == "true") "Y" else "N"

data class DeleteParameterValue(val parameterId: Long, val value: String)

data class PostParameterOrder(val parameterIds: List<Long>)