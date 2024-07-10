package org.funfix.tasks;

import java.util.concurrent.TimeUnit;

public interface RuntimeClock {
    long realTime(TimeUnit unit);
    long monotonic(TimeUnit unit);

    RuntimeClock System = new RuntimeClock() {
        @Override
        public long realTime(TimeUnit unit) {
            return unit.convert(
                    java.lang.System.currentTimeMillis(),
                    TimeUnit.MILLISECONDS
            );
        }

        @Override
        public long monotonic(TimeUnit unit) {
            return unit.convert(
                    java.lang.System.nanoTime(),
                    TimeUnit.NANOSECONDS
            );
        }
    };
}
