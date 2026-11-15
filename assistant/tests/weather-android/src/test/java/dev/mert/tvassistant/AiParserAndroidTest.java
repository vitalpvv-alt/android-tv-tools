package dev.mert.tvassistant;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE)
public class AiParserAndroidTest {
  private static JSONObject response(String text){return Json.obj("choices",Json.arr(Json.obj("finish_reason","stop","message",Json.obj("role","assistant","content",text))));}
  @Test public void androidJsonNormalizesOnlyUnparameterizedReadOnlySteps() throws Exception {
    for(String tool:new String[]{"device_info","clock","list_apps"}){
      FreeCommands.Plan p=FreeCommands.parse(response("{\"steps\":[{\"tool\":\""+tool+"\"}],\"reply\":\"\"}"),new JSONArray());
      assertEquals(0,p.steps.getJSONObject(0).getJSONObject("args").length());
    }
  }
  @Test public void nativeProbeAcceptsTheMissingEmptyObjectAndExecutesNothing() throws Exception {
    String model=ModelConfig.XKIRO_MODELS[2];
    JSONObject report=XkiroProbe.run(model,new JSONArray(),new XkiroProbe.Gateway(){
      public JSONObject catalog(){return Json.obj("data",Json.arr(Json.obj("id",model,"access_tier","free","pricing",Json.obj("currency","USD","unit","per_1m_tokens","input",0,"output",0))));}
      public JSONObject infer(JSONObject request){return response("{\"steps\":[{\"tool\":\"device_info\"},{\"tool\":\"volume\",\"args\":{\"action\":\"get\"}}],\"reply\":\"\"}");}
    });
    assertTrue(report.getBoolean("plan_ok"));assertEquals(0,report.getInt("executed_actions"));
  }
  @Test public void androidJsonNeverDefaultsVolumeNavigationOrApplicationArguments() throws Exception {
    for(String tool:new String[]{"volume","open_app","navigate"}){
      try{FreeCommands.parse(response("{\"steps\":[{\"tool\":\"device_info\"},{\"tool\":\""+tool+"\"}]}"),new JSONArray());fail("Missing required args accepted");}
      catch(IllegalArgumentException expected){}
    }
  }
}
