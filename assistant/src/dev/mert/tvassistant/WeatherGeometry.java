package dev.mert.tvassistant;

/** Pure placement logic: screen bounds and launcher controls are exclusion zones. */
final class WeatherGeometry {
  static final class Box {
    final int left,top,right,bottom;
    Box(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
    int width(){return right-left;} int height(){return bottom-top;}
    boolean intersects(Box other){return left<other.right&&right>other.left&&top<other.bottom&&bottom>other.top;}
  }
  static Box place(int width,int height,float density,int headerBottom,int dockTop,
      Box hero,int alignment,boolean large) {
    if(width<=0||height<=0||density<=0)return null;
    int margin=Math.round(56*density),gap=Math.round(24*density);
    int w=Math.min(Math.round((large?480:426)*density),(int)(width*.49));
    int h=Math.round(w*130f/426);
    int x=alignment==0?margin:alignment==1?(width-w)/2:width-margin-w;
    int safeTop=Math.max(headerBottom+Math.round(14*density),Math.round(100*density));
    int bottom=Math.min(dockTop-gap,height-margin);
    if(hero!=null&&x<hero.right&&x+w>hero.left)bottom=Math.min(bottom,hero.top-Math.round(14*density));
    int y=bottom-h;
    if(w<250*density||x<0||x+w>width||y<safeTop||bottom>height)return null;
    return new Box(x,y,x+w,bottom);
  }
}
