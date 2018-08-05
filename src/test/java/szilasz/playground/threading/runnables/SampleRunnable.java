package szilasz.playground.threading.runnables;

import java.util.concurrent.TimeUnit;

public class SampleRunnable implements Runnable {

    @Override
    public void run() {
        final Thread currentThread = Thread.currentThread();
        System.out.println(String.format("Current thread %s has started", currentThread));
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (final InterruptedException e) {
            System.out.println("Interrupted " + e.getMessage());
        }
        System.out.println(String.format("Current thread %s has finished", currentThread));
    }
}

