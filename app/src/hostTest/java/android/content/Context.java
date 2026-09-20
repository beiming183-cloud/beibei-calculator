package android.content;
import android.content.res.Resources;
public class Context {
 public static final String CLIPBOARD_SERVICE="clipboard";
 private final Resources resources=new Resources();
 private final ClipboardManager clipboard=new ClipboardManager();
 public Resources getResources(){return resources;}
 public Object getSystemService(String name){return clipboard;}
}
