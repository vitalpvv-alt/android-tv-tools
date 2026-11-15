package dev.mert.tvassistant;

import android.content.*;
import android.media.*;
import android.os.*;
import android.speech.tts.*;
import java.util.*;
import org.json.JSONObject;

/** Russian spoken answers through the installed TTS engine; no audio files or volume changes. */
final class AnswerSpeech implements SharedPreferences.OnSharedPreferenceChangeListener {
  interface Listener { void changed(); }
  private final Context context;
  private final SharedPreferences prefs;
  private final Listener listener;
  private final Handler handler=new Handler(Looper.getMainLooper());
  private final AudioManager audio;
  private TextToSpeech tts;
  private boolean closed,initialized,initSucceeded,ready,speaking,focusHeld;
  private int generation,initGeneration,languageStatus=TextToSpeech.LANG_MISSING_DATA,queued;
  private String pending="",lastId="",outcome="not_started",engineName="";
  private final AudioManager.OnAudioFocusChangeListener focus=change->{
    if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)handler.post(()->{stop();notifyState();});
  };
  AnswerSpeech(Context context,Listener listener){
    this.context=context;this.listener=listener;prefs=context.getSharedPreferences("assistant",0);
    audio=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);prefs.registerOnSharedPreferenceChangeListener(this);
    if(prefs.getBoolean("speech",false))initialize();
  }
  private void initialize(){
    if(closed||tts!=null)return;outcome="initializing";initialized=false;initSucceeded=false;ready=false;
    final int initToken=++initGeneration;
    try{
      tts=new TextToSpeech(context,status->handler.post(()->{
        if(closed||tts==null||initToken!=initGeneration)return;initialized=true;
        if(status!=TextToSpeech.SUCCESS){ready=false;outcome="init_failed";pending="";notifyState();return;}
        try{
          initSucceeded=true;engineName=tts.getDefaultEngine();configureLanguage();
          tts.setAudioAttributes(new AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_MUSIC).build());
          if(tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            public void onStart(String id){handler.post(()->{if(current(id)){outcome="speaking";notifyState();}});}
            public void onDone(String id){handler.post(()->{if(current(id)&&id.equals(lastId)){speaking=false;outcome="spoken";releaseFocus();notifyState();}});}
            public void onError(String id){handler.post(()->{if(current(id)){stop();outcome="speak_error";notifyState();}});}
            public void onError(String id,int code){onError(id);}
          })==TextToSpeech.ERROR)throw new IllegalStateException("TTS listener unavailable");
          if(ready&&!pending.isEmpty())enqueue();else notifyState();
        }catch(RuntimeException e){ready=false;initSucceeded=false;outcome="init_failed";pending="";notifyState();}
      }));
      handler.postDelayed(()->{if(!closed&&!initialized&&initToken==initGeneration){pending="";outcome="init_timeout";notifyState();}},8000);
    }catch(RuntimeException e){outcome="init_failed";pending="";notifyState();}
  }
  private void configureLanguage(){
    languageStatus=tts.setLanguage(new Locale("ru","RU"));ready=languageStatus>=0;
    if(ready){
      try{Set<Voice> voices=tts.getVoices();Voice best=null;
      if(voices!=null)for(Voice v:voices)if("ru".equals(v.getLocale().getLanguage())&&!v.isNetworkConnectionRequired()
          &&(v.getFeatures()==null||!v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
          &&(best==null||v.getQuality()>best.getQuality()))best=v;
      if(best!=null)tts.setVoice(best);}catch(RuntimeException ignored){}
      outcome="ready";
    }else{outcome="russian_voice_missing";pending="";}
  }
  void say(String text){
    if(closed)return;stop();
    if(!prefs.getBoolean("speech",false)){outcome="disabled";notifyState();return;}
    pending=text==null?"":text.trim();if(pending.isEmpty())return;
    if(tts!=null&&!initSucceeded&&(initialized||outcome.equals("init_timeout"))){tts.shutdown();tts=null;}
    if(tts==null)initialize();
    if(initialized&&initSucceeded){
      try{if(!ready)configureLanguage();if(ready)enqueue();else notifyState();}
      catch(RuntimeException e){pending="";outcome="speak_error";notifyState();}
    }
  }
  private boolean current(String id){return !closed&&id!=null&&id.startsWith("mini-voice-"+generation+"-");}
  private void enqueue(){
    if(closed||!ready||pending.isEmpty()||!prefs.getBoolean("speech",false))return;
    List<String> parts=chunks(pending,Math.min(3500,TextToSpeech.getMaxSpeechInputLength()));pending="";
    if(parts.isEmpty())return;
    try{
    if(audio!=null){focusHeld=audio.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
      if(!focusHeld){outcome="audio_focus_denied";notifyState();return;}}
    int token=++generation;queued=parts.size();speaking=true;outcome="queued";
    lastId="mini-voice-"+token+"-"+(parts.size()-1);
    Bundle params=new Bundle();params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM,AudioManager.STREAM_MUSIC);
    for(int i=0;i<parts.size();i++)if(tts.speak(parts.get(i),i==0?TextToSpeech.QUEUE_FLUSH:TextToSpeech.QUEUE_ADD,params,"mini-voice-"+token+"-"+i)==TextToSpeech.ERROR){
      stop();outcome="speak_error";break;
    }
    int length=0;for(String part:parts)length+=part.length();
    handler.postDelayed(()->{if(!closed&&generation==token&&speaking){stop();outcome="speak_error";notifyState();}},Math.min(600000L,20000L+length*150L));
    }catch(RuntimeException e){stop();outcome="speak_error";}
    notifyState();
  }
  static List<String> chunks(String text,int limit){
    if(limit<2)throw new IllegalArgumentException("TTS chunk too small");
    List<String> out=new ArrayList<>();String s=text==null?"":text.trim();
    while(!s.isEmpty()){
      int end=Math.min(limit,s.length());
      if(end<s.length()){
        int at=-1;for(int i=end-1;i>=end/2;i--)if(Character.isWhitespace(s.charAt(i))||".!?;".indexOf(s.charAt(i))>=0){at=i+1;break;}
        if(at>0)end=at;if(Character.isHighSurrogate(s.charAt(end-1)))end--;
      }
      String part=s.substring(0,end).trim();if(!part.isEmpty())out.add(part);s=s.substring(end).trim();
    }
    return out;
  }
  boolean busy(){return speaking||!pending.isEmpty();}
  void stop(){
    boolean active=busy();generation++;pending="";speaking=false;queued=0;
    if(active)outcome="cancelled";
    if(tts!=null)try{tts.stop();}catch(RuntimeException ignored){}
    releaseFocus();
  }
  private void releaseFocus(){if(focusHeld&&audio!=null){audio.abandonAudioFocus(focus);focusHeld=false;}}
  private void notifyState(){if(!closed)listener.changed();}
  String notice(){
    if(!prefs.getBoolean("speech",false))return "";
    if(speaking)return "Озвучиваю ответ…";
    if(outcome.equals("russian_voice_missing"))return "Русский голос не установлен";
    if(outcome.equals("init_failed")||outcome.equals("init_timeout")||outcome.equals("speak_error"))return "Озвучка недоступна";
    if(outcome.equals("audio_focus_denied"))return "Звук занят другим приложением";
    return "";
  }
  JSONObject report(){
    return Json.obj("enabled",prefs.getBoolean("speech",false),"ready",ready,"speaking",speaking,
        "outcome",outcome,"language_status",languageStatus,"engine",engineName==null?"":engineName,"queued_parts",queued,
        "media_volume",audio==null?0:audio.getStreamVolume(AudioManager.STREAM_MUSIC));
  }
  public void onSharedPreferenceChanged(SharedPreferences p,String key){if(key.equals("speech"))handler.post(()->{
    if(!prefs.getBoolean("speech",false)){stop();outcome="disabled";notifyState();}else initialize();
  });}
  void close(){closed=true;stop();handler.removeCallbacksAndMessages(null);prefs.unregisterOnSharedPreferenceChangeListener(this);if(tts!=null)tts.shutdown();}
}
