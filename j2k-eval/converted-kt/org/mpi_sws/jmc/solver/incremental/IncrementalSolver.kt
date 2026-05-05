package org.mpi_sws.jmc.solver.incremental

import org.mpi_sws.jmc.api.symbolic.bool.JmcBooleanFormula
import org.mpi_sws.jmc.solver.ProverState
import org.mpi_sws.jmc.solver.SMTSolverTypes
import org.mpi_sws.jmc.solver.SolverResult
import org.mpi_sws.jmc.solver.SymbolicSolver
import org.sosy_lab.java_smt.api.BooleanFormula
import org.sosy_lab.java_smt.api.Model.ValueAssignment
import org.sosy_lab.java_smt.api.ProverEnvironment
import org.sosy_lab.java_smt.api.SolverContext
import org.sosy_lab.java_smt.api.SolverException
import java.math.BigInteger


class IncrementalSolver : SymbolicSolver {
    var prover: ProverEnvironment? = null
    override var proverId: Int = 0
        private set
    private val proverMap: MutableMap<Int, ProverState> = HashMap()

    // Indicates the last logical prover id
    var lastProverId: Int = 0
        private set

    // indicates the number of physical provers created
    private var numOfCreatedProvers = 0

    // Holds the free provers
    private val proverPool = ArrayList<ProverState>()

    constructor() : super() {
        val proverState = createNewProver()
        proverMap[1] = proverState
        setProver(proverState, 1)
    }

    constructor(solverType: SMTSolverTypes) : super(solverType) {
        val proverState = createNewProver()
        proverMap[1] = proverState
        setProver(proverState, 1)
    }

    override fun size(): Int {
        return prover!!.size()
    }

    // TODO :: Put a check for the cases wher both SAT and UNSAT leads to contradiction and throw an exception
    override fun computeNewSymbolicOperation(symbolicFormula: JmcBooleanFormula): SolverResult {
        val startTime = System.nanoTime()
        val concreteEval = symbolicFormula.concreteEvaluation()
        val endTime = System.nanoTime()
        advanceSolverTime(endTime - startTime)
        val symbolicEval = if (concreteEval) {
            disSolveSymbolicFormula(symbolicFormula)
        } else {
            solveSymbolicFormula(symbolicFormula)
        }
        val bothSatUnsat = symbolicEval
        pop()
        if (concreteEval) {
            push(symbolicFormula)
            // solver result is true
            return SolverResult(true, bothSatUnsat)
        } else {
            push(negateFormula(symbolicFormula))
            // solver result is false
            return SolverResult(false, bothSatUnsat)
        }
    }

    override fun computeNewSymAssumeOperation(symbolicOperation: JmcBooleanFormula): Boolean {
        val startTime = System.nanoTime()
        val concreteEval = symbolicOperation.concreteEvaluation()
        val endTime = System.nanoTime()
        advanceSolverTime(endTime - startTime)
        if (concreteEval) {
            push(symbolicOperation)
            // solver result is true
            return true
        } else {
            val symbolicEval = solveSymbolicFormula(symbolicOperation)
            if (symbolicEval) {
                updateModel()
            } else {
                pop()
            }
            // solver result is symbolicEval
            return symbolicEval
        }
    }

    override fun computeGuidedSymAssumeOperation(symbolicOperation: JmcBooleanFormula) {
        val startTime = System.nanoTime()
        val concreteEval = symbolicOperation.concreteEvaluation()
        val endTime = System.nanoTime()
        advanceSolverTime(endTime - startTime)
        if (concreteEval) {
            push(symbolicOperation)
        } else {
            val symbolicEval = solveSymbolicFormula(symbolicOperation)
            if (!symbolicEval) {
                throw RuntimeException("Symbolic formula is unsatisfiable")
            }
            updateModel()
        }
    }

    override fun computeNewSymAssertOperation(symbolicOperation: JmcBooleanFormula?): Boolean {
        val sat = disSolveSymbolicFormula(symbolicOperation!!)
        pop()
        return !sat // solver result is !sat
    }

