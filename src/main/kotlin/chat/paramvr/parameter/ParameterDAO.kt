package chat.paramvr.parameter

import chat.paramvr.dao.DAO
import java.sql.PreparedStatement
import java.sql.Types

class ParameterDAO : DAO() {

    fun validateParameterUserId(userId: Long, parameterId: Long): Boolean {
        connect().use { c ->
            c.prepareStatement("select 1 from parameter where user_id = ? and id = ?").use {
                it.setLong(1, userId)
                it.setLong(2, parameterId)
                return it.executeQuery().next()
            }
        }
    }

    fun userExists(targetUser: String): Boolean {
        connect().use { c ->
            c.prepareStatement("select 1 from user where name = ?").use {
                it.setString(1, targetUser)
                return it.executeQuery().next()
            }
        }
    }

    fun updateListenKey(userId: Long, listenKey: String) {
        connect().use { c ->
            c.prepareStatement("update user set listen_key = ? where id = ?").use {
                it.setString(1, listenKey)
                it.setLong(2, userId)
                it.executeUpdate()
            }
        }
    }

    fun retrieveParameters(userId: Long, avatarVrcUuid: String?): List<chat.paramvr.ws.listen.Parameter> {
        val parameters = mutableListOf<chat.paramvr.ws.listen.Parameter>()
        connect().use { c ->
            c.prepareStatement(
                "select parameter.id, type, description, parameter.name, `key`, data_type, default_value, min_value, max_value, saved" +
                        " from parameter left join avatar on avatar.id = avatar_id" +
                        " where parameter.user_id = ? and (vrc_uuid = ? or vrc_uuid is null) order by `order`"
            ).use {
                it.setLong(1, userId)
                it.setString(2, avatarVrcUuid)
                val rs = it.executeQuery()
                while (rs.next()) {
                    val desc = rs.getString(3)
                    val name = rs.getString(4)
                    val key = rs.getString(5)
                    val type = rs.getShort(2)
                    val dataType = rs.getShort(6)
                    val defaultValue = rs.getString(7)
                    val minValue = rs.getString(8)
                    val maxValue = rs.getString(9)
                    val parameterId = rs.getLong(1)
                    val saved = rs.getString(10)

                    val param = chat.paramvr.ws.listen.Parameter(desc, name, key, type, dataType,
                        defaultValue, minValue, maxValue, saved, parameterId)
                    parameters += param
                }
            }
        }

        parameters.filter { it.type == ParameterType.LOV.id }.forEach {
            it.values = retrieveParameterValues(it.parameterId!!)
        }

        return parameters
    }
    fun retrieveParameters(userId: Long): List<GetParameter> {
        val parameters = mutableListOf<GetParameter>()
        connect().use { c ->
            c.prepareStatement(
                "select parameter.id, vrc_uuid, avatar.name, type, description, parameter.name, `key`, data_type, default_value, min_value, max_value, saved" +
                    " from parameter left join avatar on avatar.id = avatar_id where parameter.user_id = ? order by `order`"
            ).use {
                it.setLong(1, userId)
                val rs = it.executeQuery()
                while (rs.next()) {

                    val desc = rs.getString(5)
                    val name = rs.getString(6)
                    val key = rs.getString(7)
                    val type = rs.getShort(4)
                    val dataType = rs.getShort(8)
                    val defaultValue = rs.getString(9)
                    val minValue = rs.getString(10)
                    val maxValue = rs.getString(11)
                    val vrcUuid = rs.getString(2)
                    val avatarName = rs.getString(3)
                    val parameterId = rs.getLong(1)
                    val saved = rs.getString(12)

                    val param = GetParameter(desc, name, key, type, dataType,
                        defaultValue, minValue, maxValue, saved, vrcUuid, avatarName, parameterId)
                    parameters += param
                }
            }
        }

        parameters.filter { it.type == ParameterType.LOV.id }.forEach {
            it.values = retrieveParameterValues(it.parameterId!!)
        }
        return parameters
    }

    private fun retrieveParameterValues(id: Long): List<ParameterValue> {
        val values = mutableListOf<ParameterValue>()
        connect().use { c ->
            c.prepareStatement("select description, value, `key` from parameter_value where parameter_id = ?").use {
                it.setLong(1, id)
                val rs = it.executeQuery()
                while (rs.next()) {
                    values += ParameterValue(rs.getString(1), rs.getString(2), rs.getString(3))
                }
            }
        }
        return values
    }

    fun deleteParameter(userId: Long, parameterId: Long) {
        connect().use { c ->
            c.prepareStatement("delete from parameter where id = ? and user_id = ?").use {
                it.setLong(1, parameterId)
                it.setLong(2, userId)
                it.executeUpdate()
            }
        }
    }

