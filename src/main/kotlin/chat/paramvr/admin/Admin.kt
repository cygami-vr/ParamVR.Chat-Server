package chat.paramvr.admin

import chat.paramvr.Crypto

object Admin {
    private val adminDAO = AdminDAO()

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            println("Missing action argument (user)")
            return
        }
        when (args[0].lowercase()) {
            "user" -> {
                if (args.size < 2) {
                    println("Missing user sub-action argument (create, enable, disable, recover)")
                    return
                }
                when (args[1].lowercase()) {
                    "create" -> {
                        createUser(args)
                    }
                    "enable" -> {
                        disenableUser(args, true)
                    }
                    "disable" -> {
                        disenableUser(args, false)
                    }
                    "recover" -> {
                        recoverUser(args)
                    }
                    else -> {
                        println("Unknown user sub-action argument ${args[1]}")
                    }
                }
            }
            else -> {
                println("Unknown action argument ${args[0]}")
            }
        }
    }

    private fun createUser(args: Array<String>) {
        if (args.size < 4) {
            println("Missing create user arguments (UserName, Password)")
            return
        }
        val userName = args[2]
        val password = args[3]

        val salt = Crypto.nextSalt(32)
        val hash = Crypto.hash(password.toCharArray(), salt)
        adminDAO.createUser(userName, salt, hash)
    }

    private fun disenableUser(args: Array<String>, enable: Boolean) {
        if (args.size < 3) {
            println("Missing dis/enable user arguments (UserName)")
            return
        }

        val userName = args[2]
        adminDAO.updateUserFailedLogins(userName, if (enable) 0 else 127)
    }

    private fun recoverUser(args: Array<String>) {
        if (args.size < 4) {
            println("Missing recover user arguments (UserName, Password)")
            return
        }
        val userName = args[2]
        val password = args[3]

        val salt = Crypto.nextSalt(32)
        val hash = Crypto.hash(password.toCharArray(), salt)

        adminDAO.updateUserCredentials(userName, salt, hash)
    }
}