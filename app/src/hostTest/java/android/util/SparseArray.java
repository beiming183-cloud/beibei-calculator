package android.util;
import java.util.*;
public final class SparseArray<E> {
 private final Map<Integer,E> entries=new LinkedHashMap<>();
 public SparseArray(int capacity){}
 public int indexOfKey(int key){return new ArrayList<>(entries.keySet()).indexOf(key);}
 public int size(){return entries.size();}
 public void put(int key,E value){entries.put(key,value);}
 public E get(int key){return entries.get(key);}
 public void remove(int key){entries.remove(key);}
 public void clear(){entries.clear();}
}
