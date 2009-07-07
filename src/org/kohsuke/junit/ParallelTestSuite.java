package org.kohsuke.junit;

import junit.framework.*;

/**
 * {@link TestSuite} that runs {@link Test}s in parallel.
 *
 * <p>
 * This implementation maintains illusion for the {@link TestResult}
 * that all the tests are run in a single thread.
 * For example, even though multiple tests are run at the same time,
 * the {@link TestResult} object receives startTest/endTest in an orderly
 * manner. All the callbacks are done from the thread that invokes
 * the {@link #run(junit.framework.TestResult)} } method.
 *
 * <p>
 * Furthermore, the output to the screen by {@link Test}s are serialized
 * to avoid screen cluttering.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class ParallelTestSuite extends TestSuite {
    private ParallelPrintStream out;
    private ParallelPrintStream err;

    /**
     * Before the test starts, this field keeps the number of worker threads.
     * Once the test is started, this field keeps the number of live worker threads.
     */
    private int nThreads;

    private ThreadMarshaller tm;

    public ParallelTestSuite(int nThreads) {
        this.nThreads = nThreads;
    }

    public ParallelTestSuite(Class theClass, String name, int nThreads) {
        super(theClass, name);
        this.nThreads = nThreads;
    }

    public ParallelTestSuite(Class theClass, int nThreads) {
        super(theClass);
        this.nThreads = nThreads;
    }

    public ParallelTestSuite(String name, int nThreads) {
        super(name);
        this.nThreads = nThreads;
    }

    public ParallelTestSuite() {
        this(defaultThreadSize());
    }

    public ParallelTestSuite(Class theClass, String name) {
        this(theClass, name, defaultThreadSize());
    }

    public ParallelTestSuite(Class theClass) {
        this(theClass, defaultThreadSize());
    }

    public ParallelTestSuite(String name) {
        this(name, defaultThreadSize());
    }

    public void run(final TestResult result) {
        out = new ParallelPrintStream(System.out);
        System.setOut(out);
        err = new ParallelPrintStream(System.err);
        System.setErr(err);

        try {
            // this thread marshaller serializes the calls from multiple threads
            // into the main thread.
            tm = new ThreadMarshaller(
                TestListener.class,
                new TestListener() {
                    public void addError(Test test, Throwable t) {
                        result.addError(test,t);
                    }

                    public void addFailure(Test test, AssertionFailedError t) {
                        result.addFailure(test,t);
                    }

                    public void startTest(Test test) {
                        result.startTest(test);
                    }

                    public void endTest(Test test) {
                        result.endTest(test);
                    }
                });

            // prevent nThreads from getting modified while we create threads 
            synchronized (this) {
                for( int i=0; i<nThreads; i++ )
                    new WorkerThread( i, (TestListener)tm.getProxy() ).start();
            }

            tm.run(); // blocks until all the worker threads are finished,
        } finally {
            System.setOut(out.getBase());
            System.setErr(err.getBase());
            // clean up
            out = null;
            err = null;
            tm = null;
        }
    }

    /**
     * Remembers the index of the next {@link Test} that needs to be run.
     */
    private int nextTestIndex;

    /**
     * {@link WorkerThread} can use this method to get a next {@link Test}.
     *
     * @return null if there's no more test.
     */
    private synchronized Test getNextTest() {
        if(nextTestIndex==testCount())
            return null;
        return testAt(nextTestIndex++);
    }

    /**
     * {@link WorkerThread} calls this method once it's done.
     */
    private synchronized void finish() {
        nThreads--;
        if(nThreads==0)
            tm.finish();
    }

    final class WorkerThread extends Thread {
        private final TestResult result;

        WorkerThread(int id,TestListener listener) {
            super(new WorkerThreadGroup(id),"WorkerThread-"+id);
            this.result = new ProxyTestResult(listener);
        }

        public void run() {
            try {
                Test t;
                while((t=getNextTest())!=null) {
                    t.run(result);
                }
            } finally {
                finish();
            }
        }

        /**
         * Passed to {@link Test}s run in this worker thread.
         *
         * <p>
         * Record method calls until we receive the endTest event,
         * and then serialize the events to the main method at once.
         *
         * <p>
         * This minimizes the time we occupy the main thread, thus
         * allowing multiple worker threads to run tests.
         */
        final class ProxyTestResult extends TestResult {
            private final MethodCallRecorder recorder;
            private final TestListener core;
            private TestListener recorderProxy;

            public ProxyTestResult(TestListener core) {
                recorder = new MethodCallRecorder(TestListener.class);
                recorderProxy = (TestListener)recorder.getProxy();
                this.core = core;
            }

            public synchronized void addError(Test test, Throwable t) {
                super.addError(test, t);
                recorderProxy.addError(test,t);
            }

            public synchronized void addFailure(Test test, AssertionFailedError t) {
                super.addFailure(test, t);
                recorderProxy.addFailure(test,t);
            }

            public void startTest(Test test) {
                super.startTest(test);
            }

            public void endTest(Test test) {
                super.endTest(test);

                synchronized(ParallelTestSuite.this) {
                    core.startTest(test);

                    // send the output
                    out.purge();
                    err.purge();

                    // send the accumulated method calls to the actual object
                    try {
                        recorder.replay(core);
                    } catch (RuntimeException e) {
                        // must be a bug in the test result listener
                        throw e;
                    } catch(Error e) {
                        throw e;
                    } catch (Throwable e) {
                        // impossible
                        e.printStackTrace();
                        throw new InternalError();
                    }
                    recorder.clear();

                    core.endTest(test);
                }
            }
        }
    }

    /**
     * Gets the default thread pool size.
     */
    private static final int defaultThreadSize() {
        return 4;
    }
}
