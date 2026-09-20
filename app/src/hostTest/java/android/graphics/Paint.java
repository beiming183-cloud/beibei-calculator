package android.graphics;
public class Paint {
 public static final int ANTI_ALIAS_FLAG=1,SUBPIXEL_TEXT_FLAG=128;
 private float size;
 public Paint(int flags){}
 public static class FontMetrics {public float ascent,descent;}
 public Typeface setTypeface(Typeface typeface){return typeface;}
 public void setTextSize(float size){this.size=size;}
 public float measureText(String text){return text.length()*size*0.5f;}
 public float measureText(String text,int start,int end){return (end-start)*size*0.5f;}
 public enum Style {FILL,STROKE,FILL_AND_STROKE}
 public enum Align {LEFT,CENTER,RIGHT}
 public enum Cap {BUTT,ROUND,SQUARE}
 public enum Join {MITER,ROUND,BEVEL}
 public void setStyle(Style value){}
 public void setTextAlign(Align value){}
 public void setStrokeCap(Cap value){}
 public void setStrokeJoin(Join value){}
 public void setStrokeWidth(float value){}
 public void setColor(int value){}
 public float getFontMetrics(FontMetrics metrics){metrics.ascent=-size*0.8f;metrics.descent=size*0.2f;return size;}
}
