package com.blackice.bc1.editor;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Serial work with a bounded backlog: the running value plus the newest requested value. */
public final class LatestQueue<T> {
    private final Executor executor;private final Consumer<T> work;private final Consumer<T> discard;private T pending;private boolean running;
    public LatestQueue(Executor executor,Consumer<T> work){this(executor,work,v->{});}
    public LatestQueue(Executor executor,Consumer<T> work,Consumer<T> discard){this.executor=executor;this.work=work;this.discard=discard;}
    public synchronized void submit(T value){if(pending!=null)discard.accept(pending);pending=value;if(!running){running=true;executor.execute(this::drain);}}
    private void drain(){while(true){T value;synchronized(this){value=pending;pending=null;if(value==null){running=false;return;}}try{work.accept(value);}catch(RuntimeException e){synchronized(this){running=false;}throw e;}}}
    public synchronized int queued(){return (running?1:0)+(pending==null?0:1);}
}
