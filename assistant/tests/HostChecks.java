package dev.mert.tvassistant;

import java.io.*;
import org.json.*;

/** Offline JVM checks. No Android activity, TV, account, or inference calls. */
public final class HostChecks {
  interface Check { void run() throws Exception; }
  static int passed;
  static void check(String name, Check check) throws Exception {
    check.run(); passed++; System.out.println("PASS " + name);
  }
  static String repeated(char c, int n) { char[] a = new char[n]; java.util.Arrays.fill(a, c); return new String(a); }
  static void yes(boolean value) { if (!value) throw new AssertionError(); }
  static void rejects(Check check) throws Exception {
    try { check.run(); } catch (Exception expected) { return; }
    throw new AssertionError("Expected rejection");
  }
  static Net.Events events(boolean cancelled) {
    return new Net.Events() {
      public void event(JSONObject event) {}
      public boolean cancelled() { return cancelled; }
    };
  }
  static ActionPlan.Runner runner(int[] calls, JSONObject response, boolean cancelled) {
    return new ActionPlan.Runner() {
      public void validate(String tool, JSONObject args) {}
      public JSONObject run(String tool, JSONObject args) { calls[0]++; return response; }
      public boolean cancelled() { return cancelled; }
    };
  }
  static JSONArray plan() {
    return Json.arr(Json.obj("tool", "clock", "args", Json.obj()),
        Json.obj("tool", "calculate", "args", Json.obj("expression", "7*8")));
  }
  static JSONArray search(String query, String heading) {
    return Json.arr(
        Json.obj("tool", "ui_target", "args", Json.obj("scope", "public", "context", "https://example.com", "label", "Search", "action", "click")),
        Json.obj("tool", "ui_target", "args", Json.obj("scope", "public", "context", "https://example.com", "label", "Title", "action", "type", "text", query)),
        Json.obj("tool", "ui_target", "args", Json.obj("scope", "public", "context", "https://example.com", "label", heading, "match", "text", "action", "wait")));
  }
  static JSONObject searchResult() {
    return Json.obj("stopped_early", false, "completed_steps", 3,
        "results", Json.arr(Json.obj("performed", true), Json.obj("performed", true, "text_verified", true), Json.obj("verified", true)));
  }
  static JSONObject seriesFixture() throws Exception {
    return Json.obj("id", "tt0121955", "type", "series", "name", "South Park", "videos", Json.arr(
        Json.obj("id", "catalog:real-episode", "season", 3, "episode", 5, "name", "Tweek vs. Craig"),
        Json.obj("id", "catalog:other", "season", 3, "episode", 6, "name", "Other episode")));
  }
  static JSONArray tvApps() {
    return Json.arr(Json.obj("label", "Wink", "package", "ru.rt.video.app.tv"),
        Json.obj("label", "YouTube", "package", "com.google.android.youtube.tv"));
  }
  static JSONObject freeReply(String content) {
    return Json.obj("choices", Json.arr(Json.obj("finish_reason", "stop", "message",
        Json.obj("role", "assistant", "content", content))));
  }

  static final class DiagnosticHttp extends java.net.HttpURLConnection {
    final int code; final String type, body; boolean disconnected;
    DiagnosticHttp(String url, int code, String type, String body) throws Exception {
      super(new java.net.URL(url)); this.code=code; this.type=type; this.body=body;
    }
    public void disconnect() { disconnected=true; }
    public boolean usingProxy() { return false; }
    public void connect() {}
    public int getResponseCode() { return code; }
    public String getContentType() { return type; }
    public InputStream getInputStream() throws IOException { return new ByteArrayInputStream(body.getBytes("UTF-8")); }
    public InputStream getErrorStream() { try { return getInputStream(); } catch(Exception e) { throw new AssertionError(e); } }
  }

