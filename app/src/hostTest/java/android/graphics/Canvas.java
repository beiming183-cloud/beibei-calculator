package android.graphics;
import java.util.ArrayList;
import java.util.List;
/** Records drawing commands only, not pixel geometry or device typography. */
public class Canvas {
 public final List<String> texts = new ArrayList<>();
 public int lines;
 public int save(){return 1;}
 public void restore(){}
 public void translate(float x,float y){}
 public void scale(float x,float y){}
 public void scale(float x,float y,float px,float py){}
 public boolean clipRect(float l,float t,float r,float b){return true;}
 public boolean clipRect(RectF rect){return true;}
 public void drawText(String text,float x,float y,Paint paint){texts.add(text);}
 public void drawLine(float a,float b,float c,float d,Paint paint){lines++;}
 public void drawRect(float a,float b,float c,float d,Paint paint){}
 public void drawRect(RectF rect,Paint paint){}
 public void drawRoundRect(RectF rect,float x,float y,Paint paint){}
 public void drawRoundRect(float a,float b,float c,float d,float x,float y,Paint paint){}
 public void drawCircle(float x,float y,float radius,Paint paint){}
 public void drawPath(Path path,Paint paint){}
 public void drawColor(int color){}
}
