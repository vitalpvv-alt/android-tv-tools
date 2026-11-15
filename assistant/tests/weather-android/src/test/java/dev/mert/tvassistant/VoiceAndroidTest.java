package dev.mert.tvassistant;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.view.KeyEvent;
import android.view.accessibility.*;
import android.accessibilityservice.AccessibilityService;
import java.time.Duration;
import java.io.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE)
public class VoiceAndroidTest {
  static class Heard implements VoiceInput.Listener {
    final List<String> commands = new ArrayList<>();
    String status;
    public void recognized(String text) { commands.add(text); }
    public void status(String text) { status=text; }
  }
  private Activity activity(boolean recognizer) {
    Activity a=Robolectric.buildActivity(Activity.class).setup().get();
    if(recognizer) {
      ResolveInfo r=new ResolveInfo();r.activityInfo=new ActivityInfo();
      r.activityInfo.packageName="com.google.android.katniss";
      r.activityInfo.name="com.google.android.apps.tvsearch.RecognitionActivity";
      Shadows.shadowOf(a.getPackageManager()).addResolveInfoForIntent(VoiceInput.request(),r);
    }
    return a;
  }
  private Intent result(String text) {
    return new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,new ArrayList<>(Arrays.asList(text)));
  }
  @Test public void speechUsesSystemRecognitionInRussianAndDuplicateStartIsIgnored() {
    Activity a=activity(true);Heard h=new Heard();VoiceInput v=new VoiceInput(a,h);
    assertTrue(v.start());assertTrue(v.active());assertFalse(v.start());
    ShadowActivity.IntentForResult launch=Shadows.shadowOf(a).getNextStartedActivityForResult();
    assertEquals(VoiceInput.REQUEST,launch.requestCode);
    assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH,launch.intent.getAction());
    assertEquals("ru-RU",launch.intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));
    assertEquals(1,launch.intent.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS,0));
    assertFalse(launch.intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true));
    assertNull(Shadows.shadowOf(a).getNextStartedActivityForResult());
    v.close();a.finish();
  }
  @Test public void recognizedResultReachesCommandCallbackExactlyOnce() {
    Activity a=activity(true);Heard h=new Heard();VoiceInput v=new VoiceInput(a,h);
    v.start();v.result(Activity.RESULT_OK,result("  Покажи информацию о телевизоре  "));
    v.result(Activity.RESULT_OK,result("Сделай громче"));
    assertEquals(Arrays.asList("Покажи информацию о телевизоре"),h.commands);assertFalse(v.active());
    a.finish();
  }
  @Test public void cancellationAndInvalidResultsCannotExecutePreviousCommand() {
    Activity a=activity(true);Heard h=new Heard();VoiceInput v=new VoiceInput(a,h);
    v.start();v.result(Activity.RESULT_CANCELED,result("Открой Винк"));
    assertTrue(h.commands.isEmpty());assertFalse(v.active());
    for(String s:new String[]{"", "   ", "a\nb", new String(new char[2001]).replace('\0','x')}) {
      assertTrue(v.start());v.result(Activity.RESULT_OK,result(s));assertTrue(h.commands.isEmpty());assertFalse(v.active());
    }
    assertTrue(v.start());v.result(Activity.RESULT_OK,null);assertTrue(h.commands.isEmpty());a.finish();
  }
  @Test public void unavailableRecognizerKeepsKeyboardPathWithoutLaunchingSearch() {
    Activity a=activity(false);Heard h=new Heard();VoiceInput v=new VoiceInput(a,h);
    assertFalse(v.start());assertFalse(v.active());assertNotNull(h.status);
    assertNull(Shadows.shadowOf(a).getNextStartedActivityForResult());assertTrue(h.commands.isEmpty());a.finish();
  }
  @Test public void googleRoutingGuardsOwnRecognitionKeyboardSettingsAndOtherApps() {
    String p=VoiceActivation.GOOGLE_TV,c="com.google.android.apps.tvsearch.app.launch.trampoline.SearchActivity";
    assertTrue(VoiceActivation.intercept(p,c,true,false,false,false));
    assertFalse(VoiceActivation.intercept(p,c,false,false,false,false));
    assertFalse(VoiceActivation.intercept(p,c,true,true,false,false));
    assertFalse(VoiceActivation.intercept(p,c,true,false,true,false));
    assertFalse(VoiceActivation.intercept(p,c,true,false,false,true));
    assertFalse(VoiceActivation.googleWindow("com.google.android.youtube.tv",c));
    assertFalse(VoiceActivation.googleWindow(p,"com.google.android.apps.tvsearch.settings.SettingsActivity"));
    assertFalse(VoiceActivation.googleWindow(p,"com.google.android.apps.tvsearch.account.SignInActivity"));
    assertFalse(VoiceActivation.googleWindow(p,"android.widget.EditText"));
    assertFalse(VoiceActivation.googleWindow(p,null));
    assertEquals("[unknown]",VoiceActivation.componentText("private text or token / not a class"));
  }
  @Test public void mainActivityVoiceIntentDoesNotOpenKeyboardOrStartTwoRecognizers() {
    Activity registrar=activity(true);
    MainActivity a=Robolectric.buildActivity(MainActivity.class,
        new Intent().putExtra(NavigationService.PREF_VOICE_BUTTON,true)).setup().get();
    try {
      a.onWindowFocusChanged(true);Shadows.shadowOf(Looper.getMainLooper()).idle();
      ShadowActivity.IntentForResult launch=Shadows.shadowOf(a).getNextStartedActivityForResult();
      assertNotNull(launch);assertEquals(VoiceInput.REQUEST,launch.requestCode);assertTrue(MainActivity.voiceInputActive());
      a.onNewIntent(new Intent().putExtra(NavigationService.PREF_VOICE_BUTTON,true));
      a.onWindowFocusChanged(true);Shadows.shadowOf(Looper.getMainLooper()).idle();
      assertNull(Shadows.shadowOf(a).getNextStartedActivityForResult());
      a.onActivityResult(VoiceInput.REQUEST,Activity.RESULT_CANCELED,null);
      assertFalse(MainActivity.voiceInputActive());
    } finally {a.onDestroy();registrar.finish();}
  }
  @Test public void mainThreadDumpsReturnUsableWeatherAndBoundedVoiceReports() throws Exception {
    NavigationService s=Robolectric.buildService(NavigationService.class).create().get();
    HomeWeather w=new HomeWeather(s);ReflectionHelpers.setField(s,"homeWeather",w);
    ReflectionHelpers.setField(s,"prefs",s.getSharedPreferences("assistant",0));
    try {
      StringWriter weather=new StringWriter();s.dump(null,new PrintWriter(weather),new String[]{"weather"});
      JSONObject wr=new JSONObject(weather.toString().trim().substring("TV_WEATHER_JSON=".length()));
      assertEquals(1,wr.getInt("version"));assertFalse(wr.getBoolean("visible"));
      assertFalse(wr.getBoolean("ambient_sensor_available"));
      for(int n=0;n<50;n++)s.onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));
      StringWriter voice=new StringWriter();s.dump(null,new PrintWriter(voice),new String[]{"voice"});
      JSONObject vr=new JSONObject(voice.toString().trim().substring("TV_VOICE_JSON=".length()));
      assertEquals(1,vr.getInt("version"));assertEquals(32,vr.getJSONArray("keys").length());
      assertEquals(0,vr.getJSONArray("google_windows").length());
      assertFalse(vr.toString().contains("EXTRA_RESULTS"));
    } finally {s.onDestroy();}
  }
  @Test public void googleWindowRoutesOnceAfterDismissingItAndReleasingAudio() {
    NavigationService s=Robolectric.buildService(NavigationService.class).create().get();
    NavigationService.instance=s;ReflectionHelpers.setField(s,"prefs",s.getSharedPreferences("assistant",0));
    s.getSharedPreferences("assistant",0).edit().putBoolean("mini_mode",false).commit();
    ShadowAccessibilityService shadow=Shadows.shadowOf(s);
    AccessibilityNodeInfo root=AccessibilityNodeInfo.obtain();root.setPackageName(VoiceActivation.GOOGLE_TV);
    shadow.setRootInActiveWindow(root);
    AccessibilityEvent e=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    e.setPackageName(VoiceActivation.GOOGLE_TV);e.setClassName("com.google.android.apps.tvsearch.app.launch.trampoline.SearchActivity");
    try {
      s.onAccessibilityEvent(e);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));
      assertEquals(Arrays.asList(AccessibilityService.GLOBAL_ACTION_BACK),shadow.getGlobalActionsPerformed());
      Intent next=shadow.getNextStartedActivity();assertNotNull(next);
      assertEquals(MainActivity.class.getName(),next.getComponent().getClassName());
      assertTrue(next.getBooleanExtra(NavigationService.PREF_VOICE_BUTTON,false));
      s.onAccessibilityEvent(e);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));
      assertNull(shadow.getNextStartedActivity());assertEquals(1,shadow.getGlobalActionsPerformed().size());
    } finally {e.recycle();s.onDestroy();}
  }
  @Test public void delayedGoogleCallbackCannotSendBackToAnAppThatReplacedIt() {
    NavigationService s=Robolectric.buildService(NavigationService.class).create().get();
    NavigationService.instance=s;ReflectionHelpers.setField(s,"prefs",s.getSharedPreferences("assistant",0));
    s.getSharedPreferences("assistant",0).edit().putBoolean("mini_mode",false).commit();
    ShadowAccessibilityService shadow=Shadows.shadowOf(s);
    AccessibilityNodeInfo root=AccessibilityNodeInfo.obtain();root.setPackageName("ru.rt.video.app.tv");
    shadow.setRootInActiveWindow(root);
    AccessibilityEvent e=AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    e.setPackageName(VoiceActivation.GOOGLE_TV);e.setClassName("com.google.android.apps.tvsearch.SearchActivity");
    try {
      s.onAccessibilityEvent(e);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));
      assertTrue(shadow.getGlobalActionsPerformed().isEmpty());assertNull(shadow.getNextStartedActivity());
    } finally {e.recycle();s.onDestroy();}
  }
}
