package chat.paramvr.admin

import chat.paramvr.Crypto
import java.nio.file.Files
import java.nio.file.Paths

object Admin {
    private val adminDAO = AdminDAO()

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            println("Missing action argument (user, genJnlpJarElements, signJars)")
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
            "genjnlpjarelements" -> {
                genJnlpJarElements()
            }
            "signjars" -> {
                signJars(args)
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

    private fun getClientLibPath(): String {
        val userHome = System.getProperty("user.home")
        println("User home = $userHome")
        return "$userHome\\IdeaProjects\\vrcparameters-client\\build\\install\\vrcparameters-client\\lib"
    }

    private fun clientLibPaths() = Files.list(Paths.get(getClientLibPath()))

    private fun genJnlpJarElements() {
        clientLibPaths().forEach {
            println("<jar href=\"lib/${it.fileName}\" />")
        }
    }

    private fun signJars(args: Array<String>) {
        if (args.size < 4) {
            println("Missing signJars arguments (JAVA_HOME, Key Store Path, Key Store Password)")
            return
        }

        val lib = getClientLibPath()

        clientLibPaths().forEach {
            val cmd = "\"${args[1]}\\bin\\jarsigner\" $lib\\${it.fileName} *.paramvr.chat -keystore \"${args[2]}\" -storepass ${args[3]}"
            val process = Runtime.getRuntime().exec(cmd)
            val ret = process.waitFor()
            println("Signed ${it.fileName} ret = $ret")
        }
    }
}