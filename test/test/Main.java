package test;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.kohsuke.junit.ParallelTestSuite;

/**
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class Main {
    public static void main(String[] args) {
        TestSuite tests = new ParallelTestSuite();
        for(int i=0; i<10; i++)
            tests.addTest(new TestImpl());
        TestRunner.run(tests);
    }

    private static class TestImpl extends TestCase {
        public TestImpl() {
            super("test");
        }

        protected void runTest() throws Throwable {
            for( int i=0; i<10; i++ ) {
                Thread.sleep(1000);
                System.out.println(Thread.currentThread());
                System.err.println(System.currentTimeMillis());
            }
        }

    }
}
