package chat.paramvr.ws

import chat.paramvr.parameter.ParameterValue
import chat.paramvr.parameter.ParameterWithImage

class Parameter(

    private val description: String?, val name: String,
    val requiresInvite: Boolean, val type: Short,
    val dataType: Short, val defaultValue: String?,
    val minValue: String?, val maxValue: String?,
    private val pressValue: String?, val saved: String,
    val lockable: String, var lockedByClientId: String?,
    parameterId: Long? = null, var values: List<ParameterValue>? = null

): ParameterWithImage(parameterId) {

    fun copyParam(): Parameter {
        return Parameter(
            description, name, requiresInvite, type, dataType,
            defaultValue, minValue, maxValue, pressValue,
            saved, lockable, lockedByClientId,
            parameterId, values?.toMutableList())
    }

    fun canModifyLock(clientId: String) = lockedByClientId == null ||  lockedByClientId == clientId
}