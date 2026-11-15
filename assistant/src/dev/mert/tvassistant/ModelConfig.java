package dev.mert.tvassistant;

import android.content.Context;
import org.json.*;

/** Credentials stay in the TV's encrypted Android-keystore vault, never in the installer. */
final class ModelConfig {
  static final String[] PROVIDERS = {"xkiro", "openrouter", "pollinations", "cloudflare", "qwencloud", "modelstudio"};
  static final String[] OPENROUTER_MODELS = {"qwen/qwen3.5-9b", "openai/gpt-oss-20b", "openrouter/free"};
  static final String[] XKIRO_MODELS = {"mistralai/ministral-8b", "mistralai/ministral-3b", "mistralai/mistral-small-2603"};
  private final Vault vault;
  private JSONObject saved;

  ModelConfig(Context context) throws Exception {
    vault = new Vault(context, "qwen_vault");
    saved = vault.load();
  }
  static String label(String provider) {
    switch (provider) {
      case "xkiro": return "xKiro · бесплатные модели";
      case "openrouter": return "OpenRouter · недорогие и бесплатные модели";
      case "pollinations": return "Pollinations Classic · бесплатно без ключа";
      case "cloudflare": return "Cloudflare · бесплатная дневная квота";
      case "modelstudio": return "Alibaba Model Studio";
      case "qwencloud": return "QwenCloud · официальный API";
      default: throw new IllegalArgumentException("Неизвестный сервис ИИ");
    }
  }
  static String endpoint(String provider) {
    switch (provider) {
      case "xkiro": return "https://api.xkiro.com/v1";
      case "openrouter": return "https://openrouter.ai/api/v1";
      case "pollinations": return "https://text.pollinations.ai";
      case "cloudflare": throw new IllegalArgumentException("Для Cloudflare нужен ID аккаунта");
      case "modelstudio": return "https://dashscope-intl.aliyuncs.com/compatible-mode/v1";
      case "qwencloud": return "https://maas.qwencloudapi.com/compatible-mode/v1";
      default: throw new IllegalArgumentException("Неизвестный сервис ИИ");
    }
  }
  static String defaultModel(String provider) {
    switch (provider) {
      case "xkiro": return XKIRO_MODELS[0];
      case "openrouter": return OPENROUTER_MODELS[0];
      case "pollinations": return "openai-fast";
      case "cloudflare": return "@cf/ibm-granite/granite-4.0-h-micro";
      case "modelstudio": return "qwen-plus";
      case "qwencloud": return "qwen3.8-max";
      default: throw new IllegalArgumentException("Неизвестный сервис ИИ");
    }
  }
  static void validate(String provider, String model, String key) {
    validate(provider, model, key, "");
  }
  static boolean needsKey(String provider) { return !provider.equals("pollinations"); }
  static String endpoint(String provider, String account) {
    if (!provider.equals("cloudflare")) return endpoint(provider);
    if (!account.matches("[a-fA-F0-9]{32}")) throw new IllegalArgumentException("Cloudflare Account ID: 32 шестнадцатеричных символа");
    return "https://api.cloudflare.com/client/v4/accounts/" + account + "/ai/v1";
  }
  static void validate(String provider, String model, String key, String account) {
    endpoint(provider, account);
    if (provider.equals("pollinations")) {
      if (!model.equals("openai-fast") || !key.isEmpty())
        throw new IllegalArgumentException("Для бесплатного Pollinations используется openai-fast без ключа");
      return;
    }
    if (provider.equals("openrouter")) {
      if (!java.util.Arrays.asList(OPENROUTER_MODELS).contains(model))
        throw new IllegalArgumentException("Выберите Qwen 3.5 9B, GPT-OSS 20B или бесплатный пул OpenRouter");
    } else if (provider.equals("xkiro")) {
      if (!java.util.Arrays.asList(XKIRO_MODELS).contains(model))
        throw new IllegalArgumentException("Выберите бесплатную модель xKiro из списка");
      if (!key.matches("sk-xt-[A-Za-z0-9_-]{12,506}"))
        throw new IllegalArgumentException("Для xKiro нужен отдельный API-ключ sk-xt-…");
    } else if (provider.equals("cloudflare")) {
      if (!model.equals("@cf/ibm-granite/granite-4.0-h-micro"))
        throw new IllegalArgumentException("Выберите модель Cloudflare: @cf/ibm-granite/granite-4.0-h-micro");
    } else {
    if (!model.matches("[A-Za-z0-9][A-Za-z0-9_./:-]{0,159}")
        || !model.toLowerCase(java.util.Locale.ROOT).contains("qwen") || model.contains(".."))
      throw new IllegalArgumentException("Укажите точное имя модели Qwen из кабинета сервиса");
    }
    if (key.length() < 12 || key.length() > 512 || !key.matches("[!-~]+"))
      throw new IllegalArgumentException("Проверьте API-ключ: без пробелов и переносов строк");
  }
  synchronized boolean configured() {
    // OpenRouter is selected by default, but needs the user's own key before inference.
    return saved.optBoolean("enabled", true) && (!needsKey(provider()) || !saved.optString("key").isEmpty());
  }
  synchronized String provider() { return saved.optString("provider", "openrouter"); }
  synchronized String model() { return saved.optString("model", defaultModel(provider())); }
  synchronized String account() { return saved.optString("account", ""); }
  synchronized String endpoint() { return endpoint(provider(), account()); }
  synchronized String completionEndpoint() { return endpoint() + (provider().equals("pollinations") ? "/openai" : "/chat/completions"); }
  synchronized boolean lightweight() { return provider().equals("xkiro") || provider().equals("openrouter") || provider().equals("pollinations") || provider().equals("cloudflare"); }
  synchronized String token() throws Exception {
    if (!configured()) throw new IllegalStateException("Выберите сервис в меню «Настройки → ИИ»");
    if (!needsKey(provider())) return null;
    validate(provider(), model(), saved.getString("key"), account());
    return saved.getString("key");
  }
  synchronized boolean verified() { return configured() && saved.optLong("verified_at") > 0; }
  synchronized void configure(String provider, String model, String enteredKey) throws Exception {
    configure(provider, model, enteredKey, "");
  }
  synchronized void configure(String provider, String model, String enteredKey, String account) throws Exception {
    String key = enteredKey.trim();
    if (key.isEmpty() && provider.equals(provider())) key = saved.optString("key");
    validate(provider, model.trim(), key, account.trim());
    JSONObject next = Json.obj("provider", provider, "model", model.trim(), "key", key, "account", account.trim(), "enabled", true);
    vault.save(next);
    saved = next;
  }
  synchronized void markVerified() throws Exception {
    if (saved.optLong("verified_at") > 0) return;
    saved.put("verified_at", System.currentTimeMillis());
    vault.save(saved);
  }
  synchronized void disconnect() throws Exception {
    JSONObject next = Json.obj("provider", "openrouter", "enabled", false);
    vault.save(next);
    saved = next;
  }
}
