package dev.mert.tvassistant;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

/** DUMP-protected fixed setup actions. No UI, text commands, credentials or arbitrary keycodes. */
public final class MiniControlActivity extends Activity {
  static final String LEARN="dev.mert.tvassistant.LEARN_MI";
  static final String PREVIEW="dev.mert.tvassistant.PREVIEW_MINI";
  static final String SPEECH="dev.mert.tvassistant.TEST_MINI_SPEECH";
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    try {
      NavigationService n=NavigationService.instance;
      if(n==null)Toast.makeText(this,"Служба помощника ещё не подключена. Повтори через панель.",Toast.LENGTH_LONG).show();
      else if(LEARN.equals(getIntent().getAction()))n.learnMi();
      else if(PREVIEW.equals(getIntent().getAction()))n.previewMini();
      else if(SPEECH.equals(getIntent().getAction()))n.testMiniSpeech();
    } finally {finish();}
  }
}
