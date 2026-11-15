package dev.mert.tvassistant;

import java.util.LinkedHashMap;
import java.util.Map;

/** Russian presentation only: tool identifiers, saved data and account details stay untouched. */
final class RuUi {
  private static final Map<String, String> LABELS = new LinkedHashMap<>();
  private static final Map<String, String> TOOL_INFO = new LinkedHashMap<>();
  private static final Map<String, String> PREFIXES = new LinkedHashMap<>();
  static {
    label("web_page", "Веб-страница"); TOOL_INFO.put("web_page", "Открыть и прочитать общедоступную страницу, нажать ссылку или заполнить поле. Не использует аккаунты системного браузера.");
    label("action_plan", "Последовательность действий"); TOOL_INFO.put("action_plan", "Выполнить несколько действий подряд с проверкой результата каждого шага.");
    label("ui_target", "Элемент на экране"); TOOL_INFO.put("ui_target", "Найти видимую кнопку или поле по надписи, нажать, ввести текст либо дождаться появления.");
    label("workflow", "Сохранённые действия"); TOOL_INFO.put("workflow", "Повторить ранее проверенную последовательность действий, сверяя экран на каждом шаге.");
    label("list_apps", "Список приложений"); TOOL_INFO.put("list_apps", "Показать установленные приложения, которые можно запустить.");
    label("open_app", "Запуск приложения"); TOOL_INFO.put("open_app", "Открыть установленное приложение. Сам запуск ещё не подтверждает загрузку содержимого.");
    label("search_app", "Поиск в приложении"); TOOL_INFO.put("search_app", "Отправить поисковый запрос в совместимое установленное приложение.");
    label("search_tv", "Поиск фильма или сериала"); TOOL_INFO.put("search_tv", "Найти фильм или сериал в установленном приложении телевизора. С главного экрана используется Wink, если он установлен.");
    label("open_url", "Открытие ссылки"); TOOL_INFO.put("open_url", "Открыть HTTPS-ссылку в браузере телевизора.");
    label("youtube_search", "Поиск в YouTube"); TOOL_INFO.put("youtube_search", "Найти открытые каналы и видео YouTube.");
    label("youtube_latest", "Последнее видео канала"); TOOL_INFO.put("youtube_latest", "Найти новейшее опубликованное видео выбранного YouTube-канала.");
    label("youtube_play", "Запуск видео YouTube"); TOOL_INFO.put("youtube_play", "Открыть найденное видео и по возможности проверить воспроизведение.");
    label("web_search", "Поиск в интернете"); TOOL_INFO.put("web_search", "Открыть поиск в выбранном браузере.");
    label("find_media", "Поиск фильма или сериала"); TOOL_INFO.put("find_media", "Найти сведения о фильме или сериале в открытом каталоге Cinemeta.");
    label("media_details", "Карточка фильма или серии"); TOOL_INFO.put("media_details", "Открыть карточку в Stremio. Выбор источника видео выполняется отдельно.");
    label("list_sessions", "Активные плееры"); TOOL_INFO.put("list_sessions", "Показать плееры, доступные для управления. Нужен доступ к уведомлениям.");
    label("playback", "Воспроизведение"); TOOL_INFO.put("playback", "Пауза, продолжение и перемотка в совместимом активном плеере.");
    label("volume", "Громкость"); TOOL_INFO.put("volume", "Изменить громкость или отключить звук, если телевизор допускает программное управление.");
    label("open_settings", "Настройки Android"); TOOL_INFO.put("open_settings", "Открыть нужный раздел настроек телевизора.");
    label("device_info", "Сведения о телевизоре"); TOOL_INFO.put("device_info", "Показать модель, версию Android, состояние сети и управления экраном.");
    label("clock", "Время"); TOOL_INFO.put("clock", "Показать текущее время телевизора.");
    label("calculate", "Калькулятор"); TOOL_INFO.put("calculate", "Вычислить арифметическое выражение.");
    label("weather", "Погода"); TOOL_INFO.put("weather", "Получить текущую погоду и прогноз из Open-Meteo.");
    label("notes", "Заметки"); TOOL_INFO.put("notes", "Прочитать, сохранить или удалить заметку на телевизоре.");
    label("preferences", "Предпочтения"); TOOL_INFO.put("preferences", "Сохранить предпочтения для следующих команд.");
    label("routines", "Сценарии"); TOOL_INFO.put("routines", "Сохранить и запустить последовательность действий.");
    label("timer", "Таймер"); TOOL_INFO.put("timer", "Установить или отменить таймер. Для срабатывания приложение должно продолжать работать.");
    label("screen_read", "Чтение экрана"); TOOL_INFO.put("screen_read", "Прочитать видимые надписи через службу специальных возможностей. Поля паролей исключены.");
    label("screen_see", "Снимок экрана для ИИ"); TOOL_INFO.put("screen_see", "Показать модели снимок экрана во время задачи. Требуется отдельное разрешение Android.");
    label("keyboard_keys", "Кнопки клавиатуры"); TOOL_INFO.put("keyboard_keys", "Передать разрешённые нажатия клавиш.");
    label("screen_tap", "Нажатие по координатам"); TOOL_INFO.put("screen_tap", "Нажать в выбранной точке по актуальному снимку экрана.");
    label("screen_swipe", "Жест по экрану"); TOOL_INFO.put("screen_swipe", "Выполнить жест через службу управления экраном.");
    label("screen_click", "Нажатие элемента"); TOOL_INFO.put("screen_click", "Нажать найденный элемент интерфейса.");
    label("screen_type", "Ввод текста"); TOOL_INFO.put("screen_type", "Ввести текст в видимое поле. Поля паролей исключены.");
    label("screen_scroll", "Прокрутка экрана"); TOOL_INFO.put("screen_scroll", "Прокрутить доступную область интерфейса.");
    label("navigate", "Кнопки пульта"); TOOL_INFO.put("navigate", "Вверх, вниз, влево, вправо, ОК, назад и главный экран.");
    label("browser_read", "Чтение браузера"); TOOL_INFO.put("browser_read", "Прочитать открытую страницу встроенного браузера.");
    label("browser_action", "Действие в браузере"); TOOL_INFO.put("browser_action", "Нажать элемент или ввести текст во встроенном браузере.");
    label("browser_navigation", "Навигация браузера"); TOOL_INFO.put("browser_navigation", "Назад, вперёд, обновление и прокрутка страницы.");
    label("wait", "Ожидание"); TOOL_INFO.put("wait", "Подождать загрузку экрана.");
    label("speak", "Озвучивание"); TOOL_INFO.put("speak", "Прочитать ответ голосом, если озвучивание включено и русский голос доступен.");
    label("TV Assistant", "ТВ-помощник");
    label("Chat", "Чат"); label("History", "История"); label("Tools", "Возможности");
    label("Apps", "Приложения"); label("Settings", "Настройки");
    label("Speak", "Сказать"); label("Busy", "Занят"); label("Run", "Выполнить");
    label("Stop", "Стоп"); label("Ready", "Готов");
    label("Say or type a command", "Скажите или введите команду");
    label("Press Run, or the remote mic, to continue", "Нажмите «Выполнить» или микрофон на пульте");
    label("What would you like to do?", "Что сделать?");
    label("YOU ASKED", "ВАША КОМАНДА"); label("ASSISTANT", "ПОМОЩНИК");
    label("Press OK to collapse", "Нажмите ОК, чтобы свернуть");
    label("Yes, continue", "Да, продолжить"); label("Cancel", "Отмена");
    label("ChatGPT account", "Аккаунт ChatGPT"); label("Connected", "Подключён");
    label("Unavailable", "Недоступно"); label("Local only", "Без подключения");
    label("AI limits", "Лимиты ИИ"); label("4 requests · 10 steps", "4 запроса · 10 действий");
    label("Permissions", "Разрешения"); label("Navigation and playback", "Управление ТВ и видео");
    label("Diagnostics", "Диагностика"); label("Checks and microphone", "Проверки и микрофон");
    label("Close", "Закрыть");
    label("Connected to ChatGPT. Commands are interpreted by AI.", "ChatGPT подключён. Команды обрабатывает ИИ.");
    label("Not connected. Basic commands still work offline.", "ChatGPT не подключён. Основные команды работают без интернета.");
    label("Continue with ChatGPT", "Войти через ChatGPT");
    label("Add ChatGPT account", "Добавить аккаунт ChatGPT");
    label("Choose saved account", "Выбрать сохранённый аккаунт");
    label("Choose AI model", "Выбрать модель ИИ");
    label("Manage ChatGPT usage", "Использование ChatGPT"); label("Sign out", "Выйти из аккаунта");
    label("Sign in on this TV. Only Plus and Pro plans can power AI tasks.", "Войдите на этом телевизоре. Для задач ИИ приложение поддерживает подходящие тарифы Plus и Pro.");
    label("AI request limit", "Число запросов к ИИ"); label("Tool step limit", "Число действий");
    label("Maximum AI requests per command", "Максимум запросов к ИИ на одну команду");
    label("Maximum tool steps per command", "Максимум действий на одну команду");
    label("Local-only mode", "Только локальные команды"); label("Spoken answers", "Озвучивать ответы");
    label("Lower limits finish sooner. The last AI request answers the request.", "Меньше запросов — быстрее выполнение. Последний запрос формирует ответ.");
    label("Screen navigation", "Управление экраном"); label("Off", "Выкл."); label("On", "Вкл.");
    label("Playback control", "Управление воспроизведением"); label("Notification access", "Доступ к уведомлениям");
    label("Screen vision", "Просмотр экрана ИИ");
    label("Alexa button opens TV Assistant", "Кнопка микрофона открывает помощник");
    label("Voice button opens TV Assistant", "Кнопка микрофона открывает помощник");
    label("These are optional. Enable them from Android settings when asked.", "Включайте нужные разрешения в настройках Android.");
    label("Run diagnostics", "Проверить телевизор"); label("Microphone test", "Проверка микрофона");
    label("8 seconds, local only", "8 секунд, без отправки звука");
    label("Microphone test source", "Источник микрофона для проверки");
    label("Bluetooth SCO for microphone test", "Bluetooth SCO при проверке микрофона");
    label("Clear local history", "Очистить историю"); label("Saved commands", "Сохранённые команды");
    label("Checks run on this TV. The microphone test never records or uploads audio.", "Проверки выполняются на телевизоре. Звук микрофона не сохраняется и не отправляется.");
    label("Sign out?", "Выйти из аккаунта?");
    label("Removes the saved ChatGPT connection from this TV.", "Подключение ChatGPT будет удалено с этого телевизора.");
    label("Clear local history?", "Очистить историю?");
    label("Removes saved commands and answers on this TV.", "Сохранённые команды и ответы будут удалены с телевизора.");
    label("This optional service can read visible app labels and click or type when you give a command. During AI tasks, relevant screen text goes to your selected model. Password fields are excluded. Enable TV Assistant navigation in the next screen.", "Сервис читает видимые надписи, нажимает кнопки и вводит текст по вашей команде. При работе ИИ нужный текст экрана отправляется выбранной модели. Поля паролей исключены. На следующем экране включите «ТВ-помощник: управление экраном».");
    label("This optional permission gives TV Assistant access to active media sessions through Android notification access. Enable TV Assistant playback in the next screen. Supported players can then be paused, resumed or seeked.", "Доступ к уведомлениям позволяет управлять активными плеерами. На следующем экране включите «ТВ-помощник: воспроизведение». В совместимых плеерах станут доступны пауза, продолжение и перемотка.");
    label("Screen vision stopped", "Просмотр экрана ИИ остановлен");
    label("Enable screen vision?", "Разрешить ИИ просматривать экран?");
    label("Allows AI to see screenshots of the TV during your tasks, including visible account or page details. Images stay in memory and are sent to your chosen model only when requested. You can stop this session in Settings. Android will ask for screen-capture consent next.", "ИИ сможет получать снимки экрана при выполнении ваших задач, включая видимые сведения страниц и аккаунтов. Снимки хранятся в памяти и отправляются выбранной модели по запросу. Остановить сеанс можно в настройках. Далее Android запросит разрешение на захват экрана.");
    label("Saved ChatGPT connections", "Сохранённые аккаунты ChatGPT");
    label("Choose which account this TV should use.", "Выберите аккаунт для этого телевизора.");
    label("Connect ChatGPT and grant plan usage first.", "Сначала подключите ChatGPT и разрешите использование тарифа.");
    label("Loading eligible models…", "Загружаю доступные модели…");
    label("The newest eligible model is usually the best choice.", "Выберите доступную модель для обработки команд.");
    label("No browser is available", "На телевизоре нет подходящего браузера");
    label("What the assistant can do. AI tasks are limited to the tools that match your request.", "Возможности помощника. ИИ получает только инструменты, подходящие для вашей команды.");
    label("Installed apps", "Установленные приложения"); label("Choose an app to open.", "Выберите приложение для запуска.");
    label("Run again", "Повторить"); label("Saved answer", "Сохранённый ответ");
    label("Clear conversation", "Очистить диалог"); label("Only this screen", "Только текущий экран");
    label("Recent commands", "Последние команды"); label("Nothing saved yet.", "Пока ничего не сохранено.");
    label("Choose a command to see its answer.", "Выберите команду, чтобы увидеть ответ.");
    label("Finish the current task before diagnostics", "Дождитесь завершения задачи перед диагностикой");
    label("Checking local capabilities…", "Проверяю возможности телевизора…");
    label("Diagnostics complete", "Проверка завершена"); label("Open settings", "Открыть настройки");
    label("Later", "Позже");
    label("Secure account storage could not initialize on this device.", "Не удалось открыть защищённое хранилище аккаунтов на этом устройстве.");
    label("Finish or stop the current task before changing accounts", "Завершите или остановите задачу перед сменой аккаунта");
    label("Opening ChatGPT sign-in…", "Открываю вход в ChatGPT…");
    label("No system browser is installed", "На телевизоре не установлен браузер");
    label("Return to TV Assistant after sign-in", "После входа вернитесь в ТВ-помощник");
    label("Connect ChatGPT", "Подключить ChatGPT");
    label("Sign in using the browser on this TV. Eligible Plus/Pro plans can power AI tasks. You choose whether to grant plan usage. Plus usage shares your five-hour allowance across participating apps. Basic commands remain available without sign-in.", "Войдите через браузер на этом телевизоре. Приложение использует подходящие тарифы Plus/Pro с вашего разрешения. Для Plus расходуется общий лимит за пять часов. Основные команды доступны без входа.");
    label("Microphone test stopped", "Проверка микрофона остановлена");
    label("Stop the running task before testing the microphone", "Остановите задачу перед проверкой микрофона");
    label("Local microphone test", "Проверка микрофона без отправки звука");
    label("ChatGPT plan-sharing does not include OpenAI transcription. This test checks raw microphone access for 8 seconds. Only levels are measured; audio is not saved or sent anywhere. Remote microphone access is unverified. Holding Alexa may open its system UI and interrupt the test. The test stops when you leave the app. Bluetooth SCO is optional in Diagnostics and may temporarily change audio routing.", "Проверка в течение 8 секунд измеряет только уровень звука. Запись не сохраняется и никуда не отправляется. Доступ к микрофону пульта ещё нужно проверить. Кнопка штатного помощника может открыть системное окно и прервать проверку. При выходе из приложения проверка останавливается. Bluetooth SCO включается отдельно и может временно изменить вывод звука.");
    label("Mic test", "Микрофон"); label("Starting local microphone test…", "Начинаю проверку микрофона…");
    label("Microphone permission was denied; keyboard dictation remains available", "Нет разрешения на микрофон; можно использовать диктовку экранной клавиатуры");
    label("Local microphone test complete — no transcription attempted", "Проверка микрофона завершена. Распознавание речи не запускалось");
    label("Microphone test unavailable", "Проверка микрофона недоступна");
    label("Microphone test stopped when the app lost focus", "Проверка остановлена при выходе из приложения");
    label("Say or type a command first", "Сначала скажите или введите команду");
    label("Stop the microphone test before running a command", "Остановите проверку микрофона перед выполнением команды");
    label("A task is running. Use Stop before starting another.", "Задача уже выполняется. Нажмите «Стоп» перед новой командой.");
    label("Allow", "Разрешить"); label("Confirm action", "Подтвердить действие"); label("Task timing", "Время выполнения");
    label("Speech output is disabled or no TTS engine is available.", "Озвучивание выключено или русский голос недоступен.");
    label("Local commands", "Локальные команды");
    label("Screen vision was not enabled", "Просмотр экрана ИИ не включён");
    label("Screen vision enabled. AI can now inspect the TV during your tasks.", "Просмотр экрана включён. ИИ сможет проверять экран при выполнении задач.");
    label("Opened the destination. Check the TV screen.", "Команда открытия отправлена. Проверьте экран телевизора.");
    label("Action performed.", "Действие выполнено.");
    label("The app did not accept that action.", "Приложение не приняло команду.");
    label("No active media session is available.", "Нет активного плеера.");
    label("No items found.", "Ничего не найдено."); label("Weather is unavailable.", "Погода недоступна.");
    label("Done.", "Готово."); label("Note updated.", "Заметка обновлена.");
    label("Preference saved.", "Настройка сохранена."); label("Routine updated.", "Сценарий обновлён.");
    label("Timer cancelled.", "Таймер отменён."); label("Waited for the screen to load.", "Ожидание загрузки экрана завершено.");
    label("This device has fixed software volume. Use the TV/receiver remote.", "Программная громкость на этом устройстве фиксирована. Используйте пульт телевизора или ресивера.");
    label("Playback could not be verified; check the YouTube screen.", "Воспроизведение не подтверждено. Проверьте экран YouTube.");
    label("No public uploads found.", "Открытых видео не найдено.");
    label("Active players", "Активные плееры"); label("Visible screen items", "Элементы на экране");
    label("Notes", "Заметки"); label("Saved preferences", "Сохранённые настройки");
    label("Saved routines", "Сохранённые сценарии"); label("Running timers", "Активные таймеры");
    label("Matching titles", "Найденные фильмы и сериалы");
    label("Matching videos", "Найденные видео"); label("Matching YouTube channels", "Найденные каналы YouTube");
    label("Network: connected", "Сеть: подключена"); label("Network: offline", "Сеть: нет подключения");
    label("Screen navigation: enabled", "Управление экраном: включено");
    label("Screen navigation: not connected", "Управление экраном: не подключено");
    label("Takes effect on the next command.", "Применится к следующей команде.");
    prefix("Limit set to ", "Лимит: ");
    prefix("Version ", "Версия "); prefix("Steps ", "Действия "); prefix("AI ", "ИИ ");
    prefix("Selected ", "Выбрано: "); prefix("Working on: ", "Выполняю: ");
    prefix("Screen capture is unavailable on this device: ", "Захват экрана недоступен: ");
    prefix("Screen vision could not start. ", "Не удалось включить просмотр экрана. ");
    prefix("Takes effect on the next command. Limit set to ", "Применится к следующей команде. Лимит: ");
    prefix("FAIL ", "ОШИБКА "); prefix("PASS ", "ОК ");
    prefix("Result: ", "Результат: "); prefix("Playing: ", "Воспроизводится: ");
    prefix("Requested video: ", "Запрошено видео: "); prefix("Media volume: ", "Громкость: ");
    prefix("Latest upload: ", "Последнее видео: "); prefix("Published: ", "Опубликовано: ");
    prefix("Feels like ", "Ощущается как "); prefix("Captured current screen for AI · ", "Снимок экрана для ИИ · ");
  }
  private static void label(String en, String ru) { LABELS.put(en, ru); }
  private static void prefix(String en, String ru) { PREFIXES.put(en, ru); }

  static String toolDescription(String name, String fallback) {
    String description = TOOL_INFO.get(name);
    return description == null ? fallback : description;
  }

  static String text(String value) {
    if (value == null) return "";
    String exact = LABELS.get(value);
    if (exact != null) return exact;
    String toolName = value.replace(' ', '_');
    if (TOOL_INFO.containsKey(toolName)) return LABELS.get(toolName);
    if (value.indexOf('\n') >= 0) {
      String[] lines = value.split("\n", -1);
      for (int i = 0; i < lines.length; i++) lines[i] = text(lines[i]);
      return String.join("\n", lines);
    }
    for (Map.Entry<String, String> entry : PREFIXES.entrySet())
      if (value.startsWith(entry.getKey())) {
        String suffix = value.substring(entry.getKey().length());
        if (suffix.endsWith(" · muted")) suffix = suffix.substring(0, suffix.length() - 8) + " · без звука";
        return entry.getValue() + suffix;
      }
    return value;
  }
}
