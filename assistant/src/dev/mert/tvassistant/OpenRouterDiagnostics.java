package dev.mert.tvassistant;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import javax.net.ssl.SSLException;
import org.json.*;

/** Fixed, read-only metadata checks. Never returns response text, credentials or account balances. */
final class OpenRouterDiagnostics {
  interface Transport { HttpURLConnection open(String url, String token) throws Exception; }
  interface Cancelled { boolean get(); }
  static String fingerprint(String token) throws Exception {
    byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes("UTF-8"));
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < 6; i++) { text.append("0123456789abcdef".charAt((hash[i] >>> 4) & 15)); text.append("0123456789abcdef".charAt(hash[i] & 15)); }
    return text.toString();
  }
  static String prefix(InputStream in, int limit) throws Exception {
    if (in == null) return "";
    try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] bytes = new byte[1024]; int n;
      while (out.size() < limit && (n = input.read(bytes, 0, Math.min(bytes.length, limit-out.size()))) != -1) out.write(bytes,0,n);
      return new String(out.toByteArray(), "UTF-8");
    }
  }
  static String format(String contentType) {
    String value = contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT);
    return value.contains("json") ? "json" : value.contains("html") ? "html" : value.startsWith("text/") ? "text" : "other";
  }
  static String kind(int status, String contentType, String mitigated, String body) {
    String text = body == null ? "" : body.toLowerCase(java.util.Locale.ROOT);
    String message = "";
    try {
      JSONObject error = new JSONObject(body).optJSONObject("error");
      if (error != null) message = error.optString("message").toLowerCase(java.util.Locale.ROOT);
    } catch (Exception ignored) {}
    if ("challenge".equalsIgnoreCase(mitigated) || (format(contentType).equals("html") && (text.contains("cf-chl") || text.contains("challenge-platform") || text.contains("just a moment")))) return "browser_challenge";
    if (message.contains("unsupported_country") || message.contains("unsupported country") || message.contains("not available in your country") || message.contains("not available in your region")) return "region_restricted";
    if (status == 401) {
      if (message.contains("no api key") || message.contains("authorization header")) return "missing_authorization";
      if (message.contains("expired") || message.contains("disabled") || message.contains("revoked")) return "disabled_or_expired";
      return "unauthorized";
    }
    if (status == 403) return message.contains("moderation") || message.contains("guardrail") ? "moderation" : "forbidden";
    if (status == 402) return "quota";
    if (status == 429) return "rate_limit";
    if (status >= 300 && status < 400) return "redirect";
    return status == 200 && format(contentType).equals("json") ? "ok" : "unexpected_response";
  }
  static String hint(String kind) {
    switch(kind) {
      case "browser_challenge": return "Сервис вернул браузерную проверку вместо ответа API.";
      case "region_restricted": return "Сервис сообщил об ограничении страны или региона.";
      case "missing_authorization": return "Сервис сообщил, что не получил заголовок авторизации.";
      case "disabled_or_expired": return "Сервис сообщил об отключённом или истёкшем ключе.";
      case "moderation": return "Сервис сообщил об ограничении политики или модерации.";
      default: return "";
    }
  }
  static JSONObject probe(String stage, String token, Transport transport, Cancelled cancelled) throws Exception {
    return probe("openrouter",stage,token,transport,cancelled);
  }
  static JSONObject probe(String provider, String stage, String token, Transport transport, Cancelled cancelled) throws Exception {
    if (!provider.equals("openrouter") && !provider.equals("xkiro")) throw new IllegalArgumentException("Неизвестный сервис проверки");
    if (!stage.equals("catalog") && !stage.equals("key")) throw new IllegalArgumentException("Неизвестная проверка");
    long started = System.nanoTime(); HttpURLConnection c = null;
    try {
      if (cancelled.get()) return Json.obj("stage", stage, "http", 0, "format", "other", "kind", "cancelled", "recognized", false, "elapsed_ms", 0);
      c = transport.open(ModelConfig.endpoint(provider) + "/" + (stage.equals("catalog") ? "models" : provider.equals("xkiro") ? "usage" : "key"), stage.equals("key") ? token : null);
      c.setInstanceFollowRedirects(false); c.setConnectTimeout(8000); c.setReadTimeout(8000); c.setRequestMethod("GET"); c.setRequestProperty("Accept", "application/json");
      int status = c.getResponseCode(); String contentType = c.getContentType();
      String body = status == 200 && stage.equals("catalog") ? "" : prefix(status < 400 ? c.getInputStream() : c.getErrorStream(), 16384);
      String category = kind(status, contentType, c.getHeaderField("cf-mitigated"), body); boolean recognized = false;
      if (stage.equals("key") && status == 200 && category.equals("ok")) {
        try {
          JSONObject response = new JSONObject(body);
          recognized = provider.equals("xkiro") ? response.optString("object").equals("usage") && response.optJSONObject("free_tokens") != null : response.optJSONObject("data") != null;
        } catch(Exception ignored) {}
        if (!recognized) category = "unexpected_response";
      }
      if (cancelled.get()) category = "cancelled";
      return Json.obj("stage", stage, "http", status, "format", format(contentType), "kind", category,
          "recognized", recognized && !cancelled.get(), "elapsed_ms", (System.nanoTime()-started)/1000000);
    } catch(Exception e) {
      String category = e instanceof UnknownHostException ? "dns" : e instanceof SSLException ? "tls" : e instanceof java.net.SocketTimeoutException ? "timeout" : e instanceof ConnectException ? "connection" : "io";
      return Json.obj("stage", stage, "http", 0, "format", "other", "kind", category, "recognized", false, "elapsed_ms", (System.nanoTime()-started)/1000000);
    } finally { if (c != null) c.disconnect(); }
  }
  static JSONObject run(String provider, String model, String token, Transport transport, Cancelled cancelled) throws Exception {
    return runFor("openrouter", provider, model, token, transport, cancelled);
  }
  static JSONObject runFor(String target, String storedProvider, String model, String token, Transport transport, Cancelled cancelled) throws Exception {
    JSONArray probes = new JSONArray(); probes.put(probe(target,"catalog", null, transport, cancelled));
    boolean configured = storedProvider.equals(target) && token != null && !token.isEmpty();
    if (configured) { ModelConfig.validate(target, model, token); probes.put(probe(target,"key", token, transport, cancelled)); }
    return Json.obj("provider", target, "model", configured ? model : "", "configured", configured,
        "key_fingerprint", configured ? fingerprint(token) : "", "probes", probes);
  }
}
