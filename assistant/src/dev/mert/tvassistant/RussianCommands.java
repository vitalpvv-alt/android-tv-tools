package dev.mert.tvassistant;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small offline command vocabulary; unknown requests stay unchanged for the normal AI path. */
final class RussianCommands {
  static String normalize(String input) {
    String raw = input.trim().replaceAll("[.!?]+$", "");
    String s = raw.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("\\s+", " ");
    s = s.replaceFirst("^(?:пожалуйста|давай)[, ]+", "");
    s = s.replaceFirst("^(открой|запусти|включи|сделай|убавь|прибавь)(?:[, ]+(?:мне|нам|пожалуйста))+(?:[, ]+)", "$1 ");
    if (s.matches("(?:сделай |сделайте |)?(?:потише|тише)|(?:уменьши|убавь) (?:звук|громкость)")) return "volume down";
    if (s.matches("(?:сделай |сделайте |)?(?:погромче|громче)|(?:увеличь|прибавь) (?:звук|громкость)")) return "volume up";
    if (s.matches("выключи звук|без звука|отключи звук")) return "mute";
    if (s.matches("включи звук|верни звук")) return "unmute";
    if (s.matches("пауза|поставь на паузу|останови видео")) return "pause";
    if (s.matches("продолжи|продолжить|продолжи просмотр|продолжи видео")) return "resume";
    if (s.matches("назад|вернись назад")) return "go back";
    if (s.matches("домой|на главный экран|открой главный экран")) return "go home";
    if (s.matches("(?:нажми )?(?:ок|okay|ok)|выбери")) return "select";
    if (s.matches("(?:нажми |иди |)?вверх")) return "go up";
    if (s.matches("(?:нажми |иди |)?вниз")) return "go down";
    if (s.matches("(?:нажми |иди |)?влево")) return "go left";
    if (s.matches("(?:нажми |иди |)?вправо")) return "go right";
    if (s.matches("открой настройки|покажи настройки")) return "open settings";
    if (s.matches("покажи приложения|список приложений")) return "show apps";
    if (s.matches("сколько времени|который час")) return "time";
    if (s.matches("(?:покажи )?информаци(?:ю|я) (?:о телевизоре|об устройстве)|что за телевизор")) return "show device info";
    Matcher volume = Pattern.compile("(?:установи |сделай |)?(?:громкость|звук)(?: на)? (\\d{1,3})(?: ?%| процентов| процента| процент)?").matcher(s);
    if (volume.matches()) return "volume " + volume.group(1) + " percent";
    Matcher open = Pattern.compile("(?:открой|запусти|включи)(?: приложение)? (.+)").matcher(s);
    if (open.matches()) {
      String target = open.group(1);
      if (target.matches("(?:с )?(?:ютуб|ютюб|ютубом|ютюбом|youtube|you tube)")) target = "com.google.android.youtube.tv";
      else if (target.matches("(?:с )?(?:винк|винком|wink)")) target = "ru.rt.video.app.tv";
      return "open " + target;
    }
    return raw;
  }
}
