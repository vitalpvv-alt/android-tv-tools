package dev.mert.tvassistant;

import org.json.*;

/** Offline regressions from the observed Wink/Zona controls, never a claim of TV execution. */
public final class SearchChecks {
  static JSONArray apps(){return Json.arr(Json.obj("label","Wink - ТВ и кино","package",MediaSearch.WINK),Json.obj("label","ZONA","package",MediaSearch.ZONA));}
  static JSONObject screen(String pkg,JSONObject...nodes){return Json.obj("package",pkg,"snapshot","fixture", "nodes",Json.arr((Object[])nodes));}
  static JSONObject n(int id,String cls,String text,String desc,boolean click,int...b){
    JSONArray bounds=new JSONArray();for(int x:b)bounds.put(x);
    return Json.obj("id",id,"class","android.widget."+cls,"text",text,"description",desc,"clickable",click,"bounds",bounds);
  }
  static JSONObject wink(String query){return screen(MediaSearch.WINK,
      n(1,"EditText",query,"",false,112,80,1808,184),
      n(2,"TextView","Результаты поиска","",true,252,692,566,773),
      n(3,"TextView","Сериалы","",true,793,692,962,773),
      n(4,"ViewGroup","","Шерлок и Дочь",true,112,944,368,1080));}
  static JSONObject zona()throws Exception{return screen(MediaSearch.ZONA,
      n(1,"Button","","Голосовой поиск",true,192,48,304,160),
      n(2,"EditText","[password field]","",true,376,56,1680,152).put("password",true),
      n(3,"TextView","Поиск","",false,376,71,540,137),
      n(4,"Button","","Поиск",true,1728,48,1840,160));}
  public static void main(String[] args)throws Exception{
    HostChecks.check("generic title search from Home is local, with the original Russian/English title",()->{
      for(String phrase:new String[]{"Найди сериал Шерлок","Find the series Шерлок","Пожалуйста, найди мне сериал «Шерлок»"}){
        LocalCommands.Call c=LocalCommands.parse(phrase,apps());HostChecks.yes(c.tool.equals("search_tv")&&c.args.getString("query").equals("Шерлок")&&c.args.getString("kind").equals("series"));
      }
      LocalCommands.Call c=LocalCommands.parse("Найди фильм Любовь и голуби",apps());HostChecks.yes(c.args.getString("query").equals("Любовь и голуби"));
      HostChecks.yes(LocalCommands.parse("Найди сериал Игра в кальмара",apps()).args.getString("query").equals("Игра в кальмара"));
    });
    HostChecks.check("explicit app aliases route only to installed packages",()->{
      HostChecks.yes(LocalCommands.parse("Найди сериал Шерлок в Винке",apps()).args.getString("app").equals(MediaSearch.WINK));
      HostChecks.yes(LocalCommands.parse("Find Sherlock in Zona",apps()).args.getString("app").equals(MediaSearch.ZONA));
      HostChecks.yes(LocalCommands.parse("Find Sherlock in Netflix",apps())==null);
      HostChecks.yes(MediaSearch.preferred(apps(),"com.gathin.tvhome").equals(MediaSearch.WINK));
      HostChecks.yes(MediaSearch.preferred(apps(),MediaSearch.ZONA).equals(MediaSearch.ZONA));
      HostChecks.rejects(()->MediaSearch.preferred(new JSONArray(),"home"));
    });
    HostChecks.check("negation, missing title and compound actions do not run a partial media search",()->{
      for(String phrase:new String[]{"Не ищи сериал Шерлок","Найди сериал","Найди сериал Шерлок и выключи звук","Find Sherlock in Wink and turn volume up"})HostChecks.yes(LocalCommands.parse(phrase,apps())==null);
      HostChecks.yes(LocalCommands.parse("search the web for Mars",apps()).tool.equals("web_search"));
    });
    HostChecks.check("Cyrillic installed labels never collapse to an empty match",()->{
      HostChecks.yes(Tools.normalize("Ёлки").equals("елки"));HostChecks.yes(!Tools.normalize("Кино").equals(Tools.normalize("Зона")));
      JSONArray duplicate=Json.arr(Json.obj("label","Кино","package","one"),Json.obj("label","Кино","package","two"));
      HostChecks.yes(MediaSearch.installed("Кино",duplicate)==null&&MediaSearch.installed("!!!",duplicate)==null);
      JSONObject reply=HostChecks.freeReply("{\"tool\":\"search_app\",\"args\":{\"app\":\"Wink - ТВ и кино\",\"query\":\"Шерлок\"}}");
      HostChecks.yes(FreeCommands.parse(reply,apps()).steps.getJSONObject(0).getJSONObject("args").getString("app").equals(MediaSearch.WINK));
      HostChecks.rejects(()->FreeCommands.parse(HostChecks.freeReply("{\"tool\":\"search_app\",\"args\":{\"app\":\"invented.app\",\"query\":\"Шерлок\"}}"),apps()));
    });
    HostChecks.check("search receipt requires the right app, current query and actual result cards",()->{
      JSONObject observed=SearchUi.receipt(wink("Sherlock"),MediaSearch.WINK,"Sherlock");HostChecks.yes(observed.getBoolean("search_verified")&&observed.getJSONArray("titles").getString(0).equals("Шерлок и Дочь"));
      HostChecks.yes(!SearchUi.receipt(wink("old query"),MediaSearch.WINK,"Sherlock").getBoolean("search_verified"));
      HostChecks.yes(!SearchUi.receipt(wink("Sherlock"),MediaSearch.ZONA,"Sherlock").getBoolean("search_verified"));
      JSONObject home=wink("Sherlock");home.getJSONArray("nodes").remove(1);HostChecks.yes(!SearchUi.receipt(home,MediaSearch.WINK,"Sherlock").getBoolean("search_verified"));
      JSONObject loading=wink("Sherlock");loading.getJSONArray("nodes").put(n(5,"ProgressBar","","",false,0,0,50,50));HostChecks.yes(!SearchUi.receipt(loading,MediaSearch.WINK,"Sherlock").getBoolean("search_verified"));
    });
    HostChecks.check("empty results and unverified requests produce honest Russian answers",()->{
      JSONObject empty=wink("Sherlock");empty.getJSONArray("nodes").remove(3);empty.getJSONArray("nodes").put(n(5,"TextView","Ничего не найдено","",false,100,400,600,500));
      JSONObject receipt=SearchUi.receipt(empty,MediaSearch.WINK,"Sherlock").put("search_requested",true).put("app_name","Wink");
      HostChecks.yes(receipt.getBoolean("no_results")&&LocalFormatter.format("search_tv",receipt).contains("ничего не найдено"));
      HostChecks.yes(LocalFormatter.format("search_tv",Json.obj("search_requested",true,"search_verified",false,"query","Шерлок","app_name","Wink")).contains("не удалось проверить"));
    });
    HostChecks.check("Zona's write-only search exception rejects other apps, login forms and extra fields",()->{
      HostChecks.yes(SearchUi.zonaForm(zona())!=null);
      JSONObject wrong=zona().put("package","password.manager");HostChecks.yes(SearchUi.zonaForm(wrong)==null);
      JSONObject login=zona();login.getJSONArray("nodes").put(n(9,"TextView","Введите пароль","",false,10,200,500,300));HostChecks.yes(SearchUi.zonaForm(login)==null);
      JSONObject two=zona();two.getJSONArray("nodes").put(n(9,"EditText","","",true,10,200,500,300));HostChecks.yes(SearchUi.zonaForm(two)==null);
      JSONObject reversed=zona();reversed.getJSONArray("nodes").getJSONObject(1).put("bounds",Json.arr(10,200,500,300));HostChecks.yes(SearchUi.zonaForm(reversed)==null);
    });
    System.out.println("All "+HostChecks.passed+" search checks passed; no TV or network calls.");
  }
}
