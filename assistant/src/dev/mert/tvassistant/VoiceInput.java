package dev.mert.tvassistant;

import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.speech.RecognizerIntent;
import java.util.ArrayList;
import org.json.JSONObject;

/** User-initiated system speech dialog. Audio stays with the installed recognizer. */
final class VoiceInput {
  static final int REQUEST = 941;
  interface Listener {
    void recognized(String text);
    void status(String text);
  }
  private final Activity activity;
  private final Listener listener;
  private boolean active;
  private String outcome = "not_started";

  VoiceInput(Activity activity, Listener listener) {
    this.activity = activity; this.listener = listener;
  }
  static Intent request() {
    return new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажи команду для телевизора");
  }
  boolean active() { return active; }
  boolean start() {
    if (active) return false;
    Intent intent = request();
    if (activity.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
      outcome = "recognizer_missing";
      listener.status("На ТВ нет приложения распознавания речи. Команду можно ввести с клавиатуры.");
      return false;
    }
    active = true;
    try {
      activity.startActivityForResult(intent, REQUEST);
      outcome = "listening";
      listener.status("Скажи команду. Распознавание — на русском языке.");
      return true;
    } catch (RuntimeException e) {
      active = false; outcome = "recognizer_unavailable";
      listener.status("Распознавание речи не открылось. Команду можно ввести с клавиатуры.");
      return false;
    }
  }
  void result(int result, Intent data) {
    if (!active) return;
    active = false;
    if (result != Activity.RESULT_OK || data == null) {
      outcome = "cancelled"; listener.status("Голосовой ввод отменён."); return;
    }
    try {
      ArrayList<String> values = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
      String text = values == null || values.isEmpty() || values.get(0) == null ? "" : values.get(0).trim();
      if (text.isEmpty() || text.length() > 2000 || text.matches("(?s).*[\\p{Cntrl}].*")) {
        outcome = "empty_or_invalid";
        listener.status("Не удалось распознать команду. Нажми «Сказать» и повтори."); return;
      }
      outcome = "recognized";
      listener.recognized(text);
    } catch (RuntimeException e) {
      outcome = "invalid_result";
      listener.status("Распознаватель вернул неподходящий ответ. Повтори голосовой ввод.");
    }
  }
  JSONObject report() {
    return Json.obj("active", active, "outcome", outcome,
        "recognizer_available", activity.getPackageManager().resolveActivity(request(), PackageManager.MATCH_DEFAULT_ONLY) != null);
  }
  void close() { active = false; }
}
