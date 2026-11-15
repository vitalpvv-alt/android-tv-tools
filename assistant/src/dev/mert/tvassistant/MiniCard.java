package dev.mert.tvassistant;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.text.Layout;
import android.text.StaticLayout;
import android.view.*;
import android.widget.*;

/** Passive compact card: it neither takes remote focus nor dims the application. */
final class MiniCard extends LinearLayout {
  final TextView title,query,reply;
  final ReplyScroll scroll;
  private final TextView voiceNote;
  private boolean answerMode;
  MiniCard(Context c) {
    super(c);setOrientation(VERTICAL);setPadding(dp(16),dp(10),dp(16),dp(12));
    setFocusable(false);setClickable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    GradientDrawable bg=new GradientDrawable();bg.setColor(0xF0121D2E);bg.setCornerRadius(dp(14));bg.setStroke(dp(1),0xFF54768B);setBackground(bg);
    title=line(c,14,0xFF8AD9FF,1);query=line(c,20,Color.WHITE,2);reply=line(c,18,0xFFBDCEDD,2);
    scroll=new ReplyScroll(c);scroll.setFocusable(false);scroll.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
    scroll.addView(reply,new ScrollView.LayoutParams(-1,-2));
    voiceNote=line(c,12,0xFFA8BCD2,1);voiceNote.setVisibility(GONE);
    addView(title);addView(query);addView(scroll,new LinearLayout.LayoutParams(-1,-2));addView(voiceNote);
  }
  private TextView line(Context c,int size,int color,int lines) {
    TextView v=new TextView(c);v.setTextSize(size);v.setTextColor(color);v.setMaxLines(lines);v.setEllipsize(TextUtils.TruncateAt.END);
    v.setPadding(0,dp(3),0,0);v.setFocusable(false);return v;
  }
  void content(String state,String request,String result) {
    content(state,request,result,false);
  }
  void content(String state,String request,String result,boolean reading) {
    answerMode=reading;reply.setMaxLines(reading?Integer.MAX_VALUE:2);
    scroll.maxHeight=reading?Math.round(getResources().getDisplayMetrics().heightPixels*.40f):dp(64);
    if(!TextUtils.equals(reply.getText(),result)){reply.setText(result);scroll.scrollTo(0,0);}
    String hint=reading?"↑↓ текст · Назад — закрыть":state.equals("слушаю")||state.equals("выполняю")?"Mi — отмена":state.equals("назначение Mi")?"45 секунд":"Mi — команда";
    title.setText("ТВ-помощник · "+state+"     "+hint);query.setText(request);
  }
  void voiceNotice(String text){voiceNote.setText(text);voiceNote.setVisibility(text.isEmpty()?GONE:VISIBLE);}
  private int naturalHeight(int width){
    int textWidth=Math.max(dp(40),width-getPaddingLeft()-getPaddingRight());
    return new StaticLayout(reply.getText(),reply.getPaint(),textWidth,Layout.Alignment.ALIGN_NORMAL,1f,0,true).getHeight();
  }
  int preferredWidth(){
    int screen=getResources().getDisplayMetrics().widthPixels;
    int width=Math.min(dp(560),Math.round(screen*.64f));
    return answerMode&&naturalHeight(width)>dp(100)?Math.min(dp(720),Math.round(screen*.78f)):width;
  }
  boolean longAnswer(){return answerMode&&naturalHeight(preferredWidth())>dp(52);}
  boolean canScrollReply(){return answerMode&&naturalHeight(preferredWidth())>scroll.maxHeight;}
  void scrollReply(int direction){scroll.scrollBy(0,direction*dp(64));}
  int replyScrollY(){return scroll.getScrollY();}
  @Override protected void onMeasure(int w,int h){
    int cap=Math.round(getResources().getDisplayMetrics().heightPixels*.65f);
    if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED)cap=Math.min(cap,MeasureSpec.getSize(h));
    super.onMeasure(w,MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST));
  }
  final class ReplyScroll extends ScrollView {
    int maxHeight=dp(64);
    ReplyScroll(Context c){super(c);setFillViewport(false);}
    @Override protected void onMeasure(int w,int h){
      int cap=maxHeight;if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED)cap=Math.min(cap,MeasureSpec.getSize(h));
      super.onMeasure(w,MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST));
    }
  }
  int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
}