  public static void main(String[] args) throws Exception {
    check("zero-argument model steps omit args without losing preflight or tools", () -> {
      FreeCommands.Plan p=FreeCommands.parse(freeReply("{\"steps\":[{\"tool\":\"device_info\"},{\"tool\":\"volume\",\"args\":{\"action\":\"get\"}}],\"reply\":\"\"}"),tvApps());
      yes(p.steps.length()==2&&p.steps.getJSONObject(0).getJSONObject("args").length()==0&&p.steps.getJSONObject(1).getJSONObject("args").getString("action").equals("get"));
    });
    check("legacy zero-argument responses are normalized only for the closed read-only set", () -> {
      for(String tool:new String[]{"clock","device_info","list_apps"})yes(FreeCommands.parse(freeReply("{\"tool\":\""+tool+"\"}"),tvApps()).steps.getJSONObject(0).getJSONObject("args").length()==0);
      rejects(()->FreeCommands.parse(freeReply("{\"tool\":\"delete_everything\"}"),tvApps()));
    });
    check("missing required arguments, null objects and extra fields still stop the whole plan", () -> {
      for(String tool:new String[]{"volume","navigate","open_app","search_app","playback","open_settings"})
        rejects(()->FreeCommands.parse(freeReply("{\"steps\":[{\"tool\":\"device_info\"},{\"tool\":\""+tool+"\"}],\"reply\":\"\"}"),tvApps()));
      rejects(()->FreeCommands.parse(freeReply("{\"tool\":\"clock\",\"args\":null}"),tvApps()));
      rejects(()->FreeCommands.parse(freeReply("{\"tool\":\"clock\",\"args\":{},\"code\":\"ignored\"}"),tvApps()));
    });
    check("native xKiro probe accepts a model omitting only the empty argument object", () -> {
      String model=ModelConfig.XKIRO_MODELS[0];
      JSONObject result=XkiroProbe.run(model,tvApps(),new XkiroProbe.Gateway(){
        public JSONObject catalog(){return Json.obj("data",Json.arr(Json.obj("id",model,"access_tier","free","pricing",Json.obj("currency","USD","unit","per_1m_tokens","input",0,"output",0))));}
        public JSONObject infer(JSONObject request){return freeReply("{\"steps\":[{\"tool\":\"device_info\"},{\"tool\":\"volume\",\"args\":{\"action\":\"get\"}}],\"reply\":\"\"}");}
      });
      yes(result.getBoolean("plan_ok")&&result.getBoolean("inference_ok")&&result.getInt("executed_actions")==0);
    });
    check("xKiro endpoint, model and key are bound to their own service", () -> {
      String key="sk-xt-"+repeated('e',48);
      yes(ModelConfig.endpoint("xkiro").equals("https://api.xkiro.com/v1"));
      for(String model:ModelConfig.XKIRO_MODELS) ModelConfig.validate("xkiro",model,key);
      rejects(()->ModelConfig.validate("xkiro","openai/gpt-6.1-sol",key));
      rejects(()->ModelConfig.validate("xkiro",ModelConfig.XKIRO_MODELS[0],"sk-or-v1-"+repeated('c',64)));
      KeyProvisioning.validate(Json.obj("provider","xkiro","model",ModelConfig.XKIRO_MODELS[0],"key",key));
      rejects(()->KeyProvisioning.validate(Json.obj("provider","unknown","model",ModelConfig.XKIRO_MODELS[0],"key",key)));
      rejects(()->KeyProvisioning.validate(Json.obj("provider","xkiro","model",ModelConfig.XKIRO_MODELS[0],"key",key,"endpoint","https://evil.example")));
    });
    check("xKiro request uses one bounded JSON plan and no paid routing settings", () -> {
      JSONObject request=FreeCommands.request("Покажи громкость",tvApps(),ModelConfig.XKIRO_MODELS[0],"xkiro");
      yes(request.getInt("max_tokens")==768 && request.getDouble("temperature")==0 && !request.getBoolean("stream"));
      yes(request.getString("reasoning_effort").equals("none") && !request.has("provider") && request.getJSONObject("response_format").getString("type").equals("json_object"));
      rejects(()->FreeCommands.request("Покажи громкость",tvApps(),"paid/model","xkiro"));
    });
    check("xKiro live catalog must show zero input and output prices and free access", () -> {
      String model=ModelConfig.XKIRO_MODELS[0];
      JSONObject item=Json.obj("id",model,"access_tier","free","pricing",Json.obj("currency","USD","unit","per_1m_tokens","input",0,"output",0));
      JSONObject catalog=Json.obj("data",Json.arr(item));FreeCommands.requireFreeXkiroModel(model,catalog);
      item.getJSONObject("pricing").put("output",0.01);rejects(()->FreeCommands.requireFreeXkiroModel(model,catalog));
      item.getJSONObject("pricing").put("output",0);item.put("access_tier","paid");rejects(()->FreeCommands.requireFreeXkiroModel(model,catalog));
      item.put("access_tier","free");rejects(()->FreeCommands.requireFreeXkiroModel(model,Json.obj("data",Json.arr(item,item))));
      rejects(()->FreeCommands.requireFreeXkiroModel(model,Json.obj("data",Json.arr())));
    });
    check("xKiro diagnostics authorize only usage metadata and omit identity and balance", () -> {
      String key="sk-xt-"+repeated('e',48);int[] calls={0};
      JSONObject report=OpenRouterDiagnostics.runFor("xkiro","xkiro",ModelConfig.XKIRO_MODELS[0],key,(url,token)->{
        calls[0]++;yes(url.equals("https://api.xkiro.com/v1/models")||url.equals("https://api.xkiro.com/v1/usage"));
        yes(url.endsWith("/usage")?key.equals(token):token==null);
        return new DiagnosticHttp(url,200,"application/json","{\"object\":\"usage\",\"free_tokens\":{\"remaining\":500000},\"user\":{\"email\":\"private@invalid\"},\"wallet\":{\"balance_usd\":\"4\"}}");
      },()->false);
      yes(calls[0]==2 && report.getJSONArray("probes").getJSONObject(1).getBoolean("recognized"));
      yes(!report.toString().contains(key) && !report.toString().contains("private@invalid") && !report.toString().contains("balance_usd"));
    });
    check("xKiro native probe accepts a read-only plan but never executes a TV tool", () -> {
      String model=ModelConfig.XKIRO_MODELS[0];int[] calls={0,0};
      XkiroProbe.Gateway gateway=new XkiroProbe.Gateway(){
        public JSONObject catalog(){calls[0]++;return Json.obj("data",Json.arr(Json.obj("id",model,"access_tier","free","pricing",Json.obj("currency","USD","unit","per_1m_tokens","input",0,"output",0))));}
        public JSONObject infer(JSONObject request){calls[1]++;return freeReply("{\"steps\":[{\"tool\":\"device_info\",\"args\":{}},{\"tool\":\"volume\",\"args\":{\"action\":\"get\"}}],\"reply\":\"\"}");}
      };
      JSONObject result=XkiroProbe.run(model,tvApps(),gateway);
      yes(calls[0]==1&&calls[1]==1&&result.getBoolean("plan_ok")&&result.getInt("executed_actions")==0);
    });
    check("xKiro native probe stops before inference when model is paid", () -> {
      JSONObject result=XkiroProbe.run(ModelConfig.XKIRO_MODELS[0],tvApps(),new XkiroProbe.Gateway(){
        public JSONObject catalog(){return Json.obj("data",Json.arr());}
        public JSONObject infer(JSONObject r){throw new AssertionError("Inference must not be called");}
      });
      yes(!result.getBoolean("inference_ok")&&!result.getBoolean("plan_ok")&&result.getString("kind").equals("unavailable_free_model"));
    });
    check("xKiro probe distinguishes a timeout from cancellation", () -> {
      JSONObject result=XkiroProbe.run(ModelConfig.XKIRO_MODELS[0],tvApps(),new XkiroProbe.Gateway(){
        public JSONObject catalog() throws Exception {throw new java.net.SocketTimeoutException("private");}
        public JSONObject infer(JSONObject r){throw new AssertionError();}
      });
      yes(result.getString("kind").equals("timeout") && !result.toString().contains("private"));
    });
    check("xKiro probe never claims success for a mutating, malformed or HTTP-failed response", () -> {
      String model=ModelConfig.XKIRO_MODELS[0];
      for(int mode=0;mode<3;mode++){
        final int m=mode; JSONObject result=XkiroProbe.run(model,tvApps(),new XkiroProbe.Gateway(){
          public JSONObject catalog(){return Json.obj("data",Json.arr(Json.obj("id",model,"access_tier","free","pricing",Json.obj("currency","USD","unit","per_1m_tokens","input",0,"output",0))));}
          public JSONObject infer(JSONObject r) throws Exception {if(m==2)throw new Net.InferenceHttpError(403,0);return freeReply(m==0?"{\"steps\":[{\"tool\":\"volume\",\"args\":{\"action\":\"mute\"}}],\"reply\":\"\"}":"not-json");}
        });
        yes(!result.getBoolean("plan_ok")&&result.getInt("executed_actions")==0);if(m==2)yes(result.getInt("http")==403);
      }
    });
    check("HTTP 401 and 403 are distinct and hints never echo server text", () -> {
      String secret="never-echo-this-key";
      String kind=OpenRouterDiagnostics.kind(401,"application/json",null,"{\"error\":{\"message\":\"No API key: "+secret+"\"}}");
      yes(kind.equals("missing_authorization"));
      String unauthorized=new Net.InferenceHttpError(401,0,kind).getMessage();
      String forbidden=new Net.InferenceHttpError(403,0,"forbidden").getMessage();
      yes(unauthorized.contains("HTTP 401") && !unauthorized.contains(secret) && forbidden.contains("HTTP 403") && !unauthorized.equals(forbidden));
    });
    check("diagnostics distinguish browser challenges, explicit region messages and generic forbidden", () -> {
      yes(OpenRouterDiagnostics.kind(403,"text/html",null,"<html>Just a moment cf-chl</html>").equals("browser_challenge"));
      yes(OpenRouterDiagnostics.kind(403,"text/plain","challenge","").equals("browser_challenge"));
      yes(OpenRouterDiagnostics.kind(403,"application/json",null,"{\"error\":{\"message\":\"Not available in your country\"}}").equals("region_restricted"));
      yes(OpenRouterDiagnostics.kind(403,"application/json",null,"{\"error\":{\"message\":\"Forbidden\"}}").equals("forbidden"));
    });
    check("diagnostics use only fixed GET endpoints and authorize only the key probe", () -> {
      String key="sk-or-v1-"+repeated('d',64); java.util.List<DiagnosticHttp> connections=new java.util.ArrayList<>();
      OpenRouterDiagnostics.Transport transport=(url,token)->{
        yes(url.equals("https://openrouter.ai/api/v1/models") || url.equals("https://openrouter.ai/api/v1/key"));
        yes(url.endsWith("/key") ? key.equals(token) : token==null);
        DiagnosticHttp c=new DiagnosticHttp(url,200,"application/json",url.endsWith("/key")?"{\"data\":{\"label\":\"private label\",\"limit\":123}}":"{\"data\":[]}");connections.add(c);return c;
      };
      JSONObject report=OpenRouterDiagnostics.run("openrouter","qwen/qwen3.5-9b",key,transport,()->false);
      yes(report.getJSONArray("probes").getJSONObject(1).getBoolean("recognized") && report.getString("key_fingerprint").matches("[a-f0-9]{12}"));
      yes(!report.toString().contains(key) && !report.toString().contains("private label") && !report.toString().contains("\"limit\""));
      yes(connections.size()==2);
      for(DiagnosticHttp c:connections) yes(c.getRequestMethod().equals("GET") && !c.getInstanceFollowRedirects() && c.getConnectTimeout()==8000 && c.getReadTimeout()==8000 && c.disconnected);
    });
    check("diagnostics reject a misleading HTTP 200 without key metadata", () -> {
      JSONObject p=OpenRouterDiagnostics.probe("key","test",(url,token)->new DiagnosticHttp(url,200,"application/json","{\"ok\":true}"),()->false);
      yes(!p.getBoolean("recognized") && p.getString("kind").equals("unexpected_response"));
      rejects(()->OpenRouterDiagnostics.probe("https://evil.example","test",(u,t)->{throw new AssertionError();},()->false));
    });
    check("diagnostic DNS, TLS and timeout failures are sanitized", () -> {
      Exception[] errors={new java.net.UnknownHostException("secret"),new javax.net.ssl.SSLException("secret"),new java.net.SocketTimeoutException("secret")};
      String[] kinds={"dns","tls","timeout"};
      for(int i=0;i<errors.length;i++){
        final Exception failure=errors[i]; JSONObject p=OpenRouterDiagnostics.probe("catalog",null,(u,t)->{throw failure;},()->false);
        yes(p.getInt("http")==0 && p.getString("kind").equals(kinds[i]) && !p.toString().contains("secret"));
      }
    });
    check("diagnostic cancellation prevents requests and another provider never leaks its key", () -> {
      JSONObject p=OpenRouterDiagnostics.probe("catalog",null,(u,t)->{throw new AssertionError();},()->true);
      yes(p.getString("kind").equals("cancelled") && p.has("elapsed_ms"));
      int[] count={0}; JSONObject report=OpenRouterDiagnostics.run("modelstudio","qwen-plus","other-provider-secret",(u,t)->{count[0]++;yes(t==null);return new DiagnosticHttp(u,200,"application/json","");},()->false);
      yes(count[0]==1 && !report.getBoolean("configured") && !report.toString().contains("other-provider-secret"));
    });
    check("diagnostic error reading stays bounded even if the server echoes a long payload", () -> {
      yes(OpenRouterDiagnostics.prefix(new ByteArrayInputStream(repeated('x',20000).getBytes("UTF-8")),8192).length()==8192);
      JSONObject p=OpenRouterDiagnostics.probe("key","test",(u,t)->new DiagnosticHttp(u,401,"application/json","{\"error\":{\"message\":\"revoked secret\"}}"),()->false);
      yes(p.getString("kind").equals("disabled_or_expired") && !p.toString().contains("secret"));
    });
    check("PC key transfer base64 matches the standard and rejects noncanonical or oversized data", () -> {
      byte[] bytes = new byte[256]; for (int i = 0; i < bytes.length; i++) bytes[i] = (byte)i;
      String encoded = KeyProvisioning.encode(bytes);
      yes(encoded.equals(java.util.Base64.getEncoder().encodeToString(bytes)));
      yes(java.util.Arrays.equals(bytes, KeyProvisioning.decode(encoded, 256)));
      rejects(() -> KeyProvisioning.decode(encoded, 255));
      rejects(() -> KeyProvisioning.decode("YQ", 10)); rejects(() -> KeyProvisioning.decode("YQ==extra", 10));
    });
    check("PC key setup accepts only a bounded one-time session and matching authentication", () -> {
      String token = repeated('a',64);
      yes(KeyProvisioning.socketName(repeated('b',32)).startsWith("tvassistant.key."));
      rejects(() -> KeyProvisioning.socketName("../../other"));
      KeyProvisioning.hello(Json.obj("type","hello","token",token),token);
      rejects(() -> KeyProvisioning.hello(Json.obj("type","hello","token",repeated('b',64)),token));
      rejects(() -> KeyProvisioning.hello(Json.obj("type","hello","token",token,"extra",true),token));
    });
    check("PC key setup refuses partial, oversized and deeply nested frames", () -> {
      rejects(() -> KeyProvisioning.read(new ByteArrayInputStream("{}".getBytes("UTF-8"))));
      rejects(() -> KeyProvisioning.read(new ByteArrayInputStream((repeated('a',9000)+"\n").getBytes("UTF-8"))));
      rejects(() -> KeyProvisioning.json(repeated('[',1000)));
      rejects(() -> KeyProvisioning.json("{} extra"));
      yes(KeyProvisioning.read(new ByteArrayInputStream("{\"ok\":true}\n".getBytes("UTF-8"))).getBoolean("ok"));
    });
    check("PC key setup accepts only explicit OpenRouter models and never guesses a provider", () -> {
      String key = "sk-or-v1-"+repeated('c',64);
      KeyProvisioning.validate(Json.obj("model","qwen/qwen3.5-9b","key",key));
      rejects(() -> KeyProvisioning.validate(Json.obj("model","qwen/qwen3.5-9b","key","not-an-openrouter-key")));
      rejects(() -> KeyProvisioning.validate(Json.obj("model","expensive/model","key",key)));
      rejects(() -> KeyProvisioning.validate(Json.obj("model","qwen/qwen3.5-9b","key",key,"endpoint","https://example.com")));
    });
    check("OpenRouter stays on its official endpoint and allows only selected TV models", () -> {
      yes(ModelConfig.endpoint("openrouter").equals("https://openrouter.ai/api/v1"));
      for (String model : ModelConfig.OPENROUTER_MODELS)
        ModelConfig.validate("openrouter", model, "unit-test-key-not-real");
      rejects(() -> ModelConfig.validate("openrouter", "openai/expensive-model", "unit-test-key-not-real"));
      rejects(() -> ModelConfig.validate("openrouter", "qwen/qwen3.5-9b", ""));
      rejects(() -> ModelConfig.validate("openrouter", "qwen/qwen3.5-9b", "test-key\r\ninjected"));
    });
    check("OpenRouter routes JSON commands inside price ceilings and controls reasoning per model", () -> {
      JSONObject q = FreeCommands.request("Открой Винк и выключи звук", tvApps(), "qwen/qwen3.5-9b", "openrouter");
      yes(!q.getJSONObject("reasoning").getBoolean("enabled"));
      yes(q.getJSONObject("provider").getBoolean("require_parameters"));
      yes(q.getJSONObject("provider").getJSONObject("max_price").getDouble("completion") == 0.50);
      yes(!q.has("models") && !q.has("reasoning_effort"));
      JSONObject g = FreeCommands.request("Пауза", tvApps(), "openai/gpt-oss-20b", "openrouter");
      yes(g.getJSONObject("reasoning").getString("effort").equals("low"));
      JSONObject f = FreeCommands.request("Домой", tvApps(), "openrouter/free", "openrouter");
      yes(!f.has("reasoning") && f.getJSONObject("provider").getJSONObject("max_price").getDouble("prompt") == 0);
      yes(f.getJSONObject("provider").getJSONObject("max_price").getDouble("completion") == 0);
      rejects(() -> FreeCommands.request("Открой Винк", tvApps(), "unapproved/model", "openrouter"));
    });
    check("OpenRouter PKCE matches the RFC vector and exposes only the challenge", () -> {
      String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
      yes(OpenRouterAuth.challenge(verifier).equals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"));
      String url = OpenRouterAuth.authorization("http://127.0.0.1:12345/openrouter/callback", "test-state", verifier);
      yes(url.startsWith("https://openrouter.ai/auth?") && url.contains("code_challenge_method=S256"));
      yes(!url.contains(verifier) && !url.contains("code_verifier"));
    });
    check("OpenRouter callback rejects other paths, states, duplicates and oversized request lines", () -> {
      String good = "GET /openrouter/callback?state=match&code=one-use-code HTTP/1.1";
      yes(OpenRouterAuth.callbackCode(good, "match").equals("one-use-code"));
      rejects(() -> OpenRouterAuth.callbackCode(good, "wrong"));
      rejects(() -> OpenRouterAuth.callbackCode(good.replace("/openrouter/", "/other/"), "match"));
      rejects(() -> OpenRouterAuth.callbackCode(good.replace("state=match", "state=match&state=match"), "match"));
      rejects(() -> OpenRouterAuth.callbackCode(good.replace("code=one-use-code", "error=denied"), "match"));
      rejects(() -> OpenRouterAuth.callbackCode(good.replace("GET /", "GET https://other.example/"), "match"));
      rejects(() -> OpenRouterAuth.callbackCode(good.replace("GET /", "GET //other.example/"), "match"));
      rejects(() -> OpenRouterAuth.requestLine(new ByteArrayInputStream(repeated('a', 5000).getBytes("UTF-8"))));
      yes(OpenRouterAuth.requestLine(new ByteArrayInputStream((good + "\r\nextra").getBytes("UTF-8"))).equals(good));
    });
    check("OpenRouter errors do not show server payloads and have no Pollinations cooldown", () -> {
      Net.InferenceHttpError funds = new Net.InferenceHttpError(402, 0);
      yes(funds.getMessage().contains("средств") && !funds.getMessage().contains("sk-"));
      FreeRateGate gate = new FreeRateGate(); gate.reserve(1000, 0); gate.reserve(1001, 0);
      gate.backoff(1002, 10); yes(gate.remaining(1003) == 10);
    });
    check("polite Russian TV requests stay local and negated requests are not executed", () -> {
      yes(LocalCommands.parse("Включи мне, пожалуйста, приложение с Ютубом", tvApps()).args.getString("app").equals("com.google.android.youtube.tv"));
      yes(LocalCommands.parse("Запусти нам, пожалуйста, Винк", tvApps()).args.getString("app").equals("ru.rt.video.app.tv"));
      yes(LocalCommands.parse("Давай звук на 22 процента", tvApps()).args.getInt("percent") == 22);
      yes(LocalCommands.parse("Не включай Винк", tvApps()) == null);
      yes(LocalCommands.parse("Открой Винк и выключи звук", tvApps()) == null);
      yes(LocalCommands.parse("Открой приложение Кинозал999", tvApps()) == null);
    });
    check("free providers are pinned, no secret can be sent to an anonymous endpoint", () -> {
      ModelConfig.validate("pollinations", "openai-fast", "");
      yes(!ModelConfig.needsKey("pollinations"));
      rejects(() -> ModelConfig.validate("pollinations", "openai-fast", "unit-test-key-not-real"));
      rejects(() -> ModelConfig.validate("pollinations", "paid-model", ""));
      ModelConfig.validate("cloudflare", "@cf/ibm-granite/granite-4.0-h-micro", "unit-test-key-not-real", repeated('a', 32));
      rejects(() -> ModelConfig.validate("cloudflare", "@cf/meta/llama-3.2-3b-instruct", "unit-test-key-not-real", repeated('a', 32)));
      yes(ModelConfig.endpoint("cloudflare", repeated('a', 32)).contains("api.cloudflare.com/client/v4/accounts/"));
      rejects(() -> ModelConfig.validate("cloudflare", "@cf/deepseek-ai/deepseek-v4-pro-0813", "unit-test-key-not-real", repeated('a', 32)));
      rejects(() -> ModelConfig.endpoint("cloudflare", "../other"));
    });
    check("free command request is bounded, stateless and carries no account history", () -> {
      JSONObject r = FreeCommands.request("Пожалуйста, включи Винк", tvApps(), "openai-fast", "pollinations");
      yes(r.getJSONArray("messages").length() == 2 && !r.optBoolean("stream"));
      yes(!r.has("tools") && !r.has("Authorization") && r.getString("model").equals("openai-fast"));
      yes(r.getJSONArray("messages").getJSONObject(1).getString("content").contains("ru.rt.video.app.tv"));
      rejects(() -> FreeCommands.request("", tvApps(), "openai-fast", "pollinations"));
      rejects(() -> FreeCommands.request(new String(new char[501]).replace('\0', 'x'), tvApps(), "openai-fast", "pollinations"));
    });
    check("free command plan validates all steps and ignores unverified success prose", () -> {
      FreeCommands.Plan p = FreeCommands.parse(freeReply("{\"steps\":[{\"tool\":\"open_app\",\"args\":{\"app\":\"ru.rt.video.app.tv\"}},{\"tool\":\"volume\",\"args\":{\"action\":\"mute\"}}],\"reply\":\"Already succeeded\"}"), tvApps());
      yes(p.steps.length() == 2 && p.reply.isEmpty());
      FreeCommands.Plan one = FreeCommands.parse(freeReply("{\"tool\":\"open_app\",\"args\":{\"app\":\"com.google.android.youtube.tv\"}}"), tvApps());
      yes(one.steps.length() == 1);
      rejects(() -> FreeCommands.parse(freeReply("{\"steps\":[{\"tool\":\"open_app\",\"args\":{\"app\":\"ru.rt.video.app.tv\"}},{\"tool\":\"delete_everything\",\"args\":{}}]}"), tvApps()));
      rejects(() -> FreeCommands.parse(freeReply("{\"tool\":\"open_app\",\"args\":{\"app\":\"not.installed\"}}"), tvApps()));
    });
    check("free command bounds reject guessed clicks, silent volume defaults and extra args", () -> {
      for (String text : new String[] {
          "{\"tool\":\"navigate\",\"args\":{\"direction\":\"select\"}}",
          "{\"tool\":\"volume\",\"args\":{\"action\":\"set\"}}",
          "{\"tool\":\"volume\",\"args\":{\"action\":\"set\",\"percent\":101}}",
          "{\"tool\":\"volume\",\"args\":{\"action\":\"set\",\"percent\":2.5}}",
          "{\"tool\":\"open_app\",\"args\":{\"app\":\"ru.rt.video.app.tv\",\"extra\":true}}",
          "{\"tool\":\"open_settings\",\"args\":{\"panel\":\"app\"}}"})
        rejects(() -> FreeCommands.parse(freeReply(text), tvApps()));
      yes(FreeCommands.parse(freeReply("{\"tool\":\"volume\",\"args\":{\"action\":\"set\",\"percent\":22}}"), tvApps()).steps.length() == 1);
    });
    check("free parser refuses partial, trailing, nested and oversized instructions", () -> {
      JSONObject partial = freeReply("{\"tool\":\"clock\",\"args\":{}}");
      partial.getJSONArray("choices").getJSONObject(0).put("finish_reason", "length");
      rejects(() -> FreeCommands.parse(partial, tvApps()));
      rejects(() -> FreeCommands.parse(freeReply("{\"tool\":\"clock\",\"args\":{}} extra"), tvApps()));
      rejects(() -> FreeCommands.parse(freeReply(repeated('[', 1000)), tvApps()));
      rejects(() -> FreeCommands.parse(freeReply("{\"steps\":[],\"reply\":\"\"}"), tvApps()));
      String many = "{\"steps\":[" + String.join(",", java.util.Collections.nCopies(5, "{\"tool\":\"clock\",\"args\":{}}")) + "]}";
      rejects(() -> FreeCommands.parse(freeReply(many), tvApps()));
    });
    check("free parser returns a clarification without an executable action", () -> {
      FreeCommands.Plan p = FreeCommands.parse(freeReply("{\"steps\":[],\"reply\":\"Такого приложения нет. Какое открыть?\"}"), tvApps());
      yes(p.steps.length() == 0 && !p.reply.isEmpty());
    });
    check("free rate gate blocks repeats, honors server backoff and never queues work", () -> {
      FreeRateGate gate = new FreeRateGate(); gate.reserve(1000, 31);
      yes(gate.remaining(1001) == 31); rejects(() -> gate.reserve(2000, 31));
      yes(gate.remaining(32000) == 0); gate.reserve(32000, 31);
      gate.backoff(33000, 45); yes(gate.remaining(34000) == 44);
      gate.backoff(34000, 1); yes(gate.remaining(34000) == 44);
    });
    check("Qwen uses only direct official HTTPS endpoints and validates key input", () -> {
      yes(java.util.Arrays.asList(ModelConfig.PROVIDERS).contains("pollinations"));
      yes(ModelConfig.endpoint("qwencloud").equals("https://maas.qwencloudapi.com/compatible-mode/v1"));
      yes(ModelConfig.endpoint("modelstudio").equals("https://dashscope-intl.aliyuncs.com/compatible-mode/v1"));
      ModelConfig.validate("qwencloud", "qwen3.8-max", "unit-test-key-not-a-real-secret");
      rejects(() -> ModelConfig.endpoint("unknown-router"));
      rejects(() -> ModelConfig.endpoint("https://other.example"));
      rejects(() -> ModelConfig.validate("qwencloud", "qwen/../other", "unit-test-key-not-a-real-secret"));
      rejects(() -> ModelConfig.validate("qwencloud", "another-model", "unit-test-key-not-a-real-secret"));
      rejects(() -> ModelConfig.validate("qwencloud", "qwen3.8-max", "unit-test-key\nheader"));
    });
    check("Qwen preserves multiple tool calls and their matching results in history", () -> {
      JSONObject request = Json.obj("model", "qwen-plus", "instructions", "Отвечай по-русски", "tools", Json.arr(
          Json.obj("type", "namespace", "name", "tv", "tools", Json.arr(
              Json.obj("name", "open_app", "parameters", Json.obj("type", "object")),
              Json.obj("name", "screen_see", "parameters", Json.obj("type", "object"))))),
          "input", Json.arr(Json.obj("role", "user", "content", "Открой Винк"),
              Json.obj("type", "function_call", "call_id", "first", "name", "open_app", "arguments", "{\"app\":\"Wink\"}"),
              Json.obj("type", "function_call", "call_id", "second", "name", "screen_read", "arguments", "{}"),
              Json.obj("type", "function_call_output", "call_id", "first", "output", "{\"launched\":true}"),
              Json.obj("type", "function_call_output", "call_id", "second", "output", "{\"text\":\"untrusted data\"}")),
          "tool_choice", "none", "include", Json.arr("reasoning.encrypted_content"), "prompt_cache_key", "private");
      JSONObject body = QwenProtocol.request(request, "modelstudio");
      JSONArray messages = body.getJSONArray("messages");
      yes(messages.length() == 5 && messages.getJSONObject(2).getJSONArray("tool_calls").length() == 2);
      yes(messages.getJSONObject(3).getString("tool_call_id").equals("first"));
      yes(messages.getJSONObject(4).getString("tool_call_id").equals("second"));
      yes(messages.getJSONObject(4).getString("content").contains("untrusted data"));
      yes(body.getJSONArray("tools").length() == 1 && body.getString("tool_choice").equals("none"));
      yes(!body.has("include") && !body.has("prompt_cache_key") && !body.has("input") && !body.optBoolean("stream"));
    });
    check("Qwen validates structured tool calls before returning anything executable", () -> {
      JSONArray advertised = Json.arr(Json.obj("type", "function", "function", Json.obj("name", "open_app",
          "parameters", Json.obj("type", "object", "properties", Json.obj("app", Json.obj("type", "string")),
              "required", Json.arr("app"), "additionalProperties", false))));
      JSONObject call = Json.obj("id", "call_1", "type", "function", "function", Json.obj("name", "open_app", "arguments", "{\"app\":\"Wink\"}"));
      JSONObject raw = Json.obj("choices", Json.arr(Json.obj("finish_reason", "tool_calls", "message",
          Json.obj("role", "assistant", "content", JSONObject.NULL, "tool_calls", Json.arr(call)))));
      JSONObject parsed = QwenProtocol.response(raw, advertised).getJSONArray("output").getJSONObject(0);
      yes(parsed.getString("name").equals("open_app") && parsed.getString("call_id").equals("call_1"));
      JSONObject invalid = new JSONObject(raw.toString());
      invalid.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getJSONArray("tool_calls")
          .getJSONObject(0).getJSONObject("function").put("arguments", "{\"app\":\"Wink\",\"extra\":true}");
      rejects(() -> QwenProtocol.response(invalid, advertised));
      JSONObject unknown = new JSONObject(raw.toString());
      unknown.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getJSONArray("tool_calls")
          .getJSONObject(0).getJSONObject("function").put("name", "delete_everything");
      rejects(() -> QwenProtocol.response(unknown, advertised));
      JSONObject duplicate = new JSONObject(raw.toString());
      duplicate.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getJSONArray("tool_calls").put(call);
      rejects(() -> QwenProtocol.response(duplicate, advertised));
    });
    check("Qwen refuses partial responses and never treats model prose as actions", () -> {
      JSONObject raw = Json.obj("choices", Json.arr(Json.obj("finish_reason", "stop", "message",
          Json.obj("role", "assistant", "content", "{\"tool\":\"open_app\",\"args\":{\"app\":\"Wink\"}}"))));
      JSONArray output = QwenProtocol.response(raw, new JSONArray()).getJSONArray("output");
      yes(output.length() == 1 && output.getJSONObject(0).getString("type").equals("message"));
      for (String finish : new String[] {"length", "content_filter", "", "error"}) {
        JSONObject broken = new JSONObject(raw.toString());
        broken.getJSONArray("choices").getJSONObject(0).put("finish_reason", finish);
        rejects(() -> QwenProtocol.response(broken, new JSONArray()));
      }
      rejects(() -> QwenProtocol.response(Json.obj("choices", new JSONArray()), new JSONArray()));
    });
    check("Russian package command remains local and human-readable tool labels are translated", () -> {
      JSONArray apps = Json.arr(Json.obj("label", "Wink", "package", "ru.rt.video.app.tv"));
      yes(LocalCommands.parse("Открой ru.rt.video.app.tv", apps).tool.equals("open_app"));
      yes(RuUi.text("open app").equals("Запуск приложения"));
    });
    check("Russian TV requests resolve to the same bounded tools", () -> {
      JSONArray apps = Json.arr(Json.obj("label", "YouTube", "package", "com.google.android.youtube.tv"),
          Json.obj("label", "Wink", "package", "ru.rt.video.app.tv"));
      LocalCommands.Call youtube = LocalCommands.parse("Открой Ютуб!", apps);
      yes(youtube != null && youtube.tool.equals("open_app")
          && youtube.args.getString("app").equals("com.google.android.youtube.tv"));
      LocalCommands.Call wink = LocalCommands.parse("Включи Винк", apps);
      yes(wink.tool.equals("open_app") && wink.args.getString("app").equals("ru.rt.video.app.tv"));
      yes(LocalCommands.parse("Сделай потише",apps).args.getString("action").equals("down"));
      yes(LocalCommands.parse("Громкость на 20 процентов",apps).args.getInt("percent")==20);
      yes(LocalCommands.parse("Назад",apps).args.getString("direction").equals("back"));
      yes(LocalCommands.parse("На главный экран",apps).args.getString("direction").equals("home"));
      yes(RussianCommands.normalize("Объясни устройство Солнца").equals("Объясни устройство Солнца"));
    });
    check("Russian presentation keeps device identifiers and unknown content intact", () -> {
      String device = RuUi.text(LocalFormatter.format("device_info", Json.obj("manufacturer", "Xiaomi", "model", "MiTV-MSSP1", "android", "9", "online", true, "navigation_enabled", true)));
      yes(device.contains("MiTV-MSSP1") && device.contains("Сеть: подключена") && device.contains("Управление экраном: включено"));
      yes(RuUi.text("Media volume: 20% · muted").equals("Громкость: 20% · без звука"));
      yes(RuUi.text("Settings").equals("Настройки"));
      yes(RuUi.text("ru.rt.video.app.tv").equals("ru.rt.video.app.tv"));
      yes(RuUi.text("Мой ответ: Settings are fine").equals("Мой ответ: Settings are fine"));
      yes(LocalCommands.parse("Покажи информацию о телевизоре", new JSONArray()).tool.equals("device_info"));
    });
    check("an installed Android package must not be treated as a website", () -> {
      JSONArray apps=Json.arr(Json.obj("label","YouTube","package","com.google.android.youtube.tv"));
      LocalCommands.Call call=LocalCommands.parse("Open com.google.android.youtube.tv",apps);
      yes(call.tool.equals("open_app"));
      yes(LocalCommands.parse("Open example.com",apps).tool.equals("open_url"));
    });
    check("episode resolution uses the observed video ID with autoplay disabled", () -> {
      JSONObject episode = MediaEpisode.resolve(seriesFixture(), "tt0121955", Json.obj("season", 3, "episode", 5));
      yes(episode.getString("video_id").equals("catalog:real-episode"));
      yes(episode.getString("episode_title").equals("Tweek vs. Craig"));
      yes(episode.getString("detail_url").equals("stremio:///detail/series/tt0121955/catalog%3Areal-episode?autoPlay=false"));
      MediaEpisode.validate("movie", "tt0121955", Json.obj());
      rejects(() -> MediaEpisode.resolve(seriesFixture(), "tt0121955", Json.obj("season", 3, "episode", 99)));
    });
    check("episode UI distinguishes requested from verified selection", () -> {
      JSONObject episode = MediaEpisode.resolve(seriesFixture(), "tt0121955", Json.obj("season", 3, "episode", 5));
      JSONObject result = Json.obj("launched", true, "episode", episode, "episode_verified", false,
          "observation_error", "Navigation is disconnected");
      String unverified = LocalFormatter.format("media_details", result);
      yes(unverified.startsWith("Requested episode:") && unverified.contains("Navigation is disconnected"));
      yes(!unverified.contains("Episode selected:"));
      result.remove("observation_error");
      yes(ActionPlan.failed(result));
      int[] calls = {0};
      yes(ActionPlan.run(plan(), Json.obj(), runner(calls, result, false)).optBoolean("stopped_early"));
      yes(calls[0] == 1);
      yes(LocalFormatter.format("media_details", result.put("episode_verified", true)).startsWith("Episode selected:"));
      yes(!ActionPlan.failed(result));
    });
    check("episode arguments require an integer pair on a valid series", () -> {
      rejects(() -> MediaEpisode.validate("series", "tt0121955", Json.obj("season", 3)));
      rejects(() -> MediaEpisode.validate("series", "tt0121955", Json.obj("episode", 5)));
      rejects(() -> MediaEpisode.validate("movie", "tt0121955", Json.obj("season", 3, "episode", 5)));
      rejects(() -> MediaEpisode.validate("other", "tt0121955", Json.obj()));
      rejects(() -> MediaEpisode.validate("series", "../wrong", Json.obj()));
      rejects(() -> MediaEpisode.validate("series", "tt0121955", Json.obj("season", "3", "episode", 5)));
      rejects(() -> MediaEpisode.validate("series", "tt0121955", Json.obj("season", 3.5, "episode", 5)));
      rejects(() -> MediaEpisode.validate("series", "tt0121955", Json.obj("season", -1, "episode", 5)));
      rejects(() -> MediaEpisode.validate("series", "tt0121955", Json.obj("season", 3, "episode", 0)));
      rejects(() -> MediaEpisode.validate("series", "tt0121955", Json.obj("season", 3, "episode", 10001)));
      MediaEpisode.validate("series", "tt0121955", Json.obj("season", 0, "episode", 1));
    });
    check("episode lookup rejects wrong, duplicate and unsafe catalog responses", () -> {
      JSONObject coordinates = Json.obj("season", 3, "episode", 5);
      rejects(() -> MediaEpisode.resolve(seriesFixture().put("id", "tt0306414"), "tt0121955", coordinates));
      rejects(() -> MediaEpisode.resolve(seriesFixture().put("type", "movie"), "tt0121955", coordinates));
      JSONObject duplicate = seriesFixture();
      duplicate.getJSONArray("videos").put(duplicate.getJSONArray("videos").getJSONObject(0));
      rejects(() -> MediaEpisode.resolve(duplicate, "tt0121955", coordinates));
      for (String id : new String[] {"", "../escape", "x?autoPlay=true", "javascript:alert(1)"}) {
        JSONObject bad = seriesFixture();
        bad.getJSONArray("videos").getJSONObject(0).put("id", id);
        rejects(() -> MediaEpisode.resolve(bad, "tt0121955", coordinates));
      }
    });
    check("episode verification requires the app, series and selected episode heading", () -> {
      JSONObject episode = MediaEpisode.resolve(seriesFixture(), "tt0121955", Json.obj("season", 3, "episode", 5));
      JSONObject screen = Json.obj("package", "com.stremio.one", "nodes", Json.arr(
          Json.obj("view_id", "com.stremio.one:id/meta_details_label", "text", "South Park (1997–)"),
          Json.obj("view_id", "com.stremio.one:id/meta_details_video_label", "text", "S03E05 - Jun 24, 1999 - Tweek vs. Craig")));
      yes(MediaEpisode.verified(screen, episode));
      yes(!MediaEpisode.verified(new JSONObject(screen.toString()).put("package", "other.app"), episode));
      yes(!MediaEpisode.verified(new JSONObject(screen.toString()).put("error", "disconnected"), episode));
      JSONObject listItem = new JSONObject(screen.toString());
      listItem.getJSONArray("nodes").getJSONObject(1).put("view_id", "episode_list_item");
      yes(!MediaEpisode.verified(listItem, episode));
      JSONObject wrongSeries = new JSONObject(screen.toString());
      wrongSeries.getJSONArray("nodes").getJSONObject(0).put("text", "Wrong series (1997–)");
      yes(!MediaEpisode.verified(wrongSeries, episode));
      for (String heading : new String[] {"S03E06 - Tweek vs. Craig", "S13E05 - Tweek vs. Craig",
          "S03E050 - Tweek vs. Craig", "E05", "S03E05 - Wrong episode", "Synopsis mentioning S03E05 - Tweek vs. Craig"}) {
        screen.getJSONArray("nodes").getJSONObject(1).put("text", heading);
        yes(!MediaEpisode.verified(screen, episode));
      }
    });
    check("automatic learning replaces literals and derives verification for a new query", () -> {
      JSONObject learned = LearnedSearch.learn(search("The Wire", "Results: The Wire"), Json.obj(), searchResult());
      yes(learned != null && !learned.toString().contains("The Wire"));
      JSONObject workflow = learned.getJSONObject("workflow");
      JSONObject params = LearnedSearch.parameters(workflow, Json.obj("query", "Another show", "result", "Pending"));
      yes(params.getString("result").equals("Results: Another show"));
      JSONArray resolved = (JSONArray) ActionPlan.resolve(workflow.getJSONArray("steps"), new JSONArray(), params);
      yes(resolved.getJSONObject(1).getJSONObject("args").getString("text").equals("Another show"));
      yes(resolved.getJSONObject(2).getJSONObject("args").getString("label").equals("Results: Another show"));
      yes(learned.getString("name").equals(LearnedSearch.learn(search("Severance", "Results: Severance"), Json.obj(), searchResult()).getString("name")));
      rejects(() -> LearnedSearch.parameters(workflow, Json.obj()));
      rejects(() -> LearnedSearch.parameters(workflow, Json.obj("query", " ")));
      rejects(() -> LearnedSearch.parameters(workflow, Json.obj("query", 7)));
    });
    check("automatic learning accepts parameterized searches without retaining their values", () -> {
      JSONArray source = search("$param.title", "Results: placeholder");
      source.getJSONObject(2).getJSONObject("args").put("label", Json.obj("$param", "heading"));
      JSONObject learned = LearnedSearch.learn(source, Json.obj("title", "Severance", "heading", "Results: Severance"), searchResult());
      yes(learned != null && !learned.toString().contains("Severance"));
      yes(source.getJSONObject(1).getJSONObject("args").getString("text").equals("$param.title"));
    });
    check("automatic learning rejects failed, unverified and unrelated result headings", () -> {
      JSONObject failed = searchResult().put("stopped_early", true);
      yes(LearnedSearch.learn(search("Severance", "Results: Severance"), Json.obj(), failed) == null);
      failed = searchResult(); failed.getJSONArray("results").getJSONObject(1).put("text_verified", false);
      yes(LearnedSearch.learn(search("Severance", "Results: Severance"), Json.obj(), failed) == null);
      failed = searchResult(); failed.getJSONArray("results").getJSONObject(2).put("verified", false);
      yes(LearnedSearch.learn(search("Severance", "Results: Severance"), Json.obj(), failed) == null);
      yes(LearnedSearch.learn(search("Severance", "Pending"), Json.obj(), searchResult()) == null);
      yes(LearnedSearch.learn(search("Severance", "Severance Severance"), Json.obj(), searchResult()) == null);
    });
    check("automatic learning rejects mixed origins, field-value verification and query-bearing controls", () -> {
      JSONArray source = search("Severance", "Results: Severance");
      source.getJSONObject(2).getJSONObject("args").put("context", "https://other.com");
      yes(LearnedSearch.learn(source, Json.obj(), searchResult()) == null);
      source = search("Severance", "Results: Severance"); source.getJSONObject(2).getJSONObject("args").put("match", "exact");
      yes(LearnedSearch.learn(source, Json.obj(), searchResult()) == null);
      source = search("Severance", "Results: Severance"); source.getJSONObject(0).getJSONObject("args").put("label", "Severance");
      yes(LearnedSearch.learn(source, Json.obj(), searchResult()) == null);
    });
    check("scroll counts reject fractions and out-of-range values before any actions", () -> {
      JSONObject schema = Json.obj("type", "object", "properties", Json.obj("count",
          Json.obj("type", "integer", "minimum", 1, "maximum", 4)), "required", Json.arr());
      ToolArguments.validate(schema, Json.obj("count", 4));
      for (double value : new double[] {0, 5, 1.5, Double.NaN})
        rejects(() -> ToolArguments.validate(schema, Json.obj("count", value)));
    });
    check("WebSocket delta payload excludes stream and includes previous response", () -> {
      JSONArray input = Json.arr(Json.obj("role", "user", "content", "old"), Json.obj("type", "function_call_output", "call_id", "call1", "output", "{}"));
      JSONObject payload = ResponseSocket.payload(Json.obj("stream", true, "store", false), input, 1, "resp1");
      yes(!payload.has("stream") && payload.getString("type").equals("response.create") && !payload.getBoolean("store")
          && payload.getJSONArray("input").length() == 1 && payload.getString("previous_response_id").equals("resp1"));
    });
    check("WebSocket new chain sends full context", () -> {
      JSONObject payload = ResponseSocket.payload(Json.obj("stream", true), plan(), 0, "");
      yes(!payload.has("previous_response_id") && payload.getJSONArray("input").length() == 2);
    });
    check("shared event accumulator restores terminal output in order", () -> {
      Net.Accumulator parser = new Net.Accumulator(events(false));
      parser.accept(Json.obj("type", "response.output_item.done", "output_index", 1, "item", Json.obj("type", "message", "id", "second")));
      parser.accept(Json.obj("type", "response.output_item.done", "output_index", 0, "item", Json.obj("type", "function_call", "id", "first")));
      JSONObject response = parser.accept(Json.obj("type", "response.completed", "response", Json.obj("id", "resp1", "output", Json.arr())));
      yes(response.getJSONArray("output").length() == 2 && response.getJSONArray("output").getJSONObject(0).getString("id").equals("first"));
    });
    check("shared accumulator preserves supplied terminal output without duplication", () -> {
      Net.Accumulator parser = new Net.Accumulator(events(false));
      parser.accept(Json.obj("type", "response.output_item.done", "output_index", 0, "item", Json.obj("id", "item")));
      JSONObject done = parser.accept(Json.obj("type", "response.completed", "response", Json.obj("output", Json.arr(Json.obj("id", "item")))));
      yes(done.getJSONArray("output").length() == 1);
    });
    check("shared accumulator rejects failed, incomplete, error, cancelled", () -> {
      for (String type : new String[] {"response.failed", "response.incomplete", "error"})
        rejects(() -> new Net.Accumulator(events(false)).accept(Json.obj("type", type)));
      rejects(() -> new Net.Accumulator(events(true)).accept(Json.obj("type", "response.completed")));
    });
    check("SSE completion and missing completion", () -> {
      JSONObject result = Net.parseStream(new BufferedReader(new StringReader("data: {\"type\":\"response.completed\",\"response\":{\"output\":[]}}\n\n")), events(false));
      yes(result.getJSONArray("output").length() == 0);
      rejects(() -> Net.parseStream(new BufferedReader(new StringReader("data: {\"type\":\"response.created\"}\n\n")), events(false)));
    });
    check("plan scalar parameters and nested references", () -> {
      JSONObject resolved = (JSONObject) ActionPlan.resolve(Json.obj("id", Json.obj("$ref", "0.results.0.id"), "title", Json.obj("$param", "title")),
          Json.arr(Json.obj("results", Json.arr(Json.obj("id", "tt1")))), Json.obj("title", "The Wire"));
      yes(resolved.getString("id").equals("tt1") && resolved.getString("title").equals("The Wire"));
      rejects(() -> ActionPlan.resolve(Json.obj("$param", "missing"), Json.arr(), Json.obj()));
      rejects(() -> ActionPlan.resolve(Json.obj("$param", "nested"), Json.arr(), Json.obj("nested", Json.obj())));
    });
    check("plan rejects recursion, forward refs, unauthorized tools and oversize", () -> {
      for (String tool : new String[] {"action_plan", "routines", "workflow", "notes", "preferences"})
        rejects(() -> ActionPlan.validate(Json.arr(Json.obj("tool", tool, "args", Json.obj()))));
      rejects(() -> ActionPlan.validate(Json.arr(Json.obj("tool", "clock", "args", Json.obj("key", Json.obj("$ref", "0.value"))))));
      JSONArray tooMany = new JSONArray(); for (int i = 0; i < 9; i++) tooMany.put(plan().getJSONObject(0));
      rejects(() -> ActionPlan.validate(tooMany));
    });
    check("plan stops on failed action and cancellation", () -> {
      int[] calls = {0};
      JSONObject result = ActionPlan.run(plan(), Json.obj(), runner(calls, Json.obj("performed", false), false));
      yes(calls[0] == 1 && result.getBoolean("stopped_early") && result.getInt("completed_steps") == 0);
      calls[0] = 0;
      result = ActionPlan.run(plan(), Json.obj(), runner(calls, Json.obj(), true));
      yes(calls[0] == 0 && result.getBoolean("stopped_early"));
    });
    check("plan stops on partial keyboard delivery", () -> {
      int[] calls = {0};
      JSONObject result = ActionPlan.run(plan(), Json.obj(), runner(calls, Json.obj("keys_requested", 4, "keys_delivered", 2), false));
      yes(calls[0] == 1 && result.getBoolean("stopped_early"));
    });
    check("successful plan keeps step count and parameters", () -> {
      int[] calls = {0};
      JSONObject result = ActionPlan.run(plan(), Json.obj(), runner(calls, Json.obj("value", 56), false));
      yes(calls[0] == 2 && !result.getBoolean("stopped_early") && result.getInt("completed_steps") == 2);
    });
    check("fresh labels reject ambiguity, disabled and password fields", () -> {
      JSONObject target = Json.obj("id", 1, "tag", "BUTTON", "label", "Find");
      yes(Tools.uniqueTarget(Json.obj("nodes", Json.arr(target)), "find", false, true, false).getInt("id") == 1);
      rejects(() -> Tools.uniqueTarget(Json.obj("nodes", Json.arr(target, target)), "find", false, true, false));
      yes(Tools.uniqueTarget(Json.obj("nodes", Json.arr(Json.obj("label", "Search", "password", true))), "Search", false, true, false) == null);
      yes(Tools.uniqueTarget(Json.obj("nodes", Json.arr(Json.obj("label", "Find", "disabled", true))), "Find", false, true, false) == null);
    });
    check("native targets exclude zero-size bounds and noneditable typing", () -> {
      yes(Tools.uniqueTarget(Json.obj("nodes", Json.arr(Json.obj("text", "Find", "bounds", Json.arr(0,0,0,0)))), "Find", false, false, false) == null);
      yes(Tools.uniqueTarget(Json.obj("nodes", Json.arr(Json.obj("text", "Find", "bounds", Json.arr(1,1,100,100)))), "Find", false, false, true) == null);
    });
    check("scope guards reject changed app/origin and accept normalized HTTPS ports", () -> {
      Tools.checkScope(Json.obj("package", "test.app"), "native", "test.app");
      rejects(() -> Tools.checkScope(Json.obj("package", "other.app"), "native", "test.app"));
      Tools.checkScope(Json.obj("url", "https://example.com/path"), "browser", "https://example.com:443");
      rejects(() -> Tools.checkScope(Json.obj("url", "https://other.com/path"), "browser", "https://example.com"));
      rejects(() -> Tools.checkScope(Json.obj("url", "https://example.com/path"), "browser", "https://example.com/path"));
      rejects(() -> Tools.checkScope(Json.obj("url", "https://example.com/path"), "browser", "https://user@example.com"));
    });
    check("reusable plans require scoped clicks and terminal verification", () -> {
      JSONArray reusable = Json.arr(Json.obj("tool", "ui_target", "args", Json.obj("scope", "browser", "context", "https://example.com", "label", Json.obj("$param", "title"), "action", "wait")));
      ActionPlan.validateReusable(reusable);
      rejects(() -> ActionPlan.validateReusable(plan()));
      reusable.getJSONObject(0).getJSONObject("args").put("action", "click");
      rejects(() -> ActionPlan.validateReusable(reusable));
      reusable.getJSONObject(0).getJSONObject("args").put("action", "type");
      rejects(() -> ActionPlan.validateReusable(reusable));
    });
    check("plan preflight rejects invalid later literals and missing parameters", () -> {
      JSONObject schema = Json.obj("type", "object", "properties", Json.obj(
          "action", Json.obj("type", "string", "enum", Json.arr("click", "wait")),
          "timeout_ms", Json.obj("type", "number", "minimum", 0, "maximum", 4000)),
          "required", Json.arr("action"));
      rejects(() -> ToolArguments.preflight(schema, Json.obj("action", 123), Json.obj()));
      rejects(() -> ToolArguments.preflight(schema, Json.obj("action", "delete"), Json.obj()));
      rejects(() -> ToolArguments.preflight(schema, Json.obj("action", "click", "timeout_ms", 5000), Json.obj()));
      rejects(() -> ToolArguments.preflight(schema, Json.obj("action", Json.obj("$param", "missing")), Json.obj()));
      rejects(() -> ToolArguments.preflight(schema, Json.obj("action", Json.obj("$param", "action")), Json.obj("action", "delete")));
      ToolArguments.preflight(schema, Json.obj("action", Json.obj("$param", "action")), Json.obj("action", "click"));
    });
    check("nested keyboard arguments are checked before delivery", () -> {
      JSONObject item = Json.obj("type", "object", "properties", Json.obj("x", Json.obj("type", "number"),
          "y", Json.obj("type", "number")), "required", Json.arr("x", "y"));
      JSONObject schema = Json.obj("type", "object", "properties", Json.obj("keys", Json.obj("type", "array", "minItems", 1, "maxItems", 32, "items", item)), "required", Json.arr("keys"));
      ToolArguments.validate(schema, Json.obj("keys", Json.arr(Json.obj("x", 10, "y", 20))));
      rejects(() -> ToolArguments.validate(schema, Json.obj("keys", "not an array")));
      rejects(() -> ToolArguments.validate(schema, Json.obj("keys", Json.arr())));
      rejects(() -> ToolArguments.validate(schema, Json.obj("keys", Json.arr(Json.obj("x", "wrong", "y", 20)))));
      rejects(() -> ToolArguments.validate(schema, Json.obj("keys", Json.arr(Json.obj("x", 10)))));
      rejects(() -> ToolArguments.validate(schema, Json.obj("keys", Json.arr(Json.obj("x", 10, "y", 20, "extra", true)))));
    });
    check("deferred references are validated after resolution", () -> {
      JSONObject schema = Json.obj("type", "object", "properties", Json.obj("id", Json.obj("type", "string")), "required", Json.arr("id"));
      JSONObject template = Json.obj("id", Json.obj("$ref", "0.id"));
      ToolArguments.preflight(schema, template, Json.obj());
      JSONObject resolved = (JSONObject) ActionPlan.resolve(template, Json.arr(Json.obj("id", 123)), Json.obj());
      rejects(() -> ToolArguments.validate(schema, resolved));
    });
    check("incorrect text verification stops dependent actions", () -> {
      int[] calls = {0};
      JSONObject result = ActionPlan.run(plan(), Json.obj(), runner(calls, Json.obj("performed", true, "text_verified", false), false));
      yes(calls[0] == 1 && result.getBoolean("stopped_early"));
    });
    check("WebSocket payload preserves source and rejects invalid continuation cursor", () -> {
      JSONArray input = Json.arr(Json.obj("role", "user", "content", "old"), Json.obj("role", "user", "content", "new"));
      JSONObject request = Json.obj("input", input, "stream", true, "background", false, "model", "test-model");
      String before = request.toString();
      JSONObject result = ResponseSocket.payload(request, input, 1, "resp1");
      yes(request.toString().equals(before) && result.getString("model").equals("test-model")
          && !result.has("background") && result.getJSONArray("input").length() == 1);
      rejects(() -> ResponseSocket.payload(request, input, 3, "resp1"));
      rejects(() -> ResponseSocket.payload(request, input, -1, "resp1"));
    });
    check("native typed field follows identity instead of the old value label", () -> {
      JSONObject original = Json.obj("view_id", "null", "bounds", Json.arr(0, 10, 200, 60), "text", "old");
      JSONObject updated = Json.obj("view_id", "null", "bounds", Json.arr(0, 10, 200, 60), "text", "new", "editable", true);
      yes(Tools.typedField(original, Json.obj("nodes", Json.arr(updated))).getString("text").equals("new"));
      rejects(() -> Tools.typedField(original, Json.obj("nodes", Json.arr(updated, updated))));
      updated.put("password", true);
      yes(Tools.typedField(original, Json.obj("nodes", Json.arr(updated))) == null);
    });
    check("public page actions can be batched with fresh result references", () -> {
      ActionPlan.validate(Json.arr(Json.obj("tool", "web_page", "args", Json.obj("action", "read")),
          Json.obj("tool", "web_page", "args", Json.obj("action", "type", "snapshot", Json.obj("$ref", "0.snapshot")))));
    });
    check("cached public searches require parameterized typing and final verification", () -> {
      JSONArray search = Json.arr(
          Json.obj("tool", "ui_target", "args", Json.obj("scope", "public", "context", "https://example.com", "label", "Query", "action", "type", "text", Json.obj("$param", "query"))),
          Json.obj("tool", "ui_target", "args", Json.obj("scope", "public", "context", "https://example.com", "label", Json.obj("$param", "result"), "match", "text", "action", "wait")));
      ActionPlan.validateReusable(search);
      search.getJSONObject(0).getJSONObject("args").put("text", "private literal");
      rejects(() -> ActionPlan.validateReusable(search));
      search.getJSONObject(0).getJSONObject("args").put("text", Json.obj("$ref", "0.text"));
      rejects(() -> ActionPlan.validateReusable(search));
    });
    check("readiness excludes field values and snapshot IDs but detects real results", () -> {
      JSONObject baseline = Json.obj("url", "https://example.com", "text", "Pending", "snapshot", "old",
          "nodes", Json.arr(Json.obj("tag", "INPUT", "label", "Query", "value", "old")));
      PageReadiness ready = new PageReadiness(baseline, true);
      JSONObject typed = new JSONObject(baseline.toString());
      typed.put("snapshot", "fresh").getJSONArray("nodes").getJSONObject(0).put("value", "query");
      yes(!ready.ready(typed, 0) && !ready.ready(typed, 1000));
      yes(!PageReadiness.contains(typed, "query"));
      typed.put("text", "Results: The   Wire");
      yes(!ready.ready(typed, 1100) && ready.ready(typed, 1250));
      yes(PageReadiness.contains(typed, "results: the wire"));
      rejects(() -> PageReadiness.contains(typed, " "));
    });
    check("public origins enforce the same fresh scope guard", () -> {
      Tools.checkScope(Json.obj("url", "https://example.com/search"), "public", "https://example.com");
      rejects(() -> Tools.checkScope(Json.obj("url", "https://other.com"), "public", "https://example.com"));
    });
    check("parameter shorthand resolves and preflights instead of typing a placeholder", () -> {
      yes(ActionPlan.resolve("$param.query", Json.arr(), Json.obj("query", "title")).equals("title"));
      JSONObject schema = Json.obj("type", "object", "properties", Json.obj("text", Json.obj("type", "string")), "required", Json.arr("text"));
      ToolArguments.preflight(schema, Json.obj("text", "$param.query"), Json.obj("query", "title"));
      rejects(() -> ToolArguments.preflight(schema, Json.obj("text", "$param.query"), Json.obj()));
      rejects(() -> ToolArguments.preflight(schema, Json.obj("text", "$param.query"), Json.obj("query", Json.obj())));
      JSONArray search = Json.arr(
          Json.obj("tool", "ui_target", "args", Json.obj("scope", "public", "context", "https://example.com", "label", "Query", "action", "type", "text", "$param.query")),
          Json.obj("tool", "ui_target", "args", Json.obj("scope", "public", "context", "https://example.com", "label", "$param.heading", "action", "wait")));
      ActionPlan.validateReusable(search);
    });
    check("large plans retain final observed links and every step status", () -> {
      JSONArray nodes = new JSONArray();
      for (int i = 0; i < 100; i++) nodes.put(Json.obj("id", i, "label", "Result" + i,
          "href", "https://example.com/" + i, "value", "", "password", false, "disabled", false));
      JSONObject observation = Json.obj("snapshot", "final", "url", "https://example.com", "nodes", nodes,
          "text", new String(new char[9000]).replace('\0', 'x'));
      JSONArray steps = Json.arr(Json.obj("performed", true, "observation", observation),
          Json.obj("verified", true, "observation", observation));
      JSONObject input = Json.obj("completed_steps", 2, "requested_steps", 2, "stopped_early", false, "results", steps);
      JSONObject output = new JSONObject(AssistantEngine.toolOutput(input));
      yes(output.toString().length() <= 16000 && output.getJSONArray("results").length() == 2);
      yes(output.getJSONArray("results").getJSONObject(1).getBoolean("verified"));
      yes(output.getJSONObject("observation").getString("snapshot").equals("final")
          && output.getJSONObject("observation").getJSONArray("nodes").getJSONObject(0).getString("href").equals("https://example.com/0"));
      yes(input.getJSONArray("results").getJSONObject(0).has("observation"));
      steps.put(1, Json.obj("error", "Stopped on drift"));
      output = new JSONObject(AssistantEngine.toolOutput(input));
      yes(output.getJSONArray("results").getJSONObject(1).optString("error").equals("Stopped on drift"));
    });
    check("cached public searches cannot freeze a previous query's verification", () -> {
      JSONArray search = Json.arr(
          Json.obj("tool", "ui_target", "args", Json.obj("scope", "public", "context", "https://example.com", "label", "Query", "action", "type", "text", "$param.query")),
          Json.obj("tool", "ui_target", "args", Json.obj("scope", "public", "context", "https://example.com", "label", "Results: previous query", "action", "wait")));
      rejects(() -> ActionPlan.validateReusable(search));
    });
    check("microphone probe distinguishes no samples from zero PCM", () -> {
      MicrophoneLevels levels = new MicrophoneLevels();
      yes(levels.samples() == 0 && levels.rms() == 0 && levels.observation().contains("No PCM"));
      levels.accept(new short[160], 160);
      yes(levels.samples() == 160 && levels.nonzero() == 0 && levels.peak() == 0
          && levels.durationMs(16000) == 10 && levels.observation().contains("silent"));
    });
    check("microphone PCM handles both full-scale extremes without overflow", () -> {
      MicrophoneLevels levels = new MicrophoneLevels();
      levels.accept(new short[] {Short.MIN_VALUE, Short.MAX_VALUE}, 2);
      yes(levels.peak() == 32768 && levels.nonzero() == 2
          && levels.rms() > 32767 && levels.rms() < 32768);
    });
    check("microphone levels include quiet chunks, zeros and all samples", () -> {
      MicrophoneLevels levels = new MicrophoneLevels();
      levels.accept(new short[] {1000, -1000, 9999}, 2);
      levels.accept(new short[] {0, 0}, 2);
      yes(levels.samples() == 4 && levels.nonzero() == 2 && levels.peak() == 1000
          && Math.abs(levels.rms() - Math.sqrt(500000)) < 0.001
          && levels.observation().contains("does not establish"));
    });
    check("microphone level validation rejects invalid buffer bounds and rates", () -> {
      MicrophoneLevels levels = new MicrophoneLevels();
      rejects(() -> levels.accept(new short[1], 2));
      rejects(() -> levels.accept(new short[1], -1));
      rejects(() -> levels.durationMs(0));
      rejects(() -> levels.durationMs(-1));
      yes(levels.samples() == 0);
    });
    check("microphone source preferences are bounded and cycle safely", () -> {
      yes(MicrophoneProbe.sourceIndex(-1) == 0 && MicrophoneProbe.sourceIndex(999) == 0
          && MicrophoneProbe.sourceName(999).equals("VOICE_RECOGNITION")
          && MicrophoneProbe.nextSource(3) == 0 && MicrophoneProbe.nextSource(-1) == 1);
    });
    System.out.println(passed + " passed, 0 failed (offline JVM checks; no device)");
  }
}
