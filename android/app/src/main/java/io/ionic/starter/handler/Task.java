package io.ionic.starter.handler;

import java.util.Queue;

/**
 * The Task be used to Poster do something
 * This Task extends {@link Runnable} and {@link Result}
 */

interface Task extends Runnable, Result {
    void setPool(Queue<Task> pool);
}
