package org.kohsuke.junit;

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;

/**
 * Converts a method call one one object by arbitrary thread
 * into the equivalent method call by the designated "main" thread.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public final class ThreadMarshaller implements InvocationHandler {

    /**
     * The real object whose methods can be only called from the main thread.
     */
    private final Object real;

    /**
     * The proxy object that the other threads should call.
     */
    private final Object proxy;

    /**
     * Queue of {@link MethodCall} objects.
     */
    private final List callQueue = new LinkedList();

    public ThreadMarshaller( Class intf, Object realObject ) {
        this( new Class[]{intf}, realObject );
    }

    public ThreadMarshaller( Class[] intfs, Object realObject ) {
        proxy = Proxy.newProxyInstance( intfs[0].getClassLoader(), intfs, this );
        this.real = realObject;
    }

    public Object getProxy() {
        return proxy;
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodCall mc = new MethodCall(method,args);
        callQueue.add(mc);

        synchronized(mc) {
            synchronized(this) {
                this.notify();      // let the main thread know that there's a new item
            }
            mc.wait();  // wait for the method call to complete
        }

        if(mc.exception!=null)
            throw mc.exception;
        else
            return mc.result;
    }

    /**
     * The main thread should call this thread to wait for the serialized method
     * calls from other threads.
     *
     * <p>
     * This method blocks until the {@link #finish()} method is called.
     * While blocking, this thread will invoke methods on the marshalled object.
     */
    public synchronized void run() {
        while(true) {
            try {
                this.wait();    // wait for new incoming method call
            } catch (InterruptedException e) {
                // not sure if this may ever happen
                e.printStackTrace();
            }
            MethodCall mc = (MethodCall)callQueue.remove(0);

            if(mc==null)
                break;  // signals the completion

            synchronized(mc) {
                mc.execute();
                mc.notify();    // let the caller thread know that the invocation is done
            }
        }
    }

    /**
     * Signals that other threads are done with this marshaller, and
     * the main thread can resume its execution.
     */
    public synchronized void finish() {
        callQueue.add(null);
        this.notify();
    }

    class MethodCall {
        // in
        Method method;
        Object[] args;

        // out
        Throwable exception;
        Object result;

        MethodCall(Method method, Object[] args) {
            this.method = method;
            this.args = args;
        }

        void execute() {
            try {
                result = method.invoke(real,args);
            } catch (IllegalAccessException e) {
                throw new IllegalAccessError(e.getMessage());
            } catch (InvocationTargetException e) {
                exception = e.getTargetException();
            }
            // clear the parameters
            method = null;
            args = null;
        }
    }
}
