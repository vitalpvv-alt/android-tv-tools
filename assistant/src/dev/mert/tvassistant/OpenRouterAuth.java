package dev.mert.tvassistant;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Official PKCE flow. Callback listens only on this TV's loopback address. */
final class OpenRouterAuth {
  static final String EXCHANGE = "https://openrouter.ai/api/v1/auth/keys";
  private final ModelConfig config;
  private ServerSocket server;
  private long generation;
  OpenRouterAuth(ModelConfig config) { this.config = config; }

  synchronized void cancel() {
    generation++;
    if (server != null) try { server.close(); } catch (Exception ignored) {}
    server = null;
  }
  private synchronized boolean current(long id) { return generation == id; }
  static String challenge(String verifier) throws Exception {
    byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes("US-ASCII"));
    String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    StringBuilder out = new StringBuilder(); int value = 0, bits = 0;
    for (byte b : hash) {
      value = (value << 8) | (b & 255); bits += 8;
      while (bits >= 6) { bits -= 6; out.append(alphabet.charAt((value >>> bits) & 63)); }
    }
    if (bits > 0) out.append(alphabet.charAt((value << (6 - bits)) & 63));
    return out.toString();
  }
  static String authorization(String redirect, String state, String verifier) throws Exception {
    Map<String, String> params = new LinkedHashMap<>();
    params.put("callback_url", redirect); params.put("state", state);
    params.put("key_label", "TV Assistant Xiaomi");
    params.put("code_challenge", challenge(verifier)); params.put("code_challenge_method", "S256");
    return "https://openrouter.ai/auth?" + Net.form(params);
  }
  static String callbackCode(String line, String state) throws Exception {
    if (line == null || line.length() > 4096 || !line.startsWith("GET ") || !line.endsWith(" HTTP/1.1"))
      throw new IllegalArgumentException("Неверный запрос входа");
    URI uri = new URI(line.substring(4, line.length() - 9));
    if (uri.isAbsolute() || uri.getRawAuthority() != null || !"/openrouter/callback".equals(uri.getPath()) || uri.getFragment() != null)
      throw new IllegalArgumentException("Неверный адрес возврата");
    Map<String, String> query = new HashMap<>();
    for (String pair : (uri.getRawQuery() == null ? "" : uri.getRawQuery()).split("&")) {
      String[] parts = pair.split("=", 2);
      String name = URLDecoder.decode(parts[0], "UTF-8");
      if (query.containsKey(name)) throw new IllegalArgumentException("Повтор параметра входа");
      query.put(name, parts.length > 1 ? URLDecoder.decode(parts[1], "UTF-8") : "");
    }
    String code = query.get("code");
    if (!state.equals(query.get("state")) || query.containsKey("error") || code == null || code.isEmpty()
        || code.length() > 2048 || !code.matches("[!-~]+"))
      throw new IllegalArgumentException("Код входа не соответствует текущему запросу");
    return code;
  }
  static String requestLine(InputStream input) throws Exception {
    StringBuilder line = new StringBuilder(); int b;
    while ((b = input.read()) != -1 && b != '\n') {
      if (line.length() >= 4096) throw new IOException("Слишком длинный запрос входа");
      if (b != '\r') line.append((char)b);
    }
    return line.toString();
  }
  void signIn(String model, ChatAuth.Listener listener) {
    cancel(); final long id;
    synchronized (this) { id = generation; }
    new Thread(() -> {
      ServerSocket local = null;
      try {
        if (!Arrays.asList(ModelConfig.OPENROUTER_MODELS).contains(model)) throw new IOException();
        String state = ChatAuth.random(), verifier = ChatAuth.random();
        local = new ServerSocket(); local.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
        synchronized (this) { if (!current(id)) return; server = local; }
        local.setSoTimeout(1000);
        long deadline = System.nanoTime() + 600_000_000_000L;
        listener.ready(authorization("http://127.0.0.1:" + local.getLocalPort() + "/openrouter/callback", state, verifier));
        while (current(id) && System.nanoTime() < deadline) {
          Socket accepted;
          try { accepted = local.accept(); } catch (SocketTimeoutException e) { continue; }
          try (Socket socket = accepted) {
            socket.setSoTimeout(5000); String code;
            try { code = callbackCode(requestLine(socket.getInputStream()), state); }
            catch (Exception invalid) { ChatAuth.reply(socket, "This callback does not match the current sign-in.", 400); continue; }
            JSONObject body = Json.obj("code", code, "code_verifier", verifier, "code_challenge_method", "S256");
            // Dedicated connection: cancelling an AI task cannot redirect/replace this key exchange.
            JSONObject raw = Net.exchangeOpenRouterKey(body);
            synchronized (this) {
              if (!current(id)) return;
              config.configure("openrouter", model, raw.getString("key"));
            }
            try { ChatAuth.reply(socket, "OpenRouter connected. Return to TV Assistant.", 200); } catch (Exception ignored) {}
            listener.result("OpenRouter подключён. Вернитесь в ТВ-помощник; модель проверится при первой свободной команде.");
            return;
          }
        }
        if (current(id)) listener.result("Время входа OpenRouter истекло. Нажмите «Войти» ещё раз.");
      } catch (Exception e) {
        if (current(id)) listener.result("Не удалось завершить вход OpenRouter. Попробуйте снова или введите отдельный API-ключ в настройках.");
      } finally {
        if (local != null) try { local.close(); } catch (Exception ignored) {}
        synchronized (this) { if (current(id)) server = null; }
      }
    }, "OpenRouterSignIn").start();
  }
}
