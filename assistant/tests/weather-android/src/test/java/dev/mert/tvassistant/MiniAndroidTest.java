package dev.mert.tvassistant;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.os.*;
import android.speech.*;
import android.view.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE,qualifiers="w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MiniAndroidTest {
  static class Heard implements MiniSpeech.Listener {
    final List<String> finals=new ArrayList<>(),partials=new ArrayList<>();String error="";
    public void partial(String t){partials.add(t);}public void recognized(String t){finals.add(t);}
    public void status(String t){}public void failed(String t){error=t;}
  }
  static class Keys implements ActivationKey.Listener {
    int activations;final List<String> messages=new ArrayList<>();
    public void activated(){activations++;}public void changed(String m){messages.add(m);}
  }
  static class Runner implements MiniAssistant.Runner {
    final List<String> calls=new ArrayList<>();boolean busy,closed;
    public void run(String text){calls.add(text);}public void stop(){busy=false;}
    public boolean busy(){return busy;}public int steps(){return calls.isEmpty()?0:1;}public void close(){closed=true;}
  }
  NavigationService service(){return Robolectric.buildService(NavigationService.class).create().get();}
  void recognizer(Context c){
    ResolveInfo r=new ResolveInfo();r.serviceInfo=new ServiceInfo();r.serviceInfo.packageName="com.google.android.katniss";
    r.serviceInfo.name="com.google.android.apps.tvsearch.RecognitionService";
    Shadows.shadowOf(c.getPackageManager()).addResolveInfoForIntent(new Intent(RecognitionService.SERVICE_INTERFACE),r);
  }
  Bundle words(String text){Bundle b=new Bundle();b.putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION,new ArrayList<>(Arrays.asList(text)));return b;}
  void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
  KeyEvent key(int action,int code,int scan,int repeat,int device){return new KeyEvent(1,SystemClock.uptimeMillis(),action,code,repeat,0,device,scan,0,InputDevice.SOURCE_DPAD);}
  @Test public void learningCapturesOnlyOnePhysicalKeyThenConsumesDownRepeatAndUp()throws Exception{
    NavigationService s=service();SharedPreferences p=s.getSharedPreferences("binding-test",0);Keys k=new Keys();ActivationKey a=new ActivationKey(p,k);
    assertFalse(a.handle(key(KeyEvent.ACTION_DOWN,191,217,0,1)));a.arm();
    assertFalse(a.handle(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_HOME,102,0,1)));
    assertFalse(a.handle(key(KeyEvent.ACTION_DOWN,191,0,0,KeyCharacterMap.VIRTUAL_KEYBOARD)));
    assertTrue(a.handle(key(KeyEvent.ACTION_DOWN,191,217,0,1)));assertTrue(a.report().getBoolean("bound"));
    assertEquals(191,a.report().getInt("keycode"));assertEquals(0,k.activations);
    assertTrue(a.handle(key(KeyEvent.ACTION_UP,191,217,0,1)));
    assertTrue(a.handle(key(KeyEvent.ACTION_DOWN,191,217,0,1)));
    assertTrue(a.handle(key(KeyEvent.ACTION_DOWN,191,217,2,1)));assertEquals(1,k.activations);
    assertTrue(a.handle(key(KeyEvent.ACTION_UP,191,217,0,1)));
    assertFalse(a.handle(key(KeyEvent.ACTION_DOWN,191,218,0,1)));
    ActivationKey afterRestart=new ActivationKey(p,k);assertTrue(afterRestart.handle(key(KeyEvent.ACTION_DOWN,191,217,0,5)));
    assertEquals(2,k.activations);s.onDestroy();
  }
  @Test public void backAndLearningTimeoutKeepThePreviousBinding()throws Exception{
    NavigationService s=service();SharedPreferences p=s.getSharedPreferences("binding-test",0);
    p.edit().putInt("activation_keycode",192).putInt("activation_scancode",218).commit();Keys k=new Keys();ActivationKey a=new ActivationKey(p,k);
    a.arm();assertTrue(a.handle(key(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK,1,0,1)));
    assertTrue(a.handle(key(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK,1,0,1)));assertFalse(a.learning());assertEquals(192,a.report().getInt("keycode"));
    a.arm();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(46));a.expire();
    assertFalse(a.learning());assertEquals(192,a.report().getInt("keycode"));assertEquals(2,k.messages.size());
    assertFalse(a.handle(key(KeyEvent.ACTION_DOWN,191,217,0,1)));s.onDestroy();
  }
  @Test public void essentialTvKeysCannotBeAssigned(){
    for(int c:new int[]{3,4,19,20,21,22,23,24,25,26,66,91,164,177,179,181,219,223,224,231})assertFalse("reserved "+c,ActivationKey.allowed(c));
    assertTrue(ActivationKey.allowed(170));assertTrue(ActivationKey.allowed(191));assertFalse(ActivationKey.allowed(0));
  }
  @Test public void directSpeechShowsPartialTextButExecutesOnlyOneFinalResult()throws Exception{
    NavigationService s=service();recognizer(s);Heard heard=new Heard();MiniSpeech speech=new MiniSpeech(s,heard);
    speech.start();idle();SpeechRecognizer real=ShadowSpeechRecognizer.getLatestSpeechRecognizer();assertNotNull(real);
    ShadowSpeechRecognizer shadow=Shadows.shadowOf(real);assertEquals("ru-RU",shadow.getLastRecognizerIntent().getStringExtra(RecognizerIntent.EXTRA_LANGUAGE));
    assertTrue(shadow.getLastRecognizerIntent().getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false));
    shadow.triggerOnPartialResults(words("Сделай потише"));idle();assertEquals(Arrays.asList("Сделай потише"),heard.partials);assertTrue(heard.finals.isEmpty());
    shadow.triggerOnResults(words("Сделай потише"));idle();shadow.triggerOnResults(words("Сделай громче"));idle();
    assertEquals(Arrays.asList("Сделай потише"),heard.finals);assertFalse(speech.active());assertTrue(shadow.isDestroyed());
    assertNull(Shadows.shadowOf(s).getNextStartedActivity());speech.close();s.onDestroy();
  }
  @Test public void cancelledAndTimedOutRecognitionCannotDeliverLateCommands()throws Exception{
    NavigationService s=service();recognizer(s);Heard h=new Heard();MiniSpeech speech=new MiniSpeech(s,h);
    speech.start();idle();ShadowSpeechRecognizer old=Shadows.shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer());speech.cancel();
    old.triggerOnResults(words("Открой Винк"));idle();assertTrue(h.finals.isEmpty());assertTrue(old.isDestroyed());
    speech.start();idle();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(21));
    assertFalse(speech.active());assertEquals("timeout",speech.report().getString("outcome"));assertFalse(h.error.isEmpty());assertTrue(h.finals.isEmpty());
    speech.close();s.onDestroy();
  }
  @Test public void missingRecognizerAndEmptyResultsDoNotStartCommands()throws Exception{
    NavigationService s=service();Heard h=new Heard();MiniSpeech speech=new MiniSpeech(s,h);
    speech.start();assertFalse(speech.active());assertEquals("recognizer_missing",speech.report().getString("outcome"));assertTrue(h.finals.isEmpty());
    recognizer(s);speech.start();idle();Shadows.shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer()).triggerOnResults(words("   "));idle();
    assertFalse(speech.active());assertTrue(h.finals.isEmpty());speech.close();s.onDestroy();
  }
  @Test public void miniOverlayNeverLaunchesAnActivityTakesFocusOrDimsVideo()throws Exception{
    NavigationService s=service();Runner r=new Runner();MiniAssistant m=new MiniAssistant(s,r);
    try{
      m.preview();MiniCard card=ReflectionHelpers.getField(m,"card");WindowManager.LayoutParams p=(WindowManager.LayoutParams)card.getLayoutParams();
      assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,p.type);
      assertTrue((p.flags&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0);assertTrue((p.flags&WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)!=0);
      assertEquals(0,p.flags&WindowManager.LayoutParams.FLAG_DIM_BEHIND);assertTrue(p.width<=960*.65);assertFalse(card.isFocusable());
      assertNull(Shadows.shadowOf(s).getNextStartedActivity());assertTrue(r.calls.isEmpty());
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(13));assertFalse(m.report().getBoolean("visible"));
    }finally{m.close();s.onDestroy();}
  }
  @Test public void miniDisplaysRecognizedRequestAndAnswerWithoutFullScreen()throws Exception{
    NavigationService s=service();recognizer(s);Runner r=new Runner();MiniAssistant m=new MiniAssistant(s,r);
    try{
      m.toggle();idle();ShadowSpeechRecognizer sh=Shadows.shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer());
      sh.triggerOnPartialResults(words("Покажи информацию"));idle();assertTrue(r.calls.isEmpty());
      sh.triggerOnResults(words("Покажи информацию о телевизоре"));idle();assertEquals(1,r.calls.size());
      m.answer("Xiaomi MiTV-MSSP1 · Android 9");m.finished();idle();
      MiniCard card=ReflectionHelpers.getField(m,"card");assertEquals("Покажи информацию о телевизоре",card.query.getText().toString());
      assertTrue(card.reply.getText().toString().contains("Xiaomi"));assertEquals(1,m.report().getInt("last_tool_steps"));
      assertNull(Shadows.shadowOf(s).getNextStartedActivity());
    }finally{m.close();s.onDestroy();}
  }
  @Test public void compactCardRendersLongRussianWithinSmallScreenArea()throws Exception{
    NavigationService s=service();MiniCard card=new MiniCard(s);
    card.content("слушаю","Покажи информацию о телевизоре и текущую громкость, пожалуйста","Xiaomi MiTV-MSSP1 · Android 9 · Громкость 14%");
    card.measure(View.MeasureSpec.makeMeasureSpec(560,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(220,View.MeasureSpec.AT_MOST));
    card.layout(0,0,560,card.getMeasuredHeight());assertTrue(card.getMeasuredHeight()<=190);assertTrue(card.getMeasuredHeight()>75);
    Bitmap image=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(0xFF252E35);
    canvas.save();canvas.translate(200,540-24-card.getMeasuredHeight());card.draw(canvas);canvas.restore();
    File dir=new File("../../reports/mini-render");dir.mkdirs();
    try(FileOutputStream out=new FileOutputStream(new File(dir,"mini-request.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();s.onDestroy();
  }
  @Test public void screenOffClosesMicrophoneAndRejectsLateSpeech()throws Exception{
    NavigationService s=service();recognizer(s);Runner r=new Runner();MiniAssistant m=new MiniAssistant(s,r);
    try{
      m.toggle();idle();ShadowSpeechRecognizer sh=Shadows.shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer());
      s.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));idle();
      assertFalse(m.report().getBoolean("visible"));assertTrue(sh.isDestroyed());
      sh.triggerOnResults(words("Открой Винк"));idle();assertTrue(r.calls.isEmpty());
    }finally{m.close();s.onDestroy();}
  }
  @Test public void serviceLearnsSyntheticRemoteKeyAndActivatesOnlyPassiveMiniOverlay()throws Exception{
    NavigationService s=service();recognizer(s);s.onServiceConnected();
    try{
      s.learnMi();assertTrue(s.onKeyEvent(key(KeyEvent.ACTION_DOWN,191,217,0,1)));
      assertTrue(s.onKeyEvent(key(KeyEvent.ACTION_UP,191,217,0,1)));
      assertTrue(s.onKeyEvent(key(KeyEvent.ACTION_DOWN,191,217,0,1)));idle();
      MiniAssistant m=ReflectionHelpers.getField(s,"mini");assertTrue(m.report().getBoolean("visible"));
      assertTrue(m.speechReport().getBoolean("active"));assertNotNull(ShadowSpeechRecognizer.getLatestSpeechRecognizer());
      assertNull(Shadows.shadowOf(s).getNextStartedActivity());
      assertTrue(s.onKeyEvent(key(KeyEvent.ACTION_UP,191,217,0,1)));
    }finally{s.onDestroy();}
  }
  @Test public void longAnswerExpandsScrollsBothWaysAndBackConsumesTheWholeKeyPair()throws Exception{
    NavigationService s=service();Runner r=new Runner();MiniAssistant m=new MiniAssistant(s,r);
    try{
      m.preview();StringBuilder text=new StringBuilder();for(int i=0;i<45;i++)text.append("Строка ").append(i).append(": описание сериала и его сезонов.\n");
      m.answer(text.toString());m.finished();idle();MiniCard c=ReflectionHelpers.getField(m,"card");
      assertEquals(720,c.getLayoutParams().width);
      c.measure(View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(540,View.MeasureSpec.AT_MOST));c.layout(0,0,720,c.getMeasuredHeight());
      assertTrue(c.getMeasuredHeight()<=351);assertTrue(c.canScrollReply());assertEquals(text.toString(),c.reply.getText().toString());
      assertTrue(m.handleReadKey(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN)));assertTrue(c.replyScrollY()>0);
      assertTrue(m.handleReadKey(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_DOWN)));
      assertTrue(m.handleReadKey(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP)));assertEquals(0,c.replyScrollY());
      assertTrue(m.handleReadKey(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_UP)));
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(13));assertTrue(m.report().getBoolean("visible"));
      Bitmap image=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(0xFF252E35);canvas.save();canvas.translate(120,540-24-c.getMeasuredHeight());c.draw(canvas);canvas.restore();
      File dir=new File("../../reports/mini-render");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"mini-long-answer.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
      assertTrue(m.handleReadKey(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK)));assertFalse(m.report().getBoolean("visible"));
      assertTrue(m.handleReadKey(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK)));assertFalse(m.handleReadKey(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN)));
    }finally{m.close();s.onDestroy();}
  }
  @Test public void shortAnswerLeavesRemoteArrowsForTheApplicationAndHomeStillWorks()throws Exception{
    NavigationService s=service();MiniAssistant m=new MiniAssistant(s,new Runner());
    try{
      m.preview();m.answer("Готово");idle();assertFalse(m.handleReadKey(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN)));
      assertFalse(m.handleReadKey(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_HOME)));assertFalse(m.report().getBoolean("visible"));
      s.getSharedPreferences("assistant",0).edit().putBoolean("speech",false).commit();m.close();
      m=new MiniAssistant(s,new Runner());assertFalse(s.getSharedPreferences("assistant",0).getBoolean("speech",true));
    }finally{m.close();s.onDestroy();}
  }
}
