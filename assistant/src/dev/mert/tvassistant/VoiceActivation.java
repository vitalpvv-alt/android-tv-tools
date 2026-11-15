package dev.mert.tvassistant;

import java.util.Locale;

/** Match Google's TV search window, never its settings, account UI or a keyboard. */
final class VoiceActivation {
  static final String GOOGLE_TV = "com.google.android.katniss";
  static boolean googleWindow(String pkg, String className) {
    if (!GOOGLE_TV.equals(pkg) || className == null || className.length() > 200) return false;
    String lower = className.toLowerCase(Locale.ROOT);
    if (lower.contains("setting") || lower.contains("permission") || lower.contains("account")
        || lower.contains("signin") || lower.contains("consent")) return false;
    return className.startsWith("com.google.android.apps.tvsearch.")
        || className.startsWith("com.google.android.katniss.")
        || className.equals("android.service.voice.VoiceInteractionSession");
  }
  static boolean intercept(String pkg, String cls, boolean enabled, boolean keyboard,
      boolean ownRecognition, boolean busy) {
    return enabled && !keyboard && !ownRecognition && !busy && googleWindow(pkg, cls);
  }
  static String componentText(CharSequence text) {
    String value = text == null ? "" : text.toString();
    return value.length() <= 200 && value.matches("[A-Za-z0-9_.$]*") ? value : "[unknown]";
  }
}
