package dev.mert.tvassistant;

import android.content.Context;
import android.graphics.*;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import java.util.Locale;

/** One small, static canvas: no animation, bitmaps, WebView or input interception. */
final class WeatherCardView extends View {
  private static final int WHITE=0xfff4f7fc,MUTED=0xffb5c6d9,BLUE=0xff91d6fc,AMBER=0xffffcf7b;
  private final TextPaint pen=new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private WeatherData.Place place=WeatherData.PLACES[0];
  private WeatherData.Reading reading;
  private Float room;
  private String status="Загружаю погоду…";
  private long now;
  WeatherCardView(Context context) {
    super(context);setFocusable(false);setClickable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    setLayerType(View.LAYER_TYPE_SOFTWARE,null);
  }
  void content(WeatherData.Place place,WeatherData.Reading data,Float room,String status,long now) {
    this.place=place;this.reading=data!=null&&data.usable(now)?data:null;
    this.room=room;this.status=status;this.now=now;invalidate();
  }
  @Override protected void onDraw(Canvas c) {
    super.onDraw(c);
    c.save(); c.scale(getWidth()/426f,getHeight()/130f);
    pen.setStyle(Paint.Style.FILL);pen.setColor(0xf20d192a);
    c.drawRoundRect(0,0,426,130,16,16,pen);
    pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(1);pen.setColor(0xff34485e);
    c.drawRoundRect(.5f,.5f,425.5f,129.5f,16,16,pen);pen.setStyle(Paint.Style.FILL);
    text(c,place.name,20,24,15,WHITE,205,true);
    boolean old=reading!=null&&reading.stale(now);
    textRight(c,reading==null?"":old?"Данные "+reading.dateTime():(reading.source.equals("MET Norway")?"Прогноз ":"Обновлено ")+reading.time(),406,23,10,old?AMBER:MUTED);
    if(reading==null) {
      text(c,"—°",21,76,40,MUTED,100,false);
      text(c,status,116,58,15,WHITE,290,false);
      text(c,"Нужен доступ к интернету",116,79,11,MUTED,290,false);
    } else {
      icon(c,reading.code,reading.day,42,58);
      text(c,WeatherData.degrees(reading.temperature),73,77,40,WHITE,126,false);
      text(c,WeatherData.condition(reading.code,reading.day),204,51,14,WHITE,202,false);
      String feels=reading.feels==null?"На текущий час":("Ощущается как "+WeatherData.degrees(reading.feels));
      text(c,feels,204,73,12,MUTED,202,false);
    }
    String details="";
    if(reading!=null) {
      if(reading.wind!=null)details="Ветер "+String.format(Locale.ROOT,"%.1f",reading.wind).replace('.',',')+" м/с";
      if(reading.humidity!=null)details+=(details.isEmpty()?"":"    ·    ")+"Влажность "+Math.round(reading.humidity)+"%";
    } else details="Обновление каждые 30 минут";
    text(c,details,20,97,11,MUTED,386,false);
    pen.setColor(0xff2a3c50);pen.setStrokeWidth(1);c.drawLine(20,104,406,104,pen);
    int next=reading==null?-1:reading.tomorrow(now);
    String source=reading==null?"Open-Meteo · MET Norway":reading.source;
    String tomorrow=next<0?source:("Завтра "+WeatherData.degrees(reading.highs[next])+" / "+WeatherData.degrees(reading.lows[next]));
    text(c,tomorrow,20,121,11,WHITE,room==null?238:214,false);
    textRight(c,room==null?(next<0?"":source):"В комнате "+WeatherData.degrees(room),406,121,11,room==null?MUTED:BLUE);
    c.restore();
  }
  private void text(Canvas c,String s,float x,float y,float size,int color,float width,boolean bold) {
    pen.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
    pen.setTextSize(size);pen.setColor(color);
    c.drawText(TextUtils.ellipsize(s,pen,width,TextUtils.TruncateAt.END).toString(),x,y,pen);
  }
  private void textRight(Canvas c,String s,float x,float y,float size,int color) {
    pen.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));pen.setTextSize(size);pen.setColor(color);
    c.drawText(s,x-pen.measureText(s),y,pen);
  }
  private void icon(Canvas c,int code,boolean day,float x,float y) {
    c.save();c.translate(x,y);
    if(code<=2) {
      pen.setColor(day?AMBER:BLUE); c.drawCircle(-3,-2,12,pen);
      if(day) {
        pen.setStrokeWidth(2);pen.setStrokeCap(Paint.Cap.ROUND);
        for(int i=0;i<8;i++){c.save();c.translate(-3,-2);c.rotate(i*45);c.drawLine(0,-17,0,-21,pen);c.restore();}
      } else {pen.setColor(0xff0d192a);c.drawCircle(3,-6,11,pen);}
      if(code==0){c.restore();return;}
    }
    pen.setColor(code==0?AMBER:0xffbad3e7);
    c.drawCircle(-10,4,9,pen);c.drawCircle(0,-1,12,pen);c.drawCircle(12,5,9,pen);c.drawRoundRect(-13,4,15,13,5,5,pen);
    pen.setColor(BLUE);pen.setStrokeWidth(2);pen.setStrokeCap(Paint.Cap.ROUND);
    if((code>=51&&code<=69)||(code>=80&&code<=82))for(int i=-1;i<=1;i++)c.drawLine(i*10+1,18,i*10-3,25,pen);
    if((code>=71&&code<=77)||code==85||code==86)for(int i=-1;i<=1;i++){
      c.drawLine(i*10-3,22,i*10+3,22,pen);c.drawLine(i*10,19,i*10,25,pen);
    }
    if(code>=95&&code<=99){pen.setColor(AMBER);Path p=new Path();p.moveTo(1,14);p.lineTo(-7,25);p.lineTo(0,24);p.lineTo(-2,33);p.lineTo(9,19);p.lineTo(2,20);p.close();c.drawPath(p,pen);}
    if(code==45||code==48){pen.setColor(MUTED);c.drawLine(-17,20,17,20,pen);c.drawLine(-13,25,13,25,pen);}
    c.restore();
  }
}
