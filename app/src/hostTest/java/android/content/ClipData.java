package android.content;
public class ClipData {
 private final Item item;
 private ClipData(CharSequence text){item=new Item(text);}
 public static ClipData newPlainText(CharSequence label,CharSequence text){return new ClipData(text);}
 public Item getItemAt(int index){return item;}
 public static class Item {
  private final CharSequence text;
  public Item(CharSequence text){this.text=text;}
  public CharSequence coerceToText(Context context){return text;}
 }
}
