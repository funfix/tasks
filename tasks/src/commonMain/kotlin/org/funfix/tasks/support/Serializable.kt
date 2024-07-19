@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.support

/**
 * Marker for types that should be serializable.
 *
 * On top of the JVM this is aliased to `java.io.Serializable`.
 */
public expect interface Serializable {}
