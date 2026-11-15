package dev.mert.tvassistant;

import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.view.*;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Accessibility overlay and speech client; it never launches a search or full-screen Activity. */
final class MiniAssistant implements MiniSpeech.Listener, AssistantEngine.Listener {
  interface Runner { void run(String text); void stop(); boolean busy(); int steps(); void close(); }
  private final NavigationService service;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final WindowManager windows;
  private final MiniCard card;
  private final MiniSpeech speech;
  private final AnswerSpeech voice;
  private final Set<Integer> readKeys=new HashSet<>();
  private Runner runner;
  private boolean visible,closed,dismissed;
  private String mode="idle",query="",reply="";
  private int completed,lastSteps;
  private final Runnable hideLater=this::maybeHide;
  private void maybeHide(){
    if(voiceBusy()){autoHide(500);return;}
    if(!card.longAnswer())hide();
  }
  private final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
    if(Intent.ACTION_SCREEN_OFF.equals(i.getAction())){speech.cancel();if(busy())runner.stop();dismissed=true;hide();}
  }};

  MiniAssistant(NavigationService service) { this(service,null); }
  MiniAssistant(NavigationService service,Runner runner) {
    this.service=service;this.runner=runner;
    windows=(WindowManager)service.getSystemService(Context.WINDOW_SERVICE);
    card=new MiniCard(service);speech=new MiniSpeech(service,this);
    SharedPreferences p=service.getSharedPreferences("assistant",0);
    if(!p.getBoolean("mini_spoken_answers_v1",false))p.edit().putBoolean("speech",true).putBoolean("mini_spoken_answers_v1",true).commit();
    voice=new AnswerSpeech(service,()->{if(visible&&!closed)paint();});
    IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_OFF);
    if(Build.VERSION.SDK_INT>=33)service.registerReceiver(screen,f,Context.RECEIVER_NOT_EXPORTED);else service.registerReceiver(screen,f);
  }
  boolean busy() { return runner!=null && runner.busy(); }
  private boolean voiceBusy(){return voice!=null&&voice.busy();}
  void toggle() {
    if(closed)return;
    voice.stop();
    if(speech.active() || busy()) { speech.cancel();if(busy())runner.stop();dismissed=true;hide();return; }
    if(MainActivity.modelBusy()) { message("Заверши текущую задачу в помощнике.");return; }
    dismissed=false;query="Говори команду…";reply="";mode="listening";show();
    if(visible)speech.start();
  }
  void learningPrompt() {
    voice.stop();speech.cancel();dismissed=false;mode="learn";query="Нажми кнопку Mi один раз";
    reply="Программа запомнит именно эту кнопку. Назад — отмена.";show();
  }
  void message(String text) {
    if(closed)return;
    dismissed=false;mode="message";query=text;reply="";show();autoHide(9000);
  }
  void preview() {
    if(busy())return;
    speech.cancel();dismissed=false;mode="preview";query="Покажи информацию о телевизоре";
    reply="Проверка мини-окна. Команды не выполняются.";show();autoHide(12000);
  }
  void testVoice(){
    if(closed||busy()||speech.active())return;
    dismissed=false;mode="result";query="Проверка русского голоса";reply="Голосовой помощник готов.";
    show();voice.say(reply);autoHide(12000);
  }
  private void show() {
    if(closed)return;
    handler.removeCallbacks(hideLater);paint();
    if(visible)return;
    float density=service.getResources().getDisplayMetrics().density;
    int width=card.preferredWidth();
    WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            |WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
    p.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;p.y=Math.round(24*density);p.setTitle("ТВ-помощник мини");
    try{windows.addView(card,p);visible=true;}catch(RuntimeException e){mode="overlay_unavailable";visible=false;}
  }
  private void paint() {
    String state=mode.equals("learn")?"назначение Mi":mode.equals("listening")?"слушаю"
        :mode.equals("working")?"выполняю":mode.equals("result")?"готово"
        :mode.equals("preview")?"проверка":mode.equals("error")?"голосовой ввод":"Mi";
    card.content(state,query,reply,mode.equals("result"));card.voiceNotice(voice==null?"":voice.notice());
    if(visible){
      WindowManager.LayoutParams p=(WindowManager.LayoutParams)card.getLayoutParams();int width=card.preferredWidth();
      if(p.width!=width){p.width=width;try{windows.updateViewLayout(card,p);}catch(RuntimeException ignored){}}
    }
  }
  private void autoHide(int ms) { handler.removeCallbacks(hideLater);handler.postDelayed(hideLater,ms); }
  void hide() {
    handler.removeCallbacks(hideLater);
    if(voice!=null)voice.stop();
    if(visible)try{windows.removeView(card);}catch(RuntimeException ignored){}
    visible=false;query="";reply="";
  }
  public void partial(String text) { if(!closed&&!dismissed){query=text;paint();} }
  public void status(String text) { if(!closed&&!dismissed){reply=text;paint();} }
  public void failed(String text) {
    if(closed||dismissed)return;
    mode="error";reply=text;paint();autoHide(10000);
  }
  public void recognized(String text) {
    if(closed||dismissed)return;
    query=text;reply="Выполняю команду…";mode="working";paint();
    try {
      if(runner==null)runner=new EngineRunner();
      runner.run(text);
    }catch(Exception e){failed("Не удалось запустить помощник. Проверь настройки ИИ в приложении.");}
  }
  public void state(String value) {
    handler.post(()->{if(closed||dismissed||!visible)return;
      if(!"Ready".equals(value)&&!"Stopped".equals(value)){reply="Выполняю команду…";paint();}
    });
  }
  public void answer(String text) {
    handler.post(()->{if(closed||dismissed||!visible)return;mode="result";reply=RuUi.text(text==null?"":text);paint();voice.say(reply);});
  }
  public void finished() {
    handler.post(()->{if(closed)return;completed++;lastSteps=runner==null?0:runner.steps();
      if(!dismissed&&visible)autoHide(12000);
    });
  }
  JSONObject speechReport() { return speech.report(); }
  JSONObject report() {
    return Json.obj("visible",visible,"mode",mode,"busy",busy(),"completed_commands",completed,
        "last_tool_steps",lastSteps,"width_px",visible?card.getLayoutParams().width:0,
        "answer_scrollable",visible&&card.canScrollReply(),"answer_scroll_y",visible?card.replyScrollY():0,"spoken_answers",voice.report(),
        "full_screen_activity_started",false);
  }
  boolean handleReadKey(KeyEvent event){
    int code=event.getKeyCode();
    if(event.getAction()==KeyEvent.ACTION_UP)return readKeys.remove(code);
    if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
    if(readKeys.contains(code)){
      if(visible&&(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN))card.scrollReply(code==KeyEvent.KEYCODE_DPAD_DOWN?1:-1);
      return true;
    }
    if(!visible)return false;
    if(code==KeyEvent.KEYCODE_HOME){dismissed=true;speech.cancel();if(busy())runner.stop();hide();return false;}
    if(code==KeyEvent.KEYCODE_BACK){readKeys.add(code);dismissed=true;speech.cancel();if(busy())runner.stop();hide();return true;}
    if(mode.equals("result")&&card.canScrollReply()&&(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN)){
      readKeys.add(code);card.scrollReply(code==KeyEvent.KEYCODE_DPAD_DOWN?1:-1);return true;
    }
    return false;
  }
  void close() {
    closed=true;speech.close();voice.close();handler.removeCallbacksAndMessages(null);
    try{service.unregisterReceiver(screen);}catch(RuntimeException ignored){}
    if(runner!=null)runner.close();hide();
  }
  private final class EngineRunner implements Runner,Tools.Host {
    private final Tools tools;
    private final AssistantEngine engine;
    EngineRunner() throws Exception {
      tools=new Tools(service,this);
      engine=new AssistantEngine(service,tools,null,new ModelConfig(service),MiniAssistant.this);
    }
    public void run(String text) { engine.clear();engine.run(text,false); }
    public void stop() { engine.stop(); }
    public boolean busy() { return engine.busy(); }
    public int steps() { return tools.usedSteps(); }
    public boolean cancelled() { return engine.cancelled(); }
    public <T>T ui(Callable<T> task)throws Exception {
      if(Looper.myLooper()==Looper.getMainLooper())return task.call();
      FutureTask<T> f=new FutureTask<>(task);handler.post(f);
      try{return f.get(20,TimeUnit.SECONDS);}catch(ExecutionException e){Throwable c=e.getCause();if(c instanceof Exception)throw(Exception)c;throw e;}
    }
    public boolean approve(String description) {
      throw new IllegalStateException("Для этого действия нужно подтверждение в полном приложении ТВ-помощник.");
    }
    public void trace(String text) {}
    public void speak(String text) {
      handler.post(()->{if(!closed)voice.say(RuUi.text(text));});
    }
    public void close() { engine.shutdown();tools.close(); }
  }
}
