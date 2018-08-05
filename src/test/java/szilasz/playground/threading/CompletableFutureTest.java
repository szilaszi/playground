package szilasz.playground.threading;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import szilasz.playground.threading.runnables.SampleRunnable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.stream.IntStream;

public class CompletableFutureTest {

    public static final String INTERRUPTED_EXCEPTION = "InterruptedException: ";
    public static final String EXCEPTION = "Exception: ";
    public static final String EXCEPTION_IN_RUNNER = "Exception in runner: ";
    Logger log = LoggerFactory.getLogger(this.getClass());

    @Test
    public void runAThread() throws ExecutionException, InterruptedException {
        final CompletableFuture future = CompletableFuture.runAsync(new SampleRunnable());
        log.info("samplerunnable created");
        future.get();
    }

    @Test
    public void run3ThreadsWaitForAFutureWithExecutorService() throws InterruptedException {
        final CompletableFuture<String> longRunner = new CompletableFuture<>();
        final ExecutorService executorService = Executors.newFixedThreadPool(5);
        IntStream.rangeClosed(1, 5).forEach(c -> {
            log.info("Starting listeners");
            executorService.submit(getWaitingForlongRunner(longRunner));
        });
        TimeUnit.SECONDS.sleep(3);
        longRunner.complete("I am done!");
        executorService.shutdown();
    }

    /**
     * This is a test case when you have one process which takes long and
     * there are several others depending on its result
     * here I start a number of listeners which will finish at the same time when the code gets to longRunner.complete
     *
     * @throws InterruptedException
     */
    @Test
    public void run3ThreadsWaitForAFutureWithRunAsync() throws InterruptedException {
        log.debug("run3ThreadsWaitForAFutureWithRunAsync start");
        final CompletableFuture<String> longRunner = new CompletableFuture<>();
        IntStream.rangeClosed(1, 5).forEach(c -> {
            log.info("Starting listeners");
            CompletableFuture.runAsync(getWaitingForlongRunner(longRunner));
        });
        TimeUnit.SECONDS.sleep(3);
        longRunner.complete("I am done!");

    }

    @Test
    void run2ThreadsInSequenceWithApply() throws ExecutionException, InterruptedException {
        final CompletableFuture<CompletableFuture<String>> future = this.getDelayedRandomNumber().thenApply(this::getDelayedMultipliedNumberAsString);
        log.info("run2ThreadsInSequenceWithApply: {}", future.get());
    }

    @Test
    void run2ThreadsInSequenceWithCombine() throws ExecutionException, InterruptedException {
        final CompletableFuture<String> future = this.getDelayedRandomNumber().thenCompose(this::getDelayedMultipliedNumberAsString);
        final CompletableFuture<Integer> future2 = this.getDelayedRandomNumber().thenCompose(this::getDelayedMultipliedNumberAsString).thenCompose(this::getDelayedStringToNumber);
        log.info("run2ThreadsInSequenceWithCombine: f1 - {}", future.get());
        log.info("run2ThreadsInSequenceWithCombine: f2 - {}", future2.get());
    }

    @Test
    void combine5ThreadsIntoOne() throws ExecutionException, InterruptedException {
        final CompletableFuture<Void> masterFuture = CompletableFuture.allOf(this.getDelayedRandomNumber(), this.getDelayedRandomNumber(), this.getDelayedRandomNumber(), this.getDelayedRandomNumber());
        masterFuture.get();
        log.info("all threads done");
    }

