package org.kohsuke.junit;

/**
 * @author Kohsuke Kawaguchi
 */
class WorkerThreadGroup extends ThreadGroup {
    public WorkerThreadGroup(int id) {
        super("Parallel JUnit Worker Thread "+id);
    }
}
