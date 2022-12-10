package chat.paramvr.ws.listen

import chat.paramvr.parameter.ParameterValue
import chat.paramvr.parameter.ParameterWithImage
import chat.paramvr.parameter.canView
import chat.paramvr.ws.TriggerConnection

enum class MatchType {
    GOOD, BAD_NAME, BAD_KEY, BAD_LOV, BAD_LOV_KEY, LOCKED_PARAM
}
class Parameter(
    private val description: String?, val name: String, val key: String?, val type: Short, val dataType: Short,
    val defaultValue: String?, val minValue: String?, val maxValue: String?, private val pressValue: String?,
    val saved: String, val lockable: String, var lockKey: String?,
    parameterId: Long? = null, var values: List<ParameterValue>? = null): ParameterWithImage(parameterId) {

    fun copyParam(): Parameter {
        return Parameter(
            description, name, key, type, dataType,
            defaultValue, minValue, maxValue, pressValue,
            saved, lockable, lockKey,
            parameterId, values?.toMutableList())
    }

    fun canModify(parameterKeys: List<String>, uuid: String)
        = matchModify(parameterKeys, uuid) == MatchType.GOOD
    fun matchModify(parameterKeys: List<String>, uuid: String): MatchType {
        val view = matchView(parameterKeys)
        if (view != MatchType.GOOD)
            return view

        if (canView(parameterKeys)) {
            return if (lockKey.isNullOrEmpty() || lockKey == uuid)
                MatchType.GOOD else MatchType.LOCKED_PARAM
        }
        return MatchType.BAD_KEY
    }

    fun canModify(parameterKeys: List<String>, value: Any, uuid: String)
            = matchModify(parameterKeys, value, uuid) == MatchType.GOOD

    fun matchModify(parameterKeys: List<String>, value: Any, uuid: String): MatchType {
        val view = matchView(parameterKeys, value)
        if (view != MatchType.GOOD)
            return view

        return if (lockKey.isNullOrEmpty() || lockKey == uuid)
            MatchType.GOOD else MatchType.LOCKED_PARAM
    }

    fun canView(parameterKeys: List<String>, value: Any)
        = matchView(parameterKeys, value) == MatchType.GOOD

    fun matchView(parameterKeys: List<String>, value: Any) : MatchType {

        if (canView(parameterKeys)) {
            values?.let { values ->

                // return if the LOV doesn't define it
                val found = values.find { it.value == value.toString() } ?: return MatchType.BAD_LOV
                return if (found.canView(parameterKeys)) MatchType.GOOD else MatchType.BAD_LOV_KEY
            }
            return MatchType.GOOD
        }
        return MatchType.BAD_KEY
    }

    fun canView(parameterKeys: List<String>) = matchView(parameterKeys) == MatchType.GOOD

    fun matchView(parameterKeys: List<String>) =
        if (key.isNullOrEmpty() || parameterKeys.contains(key)) MatchType.GOOD else MatchType.BAD_KEY
}

fun List<Parameter>.filterViewable(con: TriggerConnection): List<Parameter> {
    var filtered = filter { it.canView(con.parameterKeys) }
    filtered = filtered.map(Parameter::copyParam)
    filtered.forEach { p ->
        p.values = p.values?.filter { it.canView(con.parameterKeys) }
        if (p.lockKey != con.uuid) {
            p.lockKey = null
        }
    }
    return filtered
}