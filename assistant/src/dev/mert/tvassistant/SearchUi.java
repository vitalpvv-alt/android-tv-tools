package dev.mert.tvassistant;

import java.util.*;
import org.json.*;

/** Search-only UI observations. Never treats an app launch or its recommendations as results. */
final class SearchUi {
  private static JSONArray nodes(JSONObject screen){JSONArray n=screen.optJSONArray("nodes");return n==null?new JSONArray():n;}
  private static String label(JSONObject n){return (n.optString("text")+" "+n.optString("description")).trim();}
  private static boolean edit(JSONObject n){return n.optString("class").endsWith("EditText")||n.optBoolean("editable");}
  private static boolean exact(JSONObject n,String text){return n.optString("text").equalsIgnoreCase(text)||n.optString("description").equalsIgnoreCase(text);}
  static int category(JSONObject screen,String kind){
    if(!MediaSearch.WINK.equals(screen.optString("package"))||kind.equals("any"))return 0;
    String text=kind.equals("series")?"Сериалы":"Фильмы";int id=0;
    for(int i=0;i<nodes(screen).length();i++){
      JSONObject n=nodes(screen).optJSONObject(i);
      if(n.optBoolean("clickable")&&exact(n,text)){if(id!=0)return 0;id=n.optInt("id");}
    }
    return id;
  }
  static int searchEntry(JSONObject screen,String pkg){
    if(!pkg.equals(screen.optString("package")))return 0;int id=0;
    for(int i=0;i<nodes(screen).length();i++){
      JSONObject n=nodes(screen).optJSONObject(i);
      if(!edit(n)&&exact(n,"Поиск")){if(id!=0)return 0;id=n.optInt("id");}
    }
    return id;
  }
  /** Zona's observed search bar reports the password flag. This exception is write-only,
   * exact-package, and requires the observed search toolbar, not a generic password field. */
  static JSONObject zonaForm(JSONObject screen){
    if(!MediaSearch.ZONA.equals(screen.optString("package")))return null;
    JSONObject field=null,voice=null,submit=null;int edits=0;
    JSONArray list=nodes(screen);
    for(int i=0;i<list.length();i++){
      JSONObject n=list.optJSONObject(i);String text=label(n).toLowerCase(Locale.ROOT);
      if(!n.optBoolean("password")&&text.matches("(?su).*(парол|войти|вход в|авторизац|login|sign in|password).*"))return null;
      if(edit(n)){field=n;edits++;}
      if(n.optBoolean("clickable")&&n.optString("class").endsWith("Button")){
        if(exact(n,"Голосовой поиск"))voice=n;
        if(exact(n,"Поиск"))submit=n;
      }
    }
    if(edits!=1||field==null||voice==null||submit==null)return null;
    JSONArray f=field.optJSONArray("bounds"),v=voice.optJSONArray("bounds"),s=submit.optJSONArray("bounds");
    if(f==null||v==null||s==null||f.length()!=4||v.length()!=4||s.length()!=4)return null;
    // Unique text field between the microphone and search buttons in one top toolbar.
    if(v.optInt(2)>f.optInt(0)||f.optInt(2)>s.optInt(0)||f.optInt(1)<v.optInt(1)-30
        ||f.optInt(3)>v.optInt(3)+30||f.optInt(1)>s.optInt(3)||f.optInt(3)<s.optInt(1))return null;
    return Json.obj("field",field.optInt("id"),"submit",submit.optInt("id"));
  }
  static JSONObject receipt(JSONObject screen,String pkg,String query){
    JSONObject r=Json.obj("package",pkg,"query",query,"search_verified",false,"titles",new JSONArray());
    if(!pkg.equals(screen.optString("package")))return r;
    boolean querySeen=false,heading=false,empty=false,loading=false;
    Set<String> titles=new LinkedHashSet<>();
    for(int i=0;i<nodes(screen).length();i++){
      JSONObject n=nodes(screen).optJSONObject(i);String text=label(n), lower=text.toLowerCase(Locale.ROOT);
      if(n.optString("class").contains("ProgressBar"))loading=true;
      if(!n.optBoolean("password")&&edit(n)&&MediaSearch.normalized(n.optString("text")).equals(MediaSearch.normalized(query)))querySeen=true;
      if(lower.contains("результаты поиска")||lower.startsWith("найдено")){
        heading=true;if(lower.contains(query.toLowerCase(Locale.ROOT)))querySeen=true;
      }
      if(lower.matches("(?su).*(ничего не найдено|не найдено результатов|по вашему запросу ничего|нет результатов|no results).*"))empty=true;
      JSONArray b=n.optJSONArray("bounds");String desc=n.optString("description").trim();
      if(n.optBoolean("clickable")&&!n.optBoolean("password")&&b!=null&&b.length()==4
          &&b.optInt(2)-b.optInt(0)>=100&&b.optInt(3)-b.optInt(1)>=75
          &&(n.optString("class").endsWith("ViewGroup")||n.optString("class").endsWith("ImageView"))
          &&desc.length()>1&&!desc.matches("(?iu)поиск|все|фильмы|сериалы|детям|назад|главная|меню|избранное"))titles.add(desc);
    }
    JSONArray results=new JSONArray();for(String title:titles){if(results.length()==6)break;results.put(title);}
    boolean verified=querySeen&&heading&&!loading&&(empty||results.length()>0);
    return rPut(r,"search_verified",verified,"query_visible",querySeen,"results_heading",heading,
        "no_results",verified&&empty,"titles",verified?results:new JSONArray());
  }
  private static JSONObject rPut(JSONObject result,Object...pairs){for(int i=0;i<pairs.length;i+=2)try{result.put((String)pairs[i],pairs[i+1]);}catch(JSONException e){throw new IllegalArgumentException(e);}return result;}
}