    fun importParameter(userId: Long, avatarId: Long, param: BasicPostParameter) {
        connect().use { c ->
            var parameterId: Long? = null
            c.prepareStatement("select id from parameter where user_id = ? and avatar_id = ? and name = ?").use {
                it.setLong(1, userId)
                it.setLong(2, avatarId)
                it.setString(3, param.name)
                val rs = it.executeQuery()
                if (rs.next()) {
                    parameterId = rs.getLong(1)
                }
            }
            if (parameterId == null) {
                c.prepareStatement(
                    "insert into parameter(user_id, avatar_id, type, name, data_type) values (?, ?, ?, ?, ?)",
                    PreparedStatement.RETURN_GENERATED_KEYS
                ).use {

                    it.setLong(1, userId)
                    it.setLong(2, avatarId)
                    it.setShort(3, param.type)
                    it.setString(4, param.name)
                    it.setShort(5, param.dataType)
                    it.executeUpdate()

                    val keys = it.generatedKeys
                    keys.next()
                    parameterId = keys.getLong(1)
                }
            }
            if (param.values != null && param.values.isNotEmpty()) {
                c.prepareStatement("insert into parameter_value(parameter_id, value) values (?, ?)").use {
                    param.values.forEach { v ->
                        it.setLong(1, parameterId!!)
                        it.setString(2, v)
                        it.addBatch()
                    }
                    it.executeBatch()
                }
            }
        }
    }

    fun insertParameter(userId: Long, param: PostParameter) {
        connect().use { c ->
            c.prepareStatement(
                "insert into parameter(user_id, avatar_id, type, description, name, `key`, data_type, default_value, min_value, max_value, saved, `order`)" +
                        " values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            ).use {

                it.setLong(1, userId)
                if (param.avatarId == null) {
                    it.setNull(2, Types.BIGINT)
                } else {
                    it.setLong(2, param.avatarId!!)
                }
                it.setShort(3, param.type)
                it.setString(4, param.description)
                it.setString(5, param.name)
                it.setString(6, param.key)
                it.setShort(7, param.dataType)
                it.setString(8, param.defaultValue)
                it.setString(9, param.minValue)
                it.setString(10, param.maxValue)
                it.setString(11, param.saved ?: "Y")
                it.setInt(12, param.order ?: 0)
                it.executeUpdate()
            }
        }
    }

    fun updateParameter(userId: Long, param: PostParameter) {
        connect().use { c ->
            c.prepareStatement(
                "update parameter set type = ?, description = ?, `key` = ?, data_type = ?, default_value = ?, min_value = ?, max_value = ?, saved = ?, `order` = ?" +
                        " where user_id = ? and id = ?"
            ).use {

                it.setShort(1, param.type)
                it.setString(2, param.description)
                it.setString(3, param.key)
                it.setShort(4, param.dataType)
                it.setString(5, param.defaultValue)
                it.setString(6, param.minValue)
                it.setString(7, param.maxValue)
                it.setString(8, param.saved ?: "Y")
                it.setInt(9, param.order ?: 0)
                it.setLong(10, userId)
                it.setLong(11, param.parameterId!!)
                it.executeUpdate()
            }
        }
    }

    fun insertUpdateParameterValue(userId: Long, value: PostParameterValue) {

        if (!validateParameterUserId(userId, value.parameterId))
            return

        connect().use { c ->
            val updated: Boolean
            c.prepareStatement("update parameter_value set description = ?, `key` = ? where parameter_id = ? and value = ?").use {
                it.setString(1, value.description)
                it.setString(2, value.key)
                it.setLong(3, value.parameterId)
                it.setString(4, value.value)
                updated = it.executeUpdate() > 0
            }
            if (!updated) {
                c.prepareStatement("insert into parameter_value(parameter_id, description, value, `key`) values (?, ?, ?, ?)").use {
                    it.setLong(1, value.parameterId)
                    it.setString(2, value.description)
                    it.setString(3, value.value)
                    it.setString(4, value.key)
                    it.executeUpdate()
                }
            }
        }
    }

    fun deleteParameterValue(userId: Long, parameterId: Long, value: String): Boolean {

        if (!validateParameterUserId(userId, parameterId))
            return false

        connect().use { c ->
            c.prepareStatement("delete from parameter_value where parameter_id = ? and value = ?").use {
                it.setLong(1, parameterId)
                it.setString(2, value)
                return it.executeUpdate() > 0
            }
        }
    }

    fun updateParameterOrder(userId: Long, parameterIds: List<Long>) {
        connect().use { c ->
            c.prepareStatement("update parameter set `order` = ? where user_id = ? and id = ?").use {
                var order = 0
                parameterIds.forEach { id ->
                    it.setInt(1, order)
                    it.setLong(2, userId)
                    it.setLong(3, id)
                    it.addBatch()
                    order++
                }
                it.executeBatch()
            }
        }
    }
}