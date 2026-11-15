package dev.mert.tvassistant;

import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Named-title requests and installed-app routing; no model or guessed package names. */
final class MediaSearch {
  static final String WINK="ru.rt.video.app.tv", ZONA="mobi.zona";
  static String normalized(String text) {
    return text==null?"":text.toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("[^\\p{L}\\p{N}]","");
  }
  static String installed(String requested,JSONArray apps) {
    String q=normalized(requested);if(q.isEmpty())return null;
    Set<String> matches=new LinkedHashSet<>();
    for(int i=0;i<apps.length();i++){
      JSONObject a=apps.optJSONObject(i);if(a==null)continue;
      String p=a.optString("package"), label=normalized(a.optString("label"));
      if(requested.equals(p))return p;
      if(q.equals(label)||alias(q,p))matches.add(p);
    }
    return matches.size()==1?matches.iterator().next():null;
  }
  private static boolean alias(String q,String p){
    if(p.equals(WINK))return q.matches("wink|винк|винке");
    if(p.equals(ZONA))return q.matches("zona|зона|зоне");
    if(p.toLowerCase(Locale.ROOT).contains("youtube"))return q.matches("youtube|ютуб|ютубе|ютюб|ютюбе");
    return p.equals("com.netflix.ninja")&&q.matches("netflix|нетфликс|нетфликсе");
  }
  static String preferred(JSONArray apps,String foreground){
    if((WINK.equals(foreground)||ZONA.equals(foreground))&&installed(foreground,apps)!=null)return foreground;
    for(String p:new String[]{WINK,ZONA,"youtube"}){String actual=installed(p,apps);if(actual!=null)return actual;}
    throw new IllegalArgumentException("Не найдено приложение для поиска. Открой нужное приложение и назови его в команде.");
  }
  static LocalCommands.Call parse(String command,JSONArray apps){
    if(command==null)return null;
    String raw=command.trim().replaceAll("[.!?]+$","").trim();
    raw=raw.replaceFirst("(?iu)^(?:пожалуйста|давай)[, ]+","");
    Matcher m=Pattern.compile("(?iu)^(?:найди(?:те)?|найти|поищи(?:те)?|поиск|find|search(?: for)?|look for)\\s+(?:(?:мне|нам|пожалуйста)[, ]+)*(.+)$").matcher(raw);
    if(!m.matches())return null;
    String title=m.group(1).trim();
    if(title.matches("(?isu).*(?:\\s+(?:и|затем|потом|and|then)\\s+)(?:открой|включи|выключи|удали|купи|сделай|поставь|open|play|delete|set|turn)\\b.*"))return null;
    if(title.matches("(?iu)^(?:погоду|информацию|приложения|настройки|weather|apps|settings|information|the web for|the internet for)\\b.*"))return null;
    String app=null;
    Matcher suffix=Pattern.compile("(?iu)^(.+?)\\s+(?:в|на|in|on|using)\\s+(?:приложении\\s+)?(.+)$").matcher(title);
    // Try the final suffixes; a title such as «Игра в кальмара» must remain intact.
    for(int split=title.length()-1;split>0;split--)if(Character.isWhitespace(title.charAt(split))){
      String prefix=title.substring(0,split).trim(), tail=title.substring(split+1).trim();
      Matcher prep=Pattern.compile("(?iu)^(.+)\\s+(?:в|на|in|on|using)(?:\\s+приложении)?$").matcher(prefix);
      if(prep.matches()){String p=installed(tail,apps);if(p!=null){app=p;title=prep.group(1).trim();break;}}
    }
    // An explicitly named absent app is left to the normal clarifying path, never invented.
    if(app==null&&suffix.matches()&&suffix.group(2).matches("(?iu)(?:wink|винк[е]?|zona|зон[ае]|youtube|ют[ую]б[е]?|netflix|нетфликс[е]?)"))return null;
    if(app==null&&title.matches("(?isu).*\\s(?:в|на)\\s+приложении\\s+.+"))return null;
    String kind="any";
    if(title.matches("(?iu)(?:the\\s+)?(?:сериал|фильм|мультфильм|series|tv show|movie|film)"))return null;
    Matcher type=Pattern.compile("(?iu)^(?:the\\s+)?(сериал|фильм|мультфильм|series|tv show|movie|film)\\s+(.+)$").matcher(title);
    if(type.matches()){kind=type.group(1).toLowerCase(Locale.ROOT).matches("сериал|series|tv show")?"series":"movie";title=type.group(2).trim();}
    title=title.replaceAll("^[«\"“]+|[»\"”]+$","").trim();
    if(title.isEmpty()||title.length()>200||title.matches("(?s).*[\\x00-\\x1f\\x7f].*"))return null;
    return new LocalCommands.Call(app==null?"search_tv":"search_app",
        app==null?Json.obj("query",title,"kind",kind):Json.obj("app",app,"query",title,"kind",kind));
  }
}
