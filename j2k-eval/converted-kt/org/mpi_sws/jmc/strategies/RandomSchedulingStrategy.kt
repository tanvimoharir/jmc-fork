package org.mpi_sws.jmc.strategies

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.checker.JmcModelCheckerReport
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException
import org.mpi_sws.jmc.runtime.HaltExecutionException
import org.mpi_sws.jmc.runtime.HaltTaskException
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent
import org.mpi_sws.jmc.runtime.scheduling.*
import org.mpi_sws.jmc.strategies.tracker.TrackActiveTasksStrategy
import org.mpi_sws.jmc.util.FileUtil
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.atomic.AtomicLong

/**
 * A random scheduling strategy that selects the next thread to be scheduled randomly.
 */
open class RandomSchedulingStrategy(seed: Long, private val reportPath: String?) : TrackActiveTasksStrategy(),
    ReplayableSchedulingStrategy {
    protected val random: ExtRandom = ExtRandom(seed)
    protected val randomValueMap: HashMap<Long?, Int> = HashMap()

    protected var curTrace: RandomSchedulingTrace

    /**
     * Constructs a new RandomSchedulingStrategy object.
     *
     * @param seed the seed for the random number generator
     */
    init {
        this.curTrace = RandomSchedulingTrace(seed)
    }

    @Throws(HaltExecutionException::class)
    override fun initIteration(iteration: Int, report: JmcModelCheckerReport) {
        super.initIteration(iteration, report)
        report.replaySeed = random.getSeed()
        LOGGER.debug("Seed for iteration {} is {}", iteration, random.getSeed())
        randomValueMap.clear()

        curTrace = RandomSchedulingTrace(random.getSeed())
    }

    /**
     * Returns the next task to be scheduled. The task is picked randomly from the set of active
     * tasks.
     *
     * @return the next task to be scheduled
     */
    override fun nextTask(): SchedulingChoice<*>? {
        val taskToSchedule: Long
        val activeThreads = activeTasks
        if (activeThreads.isEmpty()) {
            return null
        }
        if (activeThreads.size == 1) {
            taskToSchedule = activeThreads.toTypedArray()[0] as Long
        } else {
            val index = random.nextInt(activeThreads.size)
            taskToSchedule = activeThreads.toTypedArray()[index] as Long
        }
        return makeSchedulingChoice(taskToSchedule)
    }

    protected fun makeSchedulingChoice(taskToSchedule: Long?): SchedulingChoice<*> {
        var choice: SchedulingChoice<*> = SchedulingChoice.Companion.task(taskToSchedule)
        if (randomValueMap.containsKey(taskToSchedule)) {
            val randomValue = randomValueMap.remove(taskToSchedule)!!
            LOGGER.debug("Using cached random value {} for task {}", randomValue, taskToSchedule)
            choice = SchedulingChoice.Companion.task<PrimitiveValue>(taskToSchedule, PrimitiveValue(randomValue))
        }
        curTrace.addChoice(choice)
        return choice
    }

    // Keep track of reactive events that need a return value
    @Throws(HaltTaskException::class, HaltExecutionException::class)
    override fun updateEvent(event: JmcRuntimeEvent) {
        super.updateEvent(event)
        if (event.type == JmcRuntimeEvent.Type.REACTIVE_EVENT_RANDOM_VALUE) {
            val taskId = event.taskId
            val bits = event.getParam<Int>("bits")
            val randomValue = random.next(bits!!)
            randomValueMap[taskId] = randomValue
            LOGGER.debug("Generated random value {} for task {}", randomValue, taskId)
        }
    }

    @Throws(JmcCheckerException::class)
    override fun recordTrace() {
        val seedFilePath = Paths.get(this.reportPath, "replay_seed.txt").toString()
        val traceFilePath = Paths.get(this.reportPath, "replay_trace.json").toString()
        FileUtil.unsafeStoreToFile(seedFilePath, curTrace.seed.toString() + "\n")
        FileUtil.storeTaskSchedule(traceFilePath, curTrace.getChoices())
    }

    @Throws(JmcCheckerException::class)
    override fun replayRecordedTrace() {
        // TODO: complete this
    }

    /*
     * ExtRandom is a custom random number generator that exposes the seed and mimics the behavior
     * of the default Random class.
     */
    protected class ExtRandom(seed: Long) : Random(seed) {
        private var seed: AtomicLong? = null

        @Synchronized
        override fun setSeed(seed: Long) {
            super.setSeed(seed)
            this.seed = AtomicLong(initialScramble(seed))
        }

        fun getSeed(): Long {
            return seed!!.get()
        }

        public override fun next(bits: Int): Int {
            val orig = super.next(bits)
            var oldseed: Long
            var nextseed: Long
            val seed = this.seed
            do {
                oldseed = seed!!.get()
                nextseed = (oldseed * multiplier + addend) and mask
            } while (!seed!!.compareAndSet(oldseed, nextseed))
            val computed = (nextseed ushr (48 - bits)).toInt()
            if (computed != orig) {
                LOGGER.error("Random number generation mismatch: {} != {}", computed, orig)
                throw RuntimeException("Random number generation mismatch")
            }
            return orig
        }

        companion object {
            private const val multiplier = 0x5DEECE66DL
            private const val addend = 0xBL
            private const val mask = (1L shl 48) - 1

            private fun initialScramble(seed: Long): Long {
                return (seed xor multiplier) and mask
            }
        }
    }

    protected class RandomSchedulingTrace(val seed: Long) {
        private val choices: MutableList<SchedulingChoice<*>> =
            ArrayList()

        fun addChoice(choice: SchedulingChoice<*>) {
            choices.add(choice)
        }

        fun getChoices(): List<SchedulingChoice<*>> {
            return choices
        }
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            RandomSchedulingStrategy::class.java
        )
    }
}
