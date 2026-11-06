package com.blackice.bc1.editor;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class LatestQueueDiscardTest {
 @Test public void supersededRequestsHaveAnExplicitCompletion(){List<Runnable> jobs=new ArrayList<>();List<Integer> ran=new ArrayList<>(),cancelled=new ArrayList<>();LatestQueue<Integer> queue=new LatestQueue<>(jobs::add,ran::add,cancelled::add);queue.submit(1);queue.submit(2);queue.submit(3);assertEquals(Arrays.asList(1,2),cancelled);jobs.get(0).run();assertEquals(Arrays.asList(3),ran);assertEquals(0,queue.queued());}
}
