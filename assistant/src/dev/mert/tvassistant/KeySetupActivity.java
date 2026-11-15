package dev.mert.tvassistant;

import android.app.Activity;
import android.content.Intent;
import android.net.*;
import android.os.*;
import android.view.WindowManager;
import android.widget.TextView;
import java.security.KeyPair;
import java.util.concurrent.*;
import org.json.*;

/** Only the ADB shell/system may launch this component (manifest DUMP permission). */
public final class KeySetupActivity extends Activity {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private volatile LocalServerSocket server;
  private volatile LocalSocket client;
  private volatile boolean closed;
  private TextView message;
  @Override public void onCreate(Bundle saved) {
    super.onCreate(saved);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    message = new TextView(this); message.setTextColor(0xffeeeeee); message.setBackgroundColor(0xff101721);
    message.setTextSize(24); message.setPadding(40, 40, 40, 40); message.setText("Получаю ключ ИИ с компьютера…"); setContentView(message);
    try {
      String id = getIntent().getStringExtra("session_id"), token = getIntent().getStringExtra("session_token");
      String mode = getIntent().getStringExtra("setup_mode");
      if (mode != null && !mode.equals("diagnose") && !mode.equals("diagnose_xkiro") && !mode.equals("test_xkiro")) throw new IllegalArgumentException();
      boolean diagnose = mode != null && !mode.equals("test_xkiro");
      String target = "diagnose_xkiro".equals(mode) ? "xkiro" : "openrouter";
      if (diagnose) message.setText("Проверяю доступ к " + (target.equals("xkiro") ? "xKiro" : "OpenRouter") + " с телевизора…");
      KeyProvisioning.token(token); server = new LocalServerSocket(KeyProvisioning.socketName(id));
      handler.postDelayed(() -> { close(); finish(); }, 60000);
      if ("test_xkiro".equals(mode)) message.setText("Проверяю один ответ xKiro. Действия на ТВ не выполняются…");
      new Thread(() -> receive(token, diagnose, target, "test_xkiro".equals(mode)), "TVKeySetup").start();
    } catch (Exception e) { close(); message.setText("Не удалось начать настройку. Повторите передачу из панели ПК."); handler.postDelayed(this::finish, 2000); }
  }
  private void receive(String token, boolean diagnose, String target, boolean test) {
    try {
      client = server.accept(); client.setSoTimeout(15000);
      KeyProvisioning.hello(KeyProvisioning.read(client.getInputStream()), token);
      if (test) {
        ModelConfig config=new ModelConfig(this);
        JSONObject report;
        if (MainActivity.modelBusy() || !config.configured() || !config.provider().equals("xkiro"))
          report=Json.obj("provider","xkiro","model","","http",0,"inference_ok",false,"plan_ok",false,"kind","not_ready","executed_actions",0,"elapsed_ms",0);
        else {
          final String key=config.token();
          Net.Events events=new Net.Events() { public boolean cancelled(){return closed;} public void event(JSONObject event){} };
          report=XkiroProbe.run(config.model(),Tools.launchableApps(this),new XkiroProbe.Gateway() {
            public JSONObject catalog() throws Exception {return Net.xkiroCatalog(events);}
            public JSONObject infer(JSONObject body) throws Exception {return Net.inferJson(ModelConfig.endpoint("xkiro")+"/chat/completions",key,body,events,20000);}
          });
        }
        if(closed)return;
        KeyProvisioning.write(client.getOutputStream(),Json.obj("version",1,"inference_check",report));
        handler.post(() -> {startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));finish();});
        return;
      }
      if (diagnose) {
        ModelConfig config = new ModelConfig(this);
        String key = config.provider().equals(target) && config.configured() ? config.token() : null;
        JSONObject report = OpenRouterDiagnostics.runFor(target, config.provider(), config.model(), key, Net::open, () -> closed);
        if (closed) return;
        KeyProvisioning.write(client.getOutputStream(), Json.obj("version", 1, "diagnostics", report));
        handler.post(() -> { startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); finish(); });
        return;
      }
      KeyPair keys = KeyProvisioning.keyPair();
      KeyProvisioning.write(client.getOutputStream(), KeyProvisioning.publicHello(keys));
      JSONObject payload = KeyProvisioning.decrypt(KeyProvisioning.read(client.getInputStream()), keys.getPrivate(), token);
      FutureTask<Void> save = new FutureTask<>(() -> {
        if (closed || isFinishing()) throw new IllegalStateException();
        MainActivity.receiveAiKey(this, KeyProvisioning.provider(payload), payload.getString("model"), payload.getString("key")); return null;
      });
      handler.post(save);
      try { save.get(15, TimeUnit.SECONDS); } catch (Exception e) { save.cancel(false); throw e; }
      KeyProvisioning.write(client.getOutputStream(), Json.obj("ok", true, "provider", KeyProvisioning.provider(payload), "model", payload.getString("model")));
      handler.post(() -> { message.setText("Ключ сохранён на телевизоре."); startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); finish(); });
    } catch (Exception e) {
      try { if (client != null) KeyProvisioning.write(client.getOutputStream(), Json.obj("ok", false)); } catch (Exception ignored) {}
      handler.post(() -> { message.setText("Не удалось завершить передачу. Остановите задачу помощника и повторите её из панели ПК."); handler.postDelayed(this::finish, 2000); });
    } finally { close(); }
  }
  private synchronized void close() {
    closed = true;
    if (client != null) try { client.close(); } catch (Exception ignored) {}
    if (server != null) try { server.close(); } catch (Exception ignored) {}
  }
  @Override protected void onDestroy() { close(); handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
