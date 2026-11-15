package dev.mert.tvassistant;

import java.io.*;
import org.json.*;

/** One explicitly requested native inference check. Never invokes any TV tool. */
final class XkiroProbe {
  interface Gateway {
    JSONObject catalog() throws Exception;
    JSONObject infer(JSONObject body) throws Exception;
  }
  static JSONObject run(String model, JSONArray apps, Gateway gateway) {
    long start=System.nanoTime(); int http=0; String kind="io"; boolean inference=false, planOk=false;
    try {
      FreeCommands.requireFreeXkiroModel(model,gateway.catalog());
      JSONObject request=FreeCommands.request("Узнай модель телевизора и текущую громкость. Ничего не меняй.",apps,model,"xkiro");
      JSONObject response=gateway.infer(request); http=200;
      FreeCommands.Plan plan=FreeCommands.parse(response,apps); inference=true;
      boolean device=false, volume=false;
      if(plan.steps.length()==2) for(int i=0;i<plan.steps.length();i++) {
        JSONObject step=plan.steps.getJSONObject(i);
        if(step.getString("tool").equals("device_info")) device=true;
        if(step.getString("tool").equals("volume") && step.getJSONObject("args").optString("action").equals("get")) volume=true;
      }
      planOk=device && volume; kind=planOk?"ok":"unexpected_plan";
    } catch(Net.InferenceHttpError e) { http=e.status; kind="http_error"; }
    catch(java.net.SocketTimeoutException e) { kind="timeout"; }
    catch(InterruptedIOException e) { kind="cancelled"; }
    catch(IllegalArgumentException e) { kind=http==200?"invalid_response":"unavailable_free_model"; }
    catch(org.json.JSONException e) { kind="invalid_response"; }
    catch(Exception e) { kind="io"; }
    return Json.obj("provider","xkiro","model",model,"http",http,"inference_ok",inference,"plan_ok",planOk,
        "kind",kind,"executed_actions",0,"elapsed_ms",(System.nanoTime()-start)/1000000);
  }
}
