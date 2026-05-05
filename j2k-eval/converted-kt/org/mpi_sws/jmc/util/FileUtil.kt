package org.mpi_sws.jmc.util

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoiceValue
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoiceValueFactory
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Utility class for file operations related to storing and reading task schedules.
 *
 *
 * This class provides methods to store task schedules to a file and read them back, as well as
 * utility methods for file and path operations.
 */
object FileUtil {
    private val LOGGER: Logger = LogManager.getLogger(FileUtil::class.java)

    /**
     * Stores the given content to a file at the specified path.
     *
     *
     * This method overwrites the file if it already exists and silently ignores any IOExceptions
     * that may occur during the operation.
     *
     * @param path the path to the file
     * @param content the content to store in the file
     */
    fun unsafeStoreToFile(path: String, content: String) {
        try {
            Files.write(Paths.get(path), content.toByteArray())
        } catch (e: IOException) {
            LOGGER.error("Failed to store content to file: {}", path, e)
        }
    }

    /**
     * Creates a new file at the specified path, deleting it if it already exists.
     *
     *
     * This method returns a [FileOutputStream] for the created file.
     *
     * @param path the path to create the file at
     * @return a [FileOutputStream] for the created file, or null if an error occurs
     */
    fun unsafeCreateFile(path: String): FileOutputStream? {
        try {
            val pPath = Paths.get(path)
            if (Files.exists(pPath)) {
                Files.delete(pPath)
            }
            return FileOutputStream(path)
        } catch (e: IOException) {
            LOGGER.error("Failed to create file at path: {}", path, e)
        }
        return null
    }

    /**
     * Ensure the path exists, creating it if it does not.
     *
     *
     * Deletes the contents of the path if it already exists.
     *
     * @param path the path to ensure
     */
    fun unsafeEnsurePath(path: String) {
        try {
            val pPath = Paths.get(path)
            if (Files.exists(pPath) && Files.isDirectory(pPath)) {
                Files.list(pPath)
                    .forEach { p: Path? ->
                        try {
                            Files.delete(p)
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
            }
            Files.deleteIfExists(pPath)
            Files.createDirectories(pPath)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Stores the task schedule to a file in JSON format.
     *
     *
     * This method serializes the list of [SchedulingChoice] objects into a JSON array and
     * writes it to the specified file path.
     *
     * @param filePath the path to the file where the schedule will be stored
     * @param taskSchedule the list of scheduling choices to store
     * @throws JmcCheckerException if an error occurs while writing to the file
     */
    @Throws(JmcCheckerException::class)
    fun storeTaskSchedule(
        filePath: String, taskSchedule: List<SchedulingChoice<*>>
    ) {
        val schedule = JsonArray()
        for (choice in taskSchedule) {
            val choiceJson = JsonObject()
            choiceJson.addProperty("taskId", choice.taskId)
            choiceJson.addProperty("isBlockTask", choice.isBlockTask)
            choiceJson.addProperty("isBlockExecution", choice.isBlockExecution)
            val value = choice.value
            if (value != null) {
                val valueObject = JsonObject()
                valueObject.addProperty("type", value.type())
                valueObject.add("content", value.toJson())
                choiceJson.add("value", valueObject)
            }
            schedule.add(choiceJson)
        }
        val scheduleJson = JsonObject()
        scheduleJson.add("schedule", schedule)
        try {
            Files.writeString(Paths.get(filePath), scheduleJson.toString())
        } catch (e: IOException) {
            throw JmcCheckerException("Failed to store task schedule to file: $filePath", e)
        }
    }

    /**
     * Reads a task schedule from a JSON file.
     *
     *
     * This method reads the content of the specified file, parses it as JSON, and constructs a
     * list of [SchedulingChoice] objects based on the parsed data.
     *
     * @param filePath the path to the file containing the task schedule
     * @return a list of scheduling choices read from the file
     * @throws JmcCheckerException if an error occurs while reading or parsing the file
     */
    @Throws(JmcCheckerException::class)
    fun readTaskSchedule(filePath: String): MutableList<SchedulingChoice<*>> {
        try {
            val content = Files.readString(Paths.get(filePath))
            val jsonObject = JsonParser.parseString(content).asJsonObject
            val scheduleArray = jsonObject.getAsJsonArray("schedule")
            val out: MutableList<SchedulingChoice<*>> = ArrayList()
            for (i in 0..<scheduleArray.size()) {
                val choiceJson = scheduleArray[i].asJsonObject
                val taskIdJson = choiceJson["taskId"]
                var taskId: Long? = null
                if (!taskIdJson.isJsonNull) {
                    taskId = taskIdJson.asLong
                }
                val isBlockTask = choiceJson["isBlockTask"].asBoolean
                if (isBlockTask) {
                    out.add(SchedulingChoice.Companion.blockTask(taskId))
                    continue
                }
                val isBlockExecution = choiceJson["isBlockExecution"].asBoolean
                if (isBlockExecution) {
                    out.add(SchedulingChoice.Companion.blockExecution())
                    continue
                }
                if (!choiceJson.has("value")) {
                    out.add(SchedulingChoice.Companion.task(taskId))
                    continue
                }
                val valueJson = choiceJson.getAsJsonObject("value")
                val choiceValueType = valueJson["type"].asString
                if (!SchedulingChoiceValueFactory.containsType(choiceValueType)) {
                    throw JmcCheckerException(
                        "No adapter registered for type: $choiceValueType"
                    )
                }
                val value =
                    SchedulingChoiceValueFactory.create(
                        choiceValueType, choiceJson["content"]
                    )
                out.add(SchedulingChoice.Companion.task<SchedulingChoiceValue?>(taskId, value))
            }
            return out
        } catch (e: IOException) {
            throw JmcCheckerException("Failed to read task schedule from file: $filePath", e)
        }
    }
}
