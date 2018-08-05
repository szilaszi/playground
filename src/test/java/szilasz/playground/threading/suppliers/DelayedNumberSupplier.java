package szilasz.playground.threading.suppliers;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class DelayedNumberSupplier implements Supplier<Integer> {

    @Override
    public Integer get() {
        final Thread currentThread = Thread.currentThread();
        System.out.println(String.format("Current sampleNumberSupplier thread %s has started", currentThread));
        final int sleepPeriod = 3 + new Random().nextInt(7);
        try {
            System.out.printf("Sleeping for %d seconds%n", sleepPeriod);
            TimeUnit.SECONDS.sleep(sleepPeriod);
        } catch (final InterruptedException e) {
            System.out.printf("Interrupted %s%n", e.getMessage());
        }
        System.out.println(String.format("Current sampleNumberSupplier thread %s has finished", currentThread));
        return sleepPeriod;
    }

}
