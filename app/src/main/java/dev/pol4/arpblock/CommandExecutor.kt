package com.example.commandexecutor

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

object CommandExecutor {

    /**
     * Executes a given command and returns the process.
     * @param command The command to execute.
     * @param asRoot If true, executes the command with root privileges.
     * @return The process of the executed command.
     */
    @JvmStatic
    fun executeCommand(command: String): Process? {
        return try {
            // Prepare the command with root privileges
            val finalCommand = arrayOf("su", "-c", command)

            // Start the process
            val process = Runtime.getRuntime().exec(finalCommand)

            // Return the process
            process
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