    @Test
    void testingErrorHandling() throws ExecutionException, InterruptedException {
        final CompletableFuture future = CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("Ops I DiD iT AgaiN.");
        }).exceptionally(e -> {
            log.info(EXCEPTION, e);
            return "Baki";
        });
        log.info("testingErrorHandling - {}", future.get());
    }

    @Test
    void testingErrorHandlingWithHandle() throws ExecutionException, InterruptedException {
        final CompletableFuture future = CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("Ops I DiD iT AgaiN.");
        }).handle((v, e) -> {
            log.error(EXCEPTION, e);
            // v will be the value from the previous supplier. When there is no exception, it is the return value otherwise null and ex has value
            return v;
        });
        log.info("run2ThreadsInSequenceWithCombine: f1 - {}", future.get());
    }

    @Test
    void multipleRunnableRunningWithTimeOutMonitorAndRunnerThreads() throws ExecutionException, InterruptedException {
        final ExecutorService executorService = Executors.newFixedThreadPool(10);
        final List<CompletableFuture> tasks = new ArrayList(5);
        log.info("generating tasks");
        final Instant start = Instant.now();
        IntStream.rangeClosed(0, 4).forEach(i -> tasks.add(CompletableFuture.supplyAsync(() -> {

            log.debug(String.format("Runnable thread %s of task %d running", Thread.currentThread().getName(), i));
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (final InterruptedException e) {
                log.error("Exception in task: ", e);
                Thread.currentThread().interrupt();
            }
            log.debug(String.format("Runnable thread %s of task %d finished", Thread.currentThread().getName(), i));
            return null;
        }, executorService)));
        log.info("generating runners");
        final List<CompletableFuture> runners = new ArrayList(5);
        tasks.forEach(task -> runners.add(CompletableFuture.supplyAsync(() -> {
            try {
                task.get(3, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                log.error(EXCEPTION_IN_RUNNER, e);
                Thread.currentThread().interrupt();
            } catch (final ExecutionException | TimeoutException e) {
                log.error(EXCEPTION_IN_RUNNER, e);
            }
            return null;
        }, executorService)));
        log.debug("should start everything");
        CompletableFuture.allOf(runners.toArray((new CompletableFuture[runners.size()]))).get();
        final Instant end = Instant.now();
        final String message = String.format("Test finished after %d nanos", Duration.between(start, end).getNano());
        log.debug(message);

    }

    @Test
    void multipleRunnableRunningWithJustMonitorThreads() throws ExecutionException, InterruptedException {
        final ExecutorService executorService = Executors.newFixedThreadPool(10);

        final Instant start = Instant.now();
        final List<CompletableFuture> runners = new ArrayList(5);
        log.info("generating tasks");
        IntStream.rangeClosed(0, 4).forEach(i ->
                runners.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return CompletableFuture.supplyAsync(() -> {
                            log.debug(String.format("Runnable thread %s of task %d running", Thread.currentThread().getName(), i));
                            try {
                                TimeUnit.SECONDS.sleep(5);
                            } catch (InterruptedException e) {
                                log.error("Exception in task: ", e);
                                Thread.currentThread().interrupt();
                            }
                            log.debug(String.format("Runnable thread %s of task %d finished", Thread.currentThread().getName(), i));
                            return null;
                        }, executorService).get(3, TimeUnit.SECONDS);
                    } catch (final InterruptedException e) {
                        log.error(EXCEPTION_IN_RUNNER, e);
                        Thread.currentThread().interrupt();
                    } catch (final ExecutionException | TimeoutException e) {
                        log.error(EXCEPTION_IN_RUNNER, e);
                    }
                    return null;
                }, executorService)));
        CompletableFuture.allOf(runners.toArray((new CompletableFuture[runners.size()]))).get();
        final Instant end = Instant.now();
        final String message = String.format("Test finished after %d seconds", Duration.between(start, end).getSeconds());
        log.debug(message);
    }

    @Test
    void testAcceptEither() {

        final CompletableFuture<Integer> longRunnerNumber = CompletableFuture.completedFuture(10);
        IntStream.rangeClosed(1, 5).forEach(i -> {
            final Integer result = longRunnerNumber.thenApplyAsync(this::delayedMultiplyByTwo).applyToEither(longRunnerNumber.thenApplyAsync(this::delayedMultiplyByThree), integer -> integer * 100).join();
            log.info("Try #{} - result is {} ", i, result);
        });
    }

    private Integer delayedMultiplyByTwo(final Integer integer) {
        final int sleep = new Random().nextInt(5);
        try {
            log.debug("I really want to multiply by two");
            TimeUnit.SECONDS.sleep(sleep);
        } catch (final InterruptedException e) {
            log.error("Interrupted", e);
            Thread.currentThread().interrupt();
        }
        return integer * 2;
    }

    private Integer delayedMultiplyByThree(final Integer integer) {
        final int sleep = new Random().nextInt(5);
        try {
            log.debug("I really want to multiply by three");
            TimeUnit.SECONDS.sleep(sleep);
        } catch (final InterruptedException e) {
            log.error("Interrupted", e);
            Thread.currentThread().interrupt();
        }
        return integer * 3;
    }

    /*
     you can cast lambdas too ho:
     CompletableFuture<Integer> f=new CompletableFuture<>();
     ForkJoinPool.commonPool().submit(
     (Runnable&CompletableFuture.AsynchronousCompletionTask)()-> {
     try { f.complete(SupplyNumbers.sendNumbers()); }
     catch(Exception ex) { f.completeExceptionally(ex); }
     });
     */

    private CompletableFuture<Integer> getDelayedRandomNumber() {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Getting a random number ");
            final int sleep = 2 + new Random().nextInt(5);
            try {
                log.info("sleeping for {} seconds%n", sleep);
                TimeUnit.SECONDS.sleep(sleep);
                log.info("sleept for {} seconds", sleep);
            } catch (final InterruptedException e) {
                log.error(INTERRUPTED_EXCEPTION, e);
                Thread.currentThread().interrupt();
            }
            return sleep;
        });
    }

    private CompletableFuture<String> getDelayedMultipliedNumberAsString(final Integer integer) {
        return CompletableFuture.supplyAsync(() -> {
            final int sleep = 1 + new Random().nextInt(3);
            try {
                TimeUnit.SECONDS.sleep(sleep);
            } catch (final InterruptedException e) {
                log.error(INTERRUPTED_EXCEPTION, e);
                Thread.currentThread().interrupt();
            }
            return String.valueOf(integer * 2);
        });
    }

    private CompletableFuture<Integer> getDelayedStringToNumber(final String string) {
        return CompletableFuture.supplyAsync(() -> {
            final int sleep = 1 + new Random().nextInt(3);
            try {
                TimeUnit.SECONDS.sleep(sleep);
            } catch (final InterruptedException e) {
                log.error(INTERRUPTED_EXCEPTION, e);
                Thread.currentThread().interrupt();
            }
            return Integer.valueOf(string);
        });
    }

    @NotNull
    private Runnable getWaitingForlongRunner(final CompletableFuture<String> longRunner) {
        return () -> {
            log.info("Waiting for longRunner");
            try {
                log.info("LongRunner finished: {}", longRunner.get());
            } catch (final Exception e) {
                log.error(EXCEPTION, e);
            }
        };
    }

}
