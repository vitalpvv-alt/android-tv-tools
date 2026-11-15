package dev.mert.tvassistant;

import android.content.*;
import android.os.*;
import android.speech.*;
import java.util.*;
import org.json.JSONObject;

/** One bounded recognition session, with no external search Activity or audio file. */
final class MiniSpeech {
  interface Listener { void partial(String text); void recognized(String text); void status(String text); void failed(String text); }
  private final Context context;
  private final Listener listener;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private SpeechRecognizer recognizer;
  private boolean active;
  private int generation, lastError;
  private String outcome="not_started";
  MiniSpeech(Context context, Listener listener) { this.context=context; this.listener=listener; }
  static Intent request() {
    return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ru-RU")
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
  }
  boolean active() { return active; }
  private boolean available() { try{return SpeechRecognizer.isRecognitionAvailable(context);}catch(RuntimeException e){return false;} }
  void start() {
    if(active) return;
    if(!available()) { outcome="recognizer_missing"; listener.failed("На ТВ не найден сервис распознавания речи."); return; }
    final int session=++generation;
    active=true; lastError=0; outcome="listening";
    try {
      recognizer=SpeechRecognizer.createSpeechRecognizer(context);
      recognizer.setRecognitionListener(new RecognitionListener() {
        private boolean current() { return active && session==generation; }
        public void onReadyForSpeech(Bundle b) { if(current())listener.status("Слушаю…"); }
        public void onBeginningOfSpeech() { if(current())listener.status("Слушаю…"); }
        public void onRmsChanged(float rms) {}
        public void onBufferReceived(byte[] audio) {}
        public void onEndOfSpeech() { if(current())listener.status("Распознаю…"); }
        public void onEvent(int type,Bundle b) {}
        public void onPartialResults(Bundle b) { if(current()) { String t=text(b); if(!t.isEmpty())listener.partial(t); } }
        public void onResults(Bundle b) {
          if(!current()) return;
          String t=text(b); release(false);
          if(t.isEmpty()) { outcome="empty_result"; listener.failed("Не удалось разобрать речь. Нажми Mi и повтори."); }
          else { outcome="recognized"; listener.recognized(t); }
        }
        public void onError(int code) {
          if(!current()) return;
          lastError=code; release(false); outcome="recognition_error";
          listener.failed(code==SpeechRecognizer.ERROR_NO_MATCH || code==SpeechRecognizer.ERROR_SPEECH_TIMEOUT
              ?"Речь не распознана. Нажми Mi и повтори."
              :code==SpeechRecognizer.ERROR_NETWORK || code==SpeechRecognizer.ERROR_NETWORK_TIMEOUT
              ?"Нет ответа от сервиса распознавания. Проверь интернет."
              :code==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS?"Для голосового ввода нужен доступ к микрофону."
              :"Голосовой ввод недоступен. Нажми Mi и повтори.");
        }
      });
      listener.status("Слушаю…"); recognizer.startListening(request());
      handler.postDelayed(()->{
        if(active && session==generation) { release(true); outcome="timeout"; listener.failed("Время голосового ввода вышло. Нажми Mi и повтори."); }
      },20000);
    } catch(RuntimeException e) { release(true); outcome="start_failed"; listener.failed("Не удалось включить распознавание речи."); }
  }
  private static String text(Bundle data) {
    if(data==null)return "";
    try {
      ArrayList<String> values=data.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
      String t=values==null||values.isEmpty()||values.get(0)==null?"":values.get(0).trim();
      return t.length()>500 || t.matches("(?s).*[\\p{Cntrl}].*")?"":t;
    } catch(RuntimeException e) { return ""; }
  }
  private void release(boolean cancel) {
    active=false; generation++; handler.removeCallbacksAndMessages(null);
    SpeechRecognizer old=recognizer;recognizer=null;
    if(old!=null) { if(cancel)try{old.cancel();}catch(RuntimeException ignored){} try{old.destroy();}catch(RuntimeException ignored){} }
  }
  void cancel() { if(active){release(true);outcome="cancelled";} }
  void close() { release(true); }
  JSONObject report() {
    String component="";
    try{String s=android.provider.Settings.Secure.getString(context.getContentResolver(),"voice_recognition_service");
      if(s!=null&&s.length()<=200&&s.matches("[A-Za-z0-9_.$/]+"))component=s;
    }catch(RuntimeException ignored){}
    return Json.obj("active",active,"outcome",outcome,"error_code",lastError,
      "recognizer_available",available(),"recognizer_component",component);
  }
}
