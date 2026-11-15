package dev.mert.tvassistant;

import android.content.*;
import android.media.AudioManager;
import android.os.*;
import android.speech.tts.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE)
public class AnswerSpeechAndroidTest {
  Context context(){Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("assistant",0).edit().putBoolean("speech",true).commit();return c;}
  void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
  ShadowTextToSpeech init(boolean russian){
    if(russian){ShadowTextToSpeech.addLanguageAvailability(new Locale("ru","RU"));ShadowTextToSpeech.addVoice(new Voice("ru-offline",new Locale("ru","RU"),Voice.QUALITY_HIGH,Voice.LATENCY_NORMAL,false,new HashSet<>()));}
    ShadowTextToSpeech shadow=Shadows.shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance());shadow.getOnInitListener().onInit(TextToSpeech.SUCCESS);idle();return shadow;
  }
  @Test public void queuedRussianAnswerUsesMediaAudioWithoutChangingVolume()throws Exception{
    Context c=context();AudioManager audio=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);int before=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
    AnswerSpeech voice=new AnswerSpeech(c,()->{});
    try{
      voice.say("Нашёл результаты в Wink.");ShadowTextToSpeech t=init(true);
      assertEquals("ru",t.getCurrentLanguage().getLanguage());assertEquals("ru-offline",t.getCurrentVoice().getName());
      assertEquals("Нашёл результаты в Wink.",t.getLastSpokenText());assertTrue(voice.report().getBoolean("ready"));
      assertEquals(before,audio.getStreamVolume(AudioManager.STREAM_MUSIC));assertNotNull(Shadows.shadowOf(audio).getLastAudioFocusRequest());
      voice.stop();assertTrue(t.isStopped());assertFalse(voice.busy());assertNotNull(Shadows.shadowOf(audio).getLastAbandonedAudioFocusListener());
    }finally{voice.close();}
  }
  @Test public void missingRussianVoiceNeverFallsBackToReadingRussianWithEnglishVoice()throws Exception{
    AnswerSpeech voice=new AnswerSpeech(context(),()->{});
    try{voice.say("Голосовой помощник готов.");ShadowTextToSpeech t=init(false);
      assertFalse(voice.report().getBoolean("ready"));assertNull(t.getLastSpokenText());assertEquals("russian_voice_missing",voice.report().getString("outcome"));assertFalse(voice.busy());
    }finally{voice.close();}
  }
  @Test public void longAnswersAreQueuedInFullAndStopDropsPendingSpeech()throws Exception{
    AnswerSpeech voice=new AnswerSpeech(context(),()->{});
    try{
      ShadowTextToSpeech t=init(true);StringBuilder answer=new StringBuilder();for(int i=0;i<700;i++)answer.append("Сериал найден. ");
      voice.say(answer.toString());int count=t.getSpokenTextList().size();assertTrue(count>1);assertEquals(TextToSpeech.QUEUE_ADD,t.getQueueMode());
      StringBuilder joined=new StringBuilder();for(String part:t.getSpokenTextList())joined.append(part).append(' ');
      assertEquals(answer.toString().replaceAll("\\s+"," ").trim(),joined.toString().replaceAll("\\s+"," ").trim());
      voice.stop();assertFalse(voice.busy());voice.close();t.getOnInitListener().onInit(TextToSpeech.SUCCESS);idle();assertEquals(count,t.getSpokenTextList().size());
    }finally{voice.close();}
  }
  @Test public void turningSpeechOffCancelsAndDoesNotSilentlyEnableItAgain()throws Exception{
    Context c=context();AnswerSpeech voice=new AnswerSpeech(c,()->{});
    try{ShadowTextToSpeech t=init(true);voice.say("Первый ответ");int count=t.getSpokenTextList().size();
      c.getSharedPreferences("assistant",0).edit().putBoolean("speech",false).commit();idle();voice.say("Второй ответ");
      assertTrue(t.isStopped());assertEquals(count,t.getSpokenTextList().size());assertEquals("disabled",voice.report().getString("outcome"));
    }finally{voice.close();}
  }
  @Test public void chunkBoundariesPreserveSurrogatePairsAndDoNotDropText(){
    String input="АБ😀ВГ😀ДЕ";List<String> chunks=AnswerSpeech.chunks(input,5);StringBuilder joined=new StringBuilder();
    for(String s:chunks){assertTrue(s.length()<=5);assertFalse(Character.isHighSurrogate(s.charAt(s.length()-1)));assertFalse(Character.isLowSurrogate(s.charAt(0)));joined.append(s);}
    assertEquals(input,joined.toString());
  }
  @Test public void failedInitializationCanBeRetriedByANewAnswerWithoutReplayingOldSpeech()throws Exception{
    AnswerSpeech voice=new AnswerSpeech(context(),()->{});
    try{
      voice.say("Не повторять старую фразу");ShadowTextToSpeech old=Shadows.shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance());
      old.getOnInitListener().onInit(TextToSpeech.ERROR);idle();assertFalse(voice.busy());assertFalse(voice.report().getBoolean("ready"));
      voice.say("Новая фраза");ShadowTextToSpeech fresh=init(true);assertNotSame(old,fresh);assertEquals("Новая фраза",fresh.getLastSpokenText());
      int count=fresh.getSpokenTextList().size();old.getOnInitListener().onInit(TextToSpeech.SUCCESS);idle();assertEquals(count,fresh.getSpokenTextList().size());
    }finally{voice.close();}
  }
}
