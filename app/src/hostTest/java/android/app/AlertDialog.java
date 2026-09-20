package android.app;
import android.content.Context;
import android.content.DialogInterface;
public class AlertDialog {
 public static String[] items;
 public static DialogInterface.OnClickListener listener;
 public static class Builder {
  public Builder(Context context){}
  public Builder setItems(CharSequence[] values,DialogInterface.OnClickListener callback){
   items=new String[values.length];
   for(int i=0;i<values.length;i++)items[i]=values[i].toString();
   listener=callback; return this;
  }
  public AlertDialog show(){return new AlertDialog();}
 }
}
