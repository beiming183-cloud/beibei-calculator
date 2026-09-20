package android.view;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
public class View {
 public static final int LAYER_TYPE_HARDWARE=2;
 private final Context context;
 private int width,height;
 public View(Context context){this.context=context;}
 public Context getContext(){return context;}
 public Resources getResources(){return context.getResources();}
 public void setFocusable(boolean b){}
 public void setFocusableInTouchMode(boolean b){}
 public void setSoundEffectsEnabled(boolean b){}
 public void setHapticFeedbackEnabled(boolean b){}
 public void setLayerType(int type,Paint paint){}
 public void setKeepScreenOn(boolean b){}
 public void setContentDescription(CharSequence s){}
 public void postInvalidateOnAnimation(){}
 public boolean performHapticFeedback(int constant){return true;}
 public boolean post(Runnable r){return new Handler().post(r);}
 public int getWidth(){return width;}
 public int getHeight(){return height;}
 public void layout(int l,int t,int r,int b){width=r-l;height=b-t;onSizeChanged(width,height,0,0);}
 protected void onSizeChanged(int w,int h,int ow,int oh){}
 protected void onDraw(Canvas canvas){}
 protected void onDetachedFromWindow(){}
 protected void onAttachedToWindow(){}
 public void onWindowFocusChanged(boolean focus){}
 public boolean performClick(){return true;}
 public boolean onTouchEvent(MotionEvent e){return false;}
 public boolean onKeyDown(int code,KeyEvent event){return false;}
}
