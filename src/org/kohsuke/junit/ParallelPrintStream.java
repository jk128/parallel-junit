package org.kohsuke.junit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link PrintStream} that handles concurrent access from
 * multiple threads and avois screen clutter.
 */
class ParallelPrintStream extends PrintStream {
    private final PrintStream base;

    public ParallelPrintStream(PrintStream out) {
        super(out);
        this.base =out;
    }

    private class Streams {
        private final PrintStream ps;
        private final ByteArrayOutputStream baos;

        Streams() {
            baos = new ByteArrayOutputStream();
            ps = new PrintStream(baos);
        }

        void purge() {
            try {
                ps.flush();
                baos.writeTo(base);
                baos.reset();
            } catch (IOException e) {
                setError();
            }
        }
    }

    /**
     * Maintain a buffer for each thread group.
     */
    private final Map/*<WorkerThreadGroup,Streams>*/ buffer = Collections.synchronizedMap(new HashMap());

    public PrintStream getBase() {
        return base;
    }

    private Streams getStreams() {
        ThreadGroup tg;
        for( tg = Thread.currentThread().getThreadGroup(); tg!=null && !(tg instanceof WorkerThreadGroup); tg=tg.getParent() )
            ;

        Streams s = (Streams)buffer.get(tg);
        if(s==null)
            buffer.put(tg,s = new Streams());

        return s;
    }

    private PrintStream out() {
        return getStreams().ps;
    }

    /**
     * Sends the buffered output from this thread to the actual output.
     */
    public synchronized void purge() {
        getStreams().purge();
    }


    //
    //
    // delegate method calls to PrintStream to thread-local stream.
    //
    //



    public void println() {
        out().println();
    }

    public void print(char c) {
        out().print(c);
    }

    public void println(char x) {
        out().println(x);
    }

    public void print(double d) {
        out().print(d);
    }

    public void println(double x) {
        out().println(x);
    }

    public void print(float f) {
        out().print(f);
    }

    public void println(float x) {
        out().println(x);
    }

    public void print(int i) {
        out().print(i);
    }

    public void println(int x) {
        out().println(x);
    }

    public void print(long l) {
        out().print(l);
    }

    public void println(long x) {
        out().println(x);
    }

    public void print(boolean b) {
        out().print(b);
    }

    public void println(boolean x) {
        out().println(x);
    }

    public void print(char s[]) {
        out().print(s);
    }

    public void println(char x[]) {
        out().println(x);
    }

    public void print(Object obj) {
        out().print(obj);
    }

    public void println(Object x) {
        out().println(x);
    }

    public void print(String s) {
        out().print(s);
    }

    public void println(String x) {
        out().println(x);
    }

    public void write(byte b[], int off, int len) {
        out().write(b, off, len);
    }

    public void close() {
        out().close();
    }

    public void flush() {
        out().flush();
    }

    public void write(int b) {
        out().write(b);
    }

    public void write(byte b[]) throws IOException {
        out().write(b);
    }
}
