package dev.mert.tvassistant;

import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.*;
import org.json.JSONObject;

/** Learns one actual remote key; never guesses a vendor's Mi keycode. */
final class ActivationKey {
  interface Listener { void activated(); void changed(String message); }
  private final SharedPreferences prefs;
  private final Listener listener;
  private long until;
  private int consumed = -1;
  private String lastLearning="none";
  ActivationKey(SharedPreferences prefs, Listener listener) { this.prefs=prefs; this.listener=listener; }
  void arm() { until=SystemClock.elapsedRealtime()+45000;lastLearning="waiting"; }
  boolean learning() { return until>SystemClock.elapsedRealtime(); }
  void expire() {
    if(until!=0 && !learning()) { until=0; listener.changed("Кнопка не получена. Повтори назначение Mi из панели."); }
  }
  void cancel() { until=0; }
  static boolean allowed(int code) {
    if(code<=0 || code>500) return false;
    switch(code) {
      case KeyEvent.KEYCODE_HOME: case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_POWER:
      case KeyEvent.KEYCODE_VOLUME_UP: case KeyEvent.KEYCODE_VOLUME_DOWN: case KeyEvent.KEYCODE_VOLUME_MUTE:
      case KeyEvent.KEYCODE_MUTE: case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN:
      case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_DPAD_CENTER:
      case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER: case KeyEvent.KEYCODE_TAB:
      case KeyEvent.KEYCODE_SLEEP: case KeyEvent.KEYCODE_WAKEUP: case KeyEvent.KEYCODE_TV_POWER:
      case KeyEvent.KEYCODE_STB_POWER: case KeyEvent.KEYCODE_AVR_POWER: case KeyEvent.KEYCODE_ASSIST:
      case KeyEvent.KEYCODE_VOICE_ASSIST: case KeyEvent.KEYCODE_SEARCH:
        return false;
      default: return true;
    }
  }
  boolean handle(KeyEvent event) {
    int code=event.getKeyCode();
    if(event.getAction()==KeyEvent.ACTION_UP) {
      if(code==consumed) { consumed=-1; return true; } return false;
    }
    if(event.getAction()!=KeyEvent.ACTION_DOWN) return false;
    if(code==consumed && event.getRepeatCount()>0) return true;
    expire();
    if(learning()) {
      if(code==KeyEvent.KEYCODE_BACK) { until=0; consumed=code; listener.changed("Назначение Mi отменено."); return true; }
      if(event.getRepeatCount()!=0){lastLearning="repeat";return false;}
      if(event.getDeviceId()==KeyCharacterMap.VIRTUAL_KEYBOARD){lastLearning="virtual_event";return false;}
      if(!allowed(code)){lastLearning="reserved_key";return false;}
      int scan=event.getScanCode();
      boolean saved=prefs.edit().putInt("activation_keycode",code).putInt("activation_scancode",scan)
          .putBoolean("mini_mode",true).putBoolean(NavigationService.PREF_VOICE_BUTTON,false).commit();
      until=0; consumed=code;
      lastLearning=saved?"bound":"save_failed";
      listener.changed(saved?"Кнопка Mi назначена. Нажми её ещё раз и говори.":"Не удалось сохранить кнопку. Повтори назначение.");
      return true;
    }
    int bound=prefs.getInt("activation_keycode",-1),scan=prefs.getInt("activation_scancode",0);
    if(code!=bound || !allowed(code) || scan!=0 && scan!=event.getScanCode()) return false;
    consumed=code;
    if(event.getRepeatCount()==0) listener.activated();
    return true;
  }
  JSONObject report() {
    int code=prefs.getInt("activation_keycode",-1);
    return Json.obj("bound",allowed(code),"keycode",allowed(code)?code:JSONObject.NULL,
        "scancode",allowed(code)?prefs.getInt("activation_scancode",0):JSONObject.NULL,"learning",learning(),"last_learning_event",lastLearning);
  }
}
