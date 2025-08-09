package org.funfix.tasks.jvm;

import java.time.Duration;

public class TestSettings {
    public static final Duration TIMEOUT;
    public static final int CONCURRENCY_REPEATS;

    static {
        if (System.getenv("CI") != null)
            TIMEOUT = Duration.ofSeconds(20);
        else
            TIMEOUT = Duration.ofSeconds(10);

        if (System.getenv("CI") != null) {
            CONCURRENCY_REPEATS = 1000;
        } else {
            CONCURRENCY_REPEATS = 10000;
        }
    }
}
