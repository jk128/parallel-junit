package org.kohsuke.junit;

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.ArrayList;

/**
 * Records the method calls to an interface, and "replays" them
 * later.
 *
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public final class MethodCallRecorder implements InvocationHandler {

    private final Object proxy;

    private final List queue = new ArrayList();

    private static class MethodCall {
        private final Method method;
        private final Object[] args;

        MethodCall(Method method, Object[] args) {
            this.method = method;
            this.args = args;
        }

        void execute(Object target) throws Throwable {
            try {
                method.invoke(target,args);
            } catch (IllegalAccessException e) {
                throw new IllegalAccessError(e.getMessage());
            } catch (InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
    }

    /**
     * Creates a new recorder for the given interface.
     *
     * @param intf
     *      all the methods on this interface should return void.
     */
    public MethodCallRecorder(Class intf) {
        proxy = Proxy.newProxyInstance(intf.getClassLoader(),new Class[]{intf},this);
    }

    /**
     * Returns the object that implements the interface specified to the constructor.
     *
     * <p>
     * Method calls to this object will be recorded.
     */
    public Object getProxy() {
        return proxy;
    }

    /**
     * Implementation detail. Shouldn't be called by the client.
     */
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        queue.add(new MethodCall(method,args));
        return null;
    }

    /**
     * Replays the recorded method calls to the specified object.
     *
     * <p>
     * If the given target object throws any exception while replaying
     * the method calls, the replay will be aborted and the same
     *
     * @param realTarget
     *      This object must implement the interface given to the constructor.
     */
    public void replay( Object realTarget ) throws Throwable {
        for (int i = 0; i < queue.size(); i++) {
            MethodCall methodCall = (MethodCall) queue.get(i);
            methodCall.execute(realTarget);
        }
    }

    /**
     * Clears the recorded method calls.
     */
    public void clear() {
        queue.clear();
    }

}
