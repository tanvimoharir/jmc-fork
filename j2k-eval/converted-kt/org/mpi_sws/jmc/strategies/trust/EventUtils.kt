package org.mpi_sws.jmc.strategies.trust

object EventUtils {
    fun isExclusiveWrite(event: Event): Boolean {
        if (event.type != Event.Type.WRITE_EX) {
            return false
        }
        // We exclude writes related to lock acquisition
        return !event.hasAttribute("lock_acquire")
    }

    fun isWrite(event: Event): Boolean {
        return event.type == Event.Type.WRITE_EX || event.type == Event.Type.WRITE
    }

    fun isRead(event: Event): Boolean {
        return event.type == Event.Type.READ || event.type == Event.Type.READ_EX
    }

    fun isLockAcquireRead(event: Event): Boolean {
        return event.type == Event.Type.READ_EX && event.hasAttribute("lock_acquire")
    }

    fun isLockReleaseWrite(event: Event): Boolean {
        return event.type == Event.Type.WRITE && event.hasAttribute("lock_release")
    }

    fun isLockAcquireWrite(event: Event): Boolean {
        return event.type == Event.Type.WRITE_EX && event.hasAttribute("lock_acquire")
    }

    fun isBlockingLabel(event: Event): Boolean {
        return event.type == Event.Type.BLOCK
    }

    fun getStartedBy(event: Event): Long? {
        return event.getAttribute("started_by")
    }

    fun isExclusiveRead(event: Event): Boolean {
        return event.type == Event.Type.READ_EX
    }

    fun isThreadStart(event: Event): Boolean {
        return event.hasAttribute("thread_start")
    }

    fun isThreadFinish(event: Event): Boolean {
        return event.hasAttribute("thread_finish")
    }

    fun isThreadJoin(event: Event): Boolean {
        return event.hasAttribute("thread_join")
    }

    fun isJoinRequest(event: Event): Boolean {
        return event.hasAttribute("join-req")
    }

    fun getJoinedTask(event: Event): Int {
        val joinedTask = event.getAttribute<Long>("joined_task") ?: return -1
        return Math.toIntExact(joinedTask)
    }

    fun makeUnRevistable(event: Event) {
        event.setAttribute("revisit", false)
    }

    fun makeRevistable(event: Event) {
        event.setAttribute("revisit", true)
    }

    fun isRevisit(event: Event): Boolean {
        val revisit = event.getAttribute<Boolean>("revisit")
        return revisit == null || revisit
    }

    fun isFinalLockWrite(event: Event): Boolean {
        return event.type == Event.Type.WRITE_EX && event.hasAttribute("final_lock")
                && event.hasAttribute("lock_acquire")
    }

    fun markLockWriteFinal(event: Event) {
        event.setAttribute("final_lock", true)
    }

    fun isLockAcquired(event: Event): Boolean {
        return event.type == Event.Type.NOOP && event.hasAttribute("lock_acquired")
    }

    fun isNoop(event: Event): Boolean {
        return event.type == Event.Type.NOOP
    }

    fun isAssume(event: Event): Boolean {
        return event.type == Event.Type.ASSUME
    }

    fun isBlockedAssume(event: Event): Boolean {
        return event.type == Event.Type.ASSUME && !event.getAttribute<Any>("result") as Boolean
    }
}
