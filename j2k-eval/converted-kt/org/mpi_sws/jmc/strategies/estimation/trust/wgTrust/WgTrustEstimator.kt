package org.mpi_sws.jmc.strategies.estimation.trust.wgTrust

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.strategies.estimation.trust.TrustEstimator
import org.mpi_sws.jmc.strategies.trust.Algo
import org.mpi_sws.jmc.strategies.trust.ExplorationStack
import java.util.*
import java.util.random.RandomGenerator
import java.util.random.RandomGeneratorFactory

class WgTrustEstimator : TrustEstimator() {
    private val FWR_WEIGHT = 3

    private val BWR_WEIGHT = 1

    override fun pickNextOption(
        items: List<ExplorationStack.Item>,
        stack: ExplorationStack?,
        alg: Algo?
    ): ExplorationStack.Item {
        if (!hasBackwardRevisit(items)) {
            // Then everything is a forward revisits, we can pick any of them uniformly
            val index = RandomGeneratorFactory.of<RandomGenerator>("Xoshiro256PlusPlus").create().nextInt(items.size)
            return items[index]
        }

        val weights: MutableList<Int> = ArrayList()
        for (item in items) {
            weights.add(if (item.isBackwardRevisit) BWR_WEIGHT else FWR_WEIGHT)
        }
        val cumulativeWeights = IntArray(items.size)

        var sum = 0
        for (i in weights.indices) {
            sum += weights[i]
            cumulativeWeights[i] = sum
        }
        val totalWeight = sum
        val r = RandomGeneratorFactory.of<RandomGenerator>("Xoshiro256PlusPlus").create().nextInt(totalWeight)
        var index = Arrays.binarySearch(cumulativeWeights, r)
        if (index < 0) index = -index - 1
        return items[index]
    }

    private fun hasBackwardRevisit(items: List<ExplorationStack.Item>): Boolean {
        // Check if there exist a BWR items among the list
        for (item in items) {
            if (item.isBackwardRevisit) {
                return true
            }
        }
        return false
    }

    companion object {
        private val LOGGER: Logger = LogManager.getLogger(
            WgTrustEstimator::class.java
        )
    }
}
