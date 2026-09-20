package android.content;
public class ClipboardManager {
 private ClipData clip;
 public boolean hasPrimaryClip(){return clip!=null;}
 public ClipData getPrimaryClip(){return clip;}
 public void setPrimaryClip(ClipData value){clip=value;}
}
