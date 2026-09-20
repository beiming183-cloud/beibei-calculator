package android.os;
import java.util.*;
/** Deterministic host main queue. Tests advance time explicitly, without sleeps. */
public class Handler {
 private record Task(Runnable runnable,long time){}
 private static final List<Task> tasks=new ArrayList<>();
 private static long now;
 public boolean postDelayed(Runnable task,long delay){synchronized(tasks){tasks.add(new Task(task,now+delay));}return true;}
 public boolean post(Runnable task){return postDelayed(task,0);}
 public void removeCallbacks(Runnable task){synchronized(tasks){tasks.removeIf(t->t.runnable()==task);}}
 public static void advanceBy(long millis){
  long end=now+millis;
  for(int count=0;count<1000;count++){
   Task next;
   synchronized(tasks){next=tasks.stream().filter(t->t.time()<=end).min(Comparator.comparingLong(Task::time)).orElse(null);if(next==null){now=end;return;}tasks.remove(next);}
   now=next.time();next.runnable().run();
  }
  throw new AssertionError("Main queue did not settle");
 }
 public static void reset(){synchronized(tasks){tasks.clear();now=0;}}
}
