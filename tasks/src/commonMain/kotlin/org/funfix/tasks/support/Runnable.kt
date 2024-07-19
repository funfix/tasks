@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.support

/**
 * Marker for simple tasks that can be executed asynchronously
 * (typically via a thread-pool or event-loop).
 *
 * On the JVM this is aliased to `java.lang.Runnable`.
 */
public expect fun interface Runnable {
    public fun run()
}
