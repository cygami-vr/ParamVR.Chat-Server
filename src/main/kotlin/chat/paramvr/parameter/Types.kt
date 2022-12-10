package chat.paramvr.parameter

import com.google.gson.JsonElement

enum class DataType(val id: Short) {
    INT(1),
    FLOAT(2),
    BOOL(3);

    companion object {
        fun parse(elem: JsonElement, dataTypeId: Short): Any? {
            return when (dataTypeId) {
                INT.id -> elem.asInt
                FLOAT.id -> elem.asFloat
                BOOL.id -> elem.asBoolean
                else -> null
            }
        }
    }
}

enum class ParameterType(val id: Short) {
    LOV(1),     // Int or Float
    TOGGLE(2),  // Boolean only
    SLIDER(3),  // Float only
    BUTTON(4);  // Any
}