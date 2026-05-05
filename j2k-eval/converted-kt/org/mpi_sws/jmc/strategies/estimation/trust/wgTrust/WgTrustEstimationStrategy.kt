package org.mpi_sws.jmc.strategies.estimation.trust.wgTrust

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.mpi_sws.jmc.strategies.estimation.trust.TrustEstimationStrategy
import org.mpi_sws.jmc.util.FileUtil
import java.nio.file.Paths

class WgTrustEstimationStrategy @JvmOverloads constructor(
    randomSeed: Long? = System.nanoTime(),
    policy: SchedulingPolicy? = SchedulingPolicy.FIFO,
    debug: Boolean = false,
    reportPath: String? = "build/test-results/jmc-report"
) :
    TrustEstimationStrategy(randomSeed, policy, debug, reportPath, WgTrustEstimator()) {
    private val LOGGER: Logger = LogManager.getLogger(
        WgTrustEstimationStrategy::class.java
    )

    /**
     *
     */
    override fun saveResults() {
        FileUtil.unsafeStoreToFile(
            Paths.get("build/test-results/jmc-report/", "WgTrustEstimateResult.txt").toString(),
            estimatorCollector.toString()
        )
    }
}
