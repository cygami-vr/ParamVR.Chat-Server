package chat.paramvr.ws.listen

import chat.paramvr.parameter.ParameterType
import chat.paramvr.parameter.ParameterValue
import chat.paramvr.parameter.matches
import chat.paramvr.ws.TriggerConnection
import chat.paramvr.ws.VrcParameter

enum class MatchType {
    GOOD, BAD_NAME, BAD_KEY, BAD_LOV, BAD_LOV_KEY
}
class Parameter(
    private val description: String?, val name: String, val key: String?, val type: Short, val dataType: Short,
    private val defaultValue: String?, private val minValue: String?, private val maxValue: String?,
    var parameterId: Long? = null, var values: List<ParameterValue>? = null) {

    fun copyParam(): Parameter {
        return Parameter(
            description, name, key, type, dataType,
            defaultValue, minValue, maxValue,
            parameterId, values?.toMutableList())
    }

    fun checkKeys(parameterKeys: List<String>, value: Any) : MatchType {

        if (matches(parameterKeys)) {
            values?.let { values ->

                // return if the LOV doesn't define it
                val found = values.find { it.value == value.toString() } ?: return MatchType.BAD_LOV
                return if (found.matches(parameterKeys)) MatchType.GOOD else MatchType.BAD_LOV_KEY
            }
            return MatchType.GOOD
        }
        return MatchType.BAD_KEY
    }

    fun matches(keys: List<String>) = key.isNullOrEmpty() || keys.contains(key)
}

fun List<Parameter>.filterKeys(parameterKeys: List<String>): List<Parameter> {
    var filtered = filter { it.matches(parameterKeys) }
    filtered = filtered.map(Parameter::copyParam)
    filtered.forEach { p ->
        p.values = p.values?.filter {
            it.matches(parameterKeys)
        }
    }
    return filtered
}

fun List<Parameter>.checkKeys(triggerCon: TriggerConnection, param: VrcParameter): MatchType {
    val match = find { it.name == param.name }
    match?.let {

        if (match.key != null && match.key.isNotEmpty()
            && !triggerCon.parameterKeys.contains(match.key)) {

            return MatchType.BAD_KEY
        }

        if (match.type == ParameterType.LOV.id) {

            val value = match.values?.find { it.value == param.value }
            value?.let {

                if (value.key != null && value.key.isNotEmpty()
                    && !triggerCon.parameterKeys.contains(value.key)) {
                    return MatchType.BAD_LOV_KEY
                }

                return MatchType.GOOD
            }
            return MatchType.BAD_LOV
        }
        return MatchType.GOOD
    }
    return MatchType.BAD_NAME
}