    override fun solveAndUpdateModel() {
        if (prover!!.size() > 0) {
            try {
                val startTime = System.nanoTime()
                val isUnsat = prover!!.isUnsat
                if (!isUnsat) {
                    model = prover!!.model
                    val endTime = System.nanoTime()
                    advanceSolverTime(endTime - startTime)
                    updateModel()
                } else {
                    throw RuntimeException("[Incremental Solver Message] The formula is unsatisfiable")
                }
            } catch (e: SolverException) {
                throw RuntimeException(e)
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
        }
    }

    override fun pop() {
        val startTime = System.nanoTime()
        prover!!.pop()
        val endTime = System.nanoTime()
        advanceSolverTime(endTime - startTime)
    }

    override fun push() {
        try {
            val startTime = System.nanoTime()
            prover!!.push()
            val endTime = System.nanoTime()
            advanceSolverTime(endTime - startTime)
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
    }

    override fun push(formula: BooleanFormula) {
        try {
            val startTime = System.nanoTime()
            prover!!.push(formula)
            val endTime = System.nanoTime()
            advanceSolverTime(endTime - startTime)
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        }
    }

    override fun solve(formula: BooleanFormula): Boolean {
        try {
            val startTime = System.nanoTime()
            push(formula)
            val isUnsat = prover!!.isUnsat
            if (!isUnsat) {
                model = prover!!.model
                val endTime = System.nanoTime()
                advanceSolverTime(endTime - startTime)
                // The formula is satisfiable
                return true
            } else {
                val endTime = System.nanoTime()
                advanceSolverTime(endTime - startTime)
                // The formula is unsatisfiable
                return false
            }
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        } catch (e: SolverException) {
            throw RuntimeException(e)
        }
    }

    override fun push(operation: JmcBooleanFormula) {
        push(operation.formula)
    }

    override fun createNewProver(): ProverState {
        lastProverId++
        if (proverPool.isEmpty()) {
            numOfCreatedProvers++
            val startTime = System.nanoTime()
            val prover = context!!.newProverEnvironment(SolverContext.ProverOptions.GENERATE_MODELS)
            val endTime = System.nanoTime()
            advanceSolverTime(endTime - startTime)
            return ProverState(prover)
        } else {
            return proverPool.removeAt(0)
        }
    }

    override fun setProver(proverState: ProverState, proverId: Int) {
        this.prover = proverState.prover
        this.proverId = proverId
        this.symBoolVariableMap = proverState.symBoolVariableMap
        this.symIntVariableMap = proverState.symIntVariableMap
        this.symArrayVariableMap = proverState.symArrayVariableHashMap
    }

    override fun resetProver(prover: ProverEnvironment) {
        val startTime = System.nanoTime()
        while (prover.size() > 0) {
            prover.pop()
        }
        val endTime = System.nanoTime()
        advanceSolverTime(endTime - startTime)
    }

    private fun updateModel() {
        if (model != null) {
            val startTime = System.nanoTime()
            model!!.iterator().forEachRemaining { entry: ValueAssignment ->
                // The key is a string like className@address. extract the class Name
                val symbolicType =
                    entry.key.toString().split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                if (symbolicType == "SymbolicBoolean") {
                    symBoolVariableMap[entry.key.toString()].setValue(entry.value as Boolean)
                } else if (symbolicType == "SymbolicInteger") {
                    if (entry.value is BigInteger) {
                        symIntVariableMap[entry.key.toString()]
                            .setValue((entry.value as BigInteger).toInt())
                    } else {
                        symIntVariableMap[entry.key.toString()].setValue(entry.value as Int)
                    }
                } else {
                    throw RuntimeException("Unknown Symbolic Type")
                }
            }
            val endTime = System.nanoTime()
            advanceSolverTime(endTime - startTime)
        }
    }

    fun updateProver(id: Int) {
        if (id == 0) {
            throw RuntimeException("Cannot update prover with zero id")
        }

        if (proverId != id) {
            val p = proverMap[id] ?: throw RuntimeException("Prover with id $id does not exist")

            setProver(p, id)
        }
    }

    fun restrictSolverStack(levels: Int) {
        var levels = levels
        while (levels > 0) {
            pop()
            levels--
        }
    }

    fun updateWithCurrentProver(p: ProverState) {
        for ((key, value) in symIntVariableMap) {
            p.symIntVariableMap[key] = value.clone()
        }

        for ((key, value) in symBoolVariableMap) {
            p.symBoolVariableMap[key] = value.clone()
        }

        for ((key, value) in symArrayVariableMap) {
            p.symArrayVariableHashMap[key] = value.clone()
        }
    }

    fun updateProverMap(id: Int, proverState: ProverState) {
        proverMap[id] = proverState
    }

    fun findProverState(id: Int): ProverState? {
        return proverMap[id]
    }

    fun removeProver(id: Int) {
        val p = proverMap[id] ?: throw RuntimeException("Prover with id $id does not exist")

        // Remove prover from the map
        proverMap.remove(id)
        // Clear the prover stack
        resetProver(p.prover)
        // Clear prover's model
        p.clear()
        // Add prover to the pool
        proverPool.add(p)
    }
}
