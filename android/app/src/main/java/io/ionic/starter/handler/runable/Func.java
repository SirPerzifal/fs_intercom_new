package io.ionic.starter.handler.runable;

/**
 * Run callback, the callback can support return any value
 */
public interface Func<Out> {
    Out call();
}
