package chat.paramvr.ws

import chat.paramvr.invite.validForParameter

class ValidationResult(val valid: Boolean,
                       private val listener: ListenConnection?,
                       private val parameter: Parameter?) {
    fun listener() = listener!!
    fun parameter() = parameter!!
}

class PermissionValidator(private val trigger: TriggerConnection) {

    fun filterViewable(): List<Parameter> {

        val viewable = mutableListOf<Parameter>()

        trigger.listener("filter viewable parameters")?.let{ listener ->

            listener.getParams().filter { canView(it) }.map { it.copyParam() }
            .forEach {  param ->
                param.values = param.values?.filter { canView(param, it.value) }
                if (!param.canModifyLock(trigger.getClientId())) {
                    // Locked by another user. Don't expose the UUID, but still let them view the parameter.
                    param.lockedByClientId = null
                }
                viewable += param
            }
        }

        return viewable
    }

    fun validate(change: ParameterChange): ValidationResult {
        trigger.listener("permit parameter change")?.let { listener ->
            listener.getParam(change)?.let { param ->

                trigger.log("Got parameter. Requires invite = ${param.requiresInvite}," +
                        " Invite ID = ${trigger.getInviteId()}," +
                        " Locked by = ${param.lockedByClientId}," +
                        " Client ID = ${trigger.getClientId()}")

                return ValidationResult(canModify(param), listener, param)
            }
        }
        return ValidationResult(false, null, null)
    }

    fun validate(lock: ParameterLock): ValidationResult {
        trigger.listener("permit parameter lock")?.let { listener ->
            listener.getParam(lock)?.let { param ->

                trigger.log("Got parameter. Requires invite = ${param.requiresInvite}," +
                        " Invite ID = ${trigger.getInviteId()}," +
                        " Locked by = ${param.lockedByClientId}," +
                        " Lockable = ${param.lockable}," +
                        " Client ID = ${trigger.getClientId()}")

                return ValidationResult(canModify(param) && param.lockable == "Y", listener, param)
            }
        }
        return ValidationResult(false, null, null)
    }

    private fun canModify(param: Parameter): Boolean {
        trigger.log("Got parameter. Requires invite = ${param.requiresInvite}," +
                " Invite ID = ${trigger.getInviteId()}," +
                " Locked by = ${param.lockedByClientId}," +
                " Client ID = ${trigger.getClientId()}")

        return canView(param) && param.canModifyLock(trigger.getClientId())
    }

    private fun canModify(param: Parameter, value: Any): Boolean {
        if (!canView(param, value))
            return false

        if (!param.canModifyLock(trigger.getClientId()))
            return false

        val paramVal = param.values?.find { it.value == value }

        return if (paramVal == null) true
        else !paramVal.requiresInvite || trigger.getInviteId() != null
    }

    fun canView(param: Parameter, value: Any) : Boolean {
        if (!canView(param))
            return false

        param.values?.let { vals ->
            return vals.any { it.value == value.toString() }
        }
        return true
    }

    fun canView(param: Parameter): Boolean {
        trigger.getListener()?.let {
            return if (param.requiresInvite) {
                it.invites.validForParameter(trigger.getInviteId(), param.name)
            } else {
                true
            }
        }
        return false
    }
}