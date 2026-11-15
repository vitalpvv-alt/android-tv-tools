package dev.mert.tvassistant;

import java.io.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.json.*;

/** One-use PC-to-TV key setup. No credentials in Intents, files, UI text or replies. */
final class KeyProvisioning {
  static final String PREFIX = "tvassistant.key.";
  private static final String B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
  static String socketName(String id) {
    if (id == null || !id.matches("[a-f0-9]{32}")) throw new IllegalArgumentException("Неверная сессия настройки");
    return PREFIX + id;
  }
  static void token(String token) {
    if (token == null || !token.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Неверная сессия настройки");
  }
  static String encode(byte[] bytes) {
    StringBuilder s = new StringBuilder(); int value = 0, bits = 0;
    for (byte b : bytes) {
      value = (value << 8) | (b & 255); bits += 8;
      while (bits >= 6) { bits -= 6; s.append(B64.charAt((value >>> bits) & 63)); }
    }
    if (bits > 0) s.append(B64.charAt((value << (6 - bits)) & 63));
    while (s.length() % 4 != 0) s.append('=');
    return s.toString();
  }
  static byte[] decode(String s, int maxBytes) {
    if (s.length() > ((maxBytes + 2) / 3) * 4 || !s.matches("[A-Za-z0-9+/]*={0,2}")) throw new IllegalArgumentException("Неверный пакет настройки");
    ByteArrayOutputStream out = new ByteArrayOutputStream(); int value = 0, bits = 0;
    for (int i = 0; i < s.length() && s.charAt(i) != '='; i++) {
      value = (value << 6) | B64.indexOf(s.charAt(i)); bits += 6;
      if (bits >= 8) { bits -= 8; out.write((value >>> bits) & 255); }
    }
    byte[] bytes = out.toByteArray();
    if (bytes.length > maxBytes || !encode(bytes).equals(s)) throw new IllegalArgumentException("Неверный пакет настройки");
    return bytes;
  }
  static JSONObject read(InputStream in) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream(); int b;
    while ((b = in.read()) != -1 && b != '\n') {
      if (out.size() >= 8192 || b == '\r' || b == 0) throw new IOException("Неверный пакет настройки");
      out.write(b);
    }
    if (b != '\n') throw new IOException("Пакет настройки не завершён");
    return json(new String(out.toByteArray(), "UTF-8"));
  }
  static JSONObject json(String s) throws Exception {
    if (s.length() > 8192) throw new IOException("Слишком большой пакет настройки");
    boolean quoted = false, escaped = false; int depth = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (escaped) { escaped = false; continue; }
      if (quoted && c == '\\') { escaped = true; continue; }
      if (c == '"') quoted = !quoted;
      else if (!quoted && (c == '{' || c == '[') && ++depth > 8) throw new IOException("Слишком вложенный пакет настройки");
      else if (!quoted && (c == '}' || c == ']')) depth--;
    }
    JSONTokener tokener = new JSONTokener(s); Object o = tokener.nextValue();
    if (!(o instanceof JSONObject) || tokener.nextClean() != 0) throw new IOException("Неверный пакет настройки");
    return (JSONObject)o;
  }
  static void write(OutputStream out, JSONObject object) throws Exception {
    out.write((object.toString() + "\n").getBytes("UTF-8")); out.flush();
  }
  private static void fields(JSONObject object, String... names) {
    Set<String> allowed = new HashSet<>(Arrays.asList(names));
    Iterator<String> keys = object.keys();
    while (keys.hasNext()) if (!allowed.contains(keys.next())) throw new IllegalArgumentException("Лишнее поле настройки");
    if (object.length() != names.length) throw new IllegalArgumentException("Не хватает полей настройки");
  }
  static void hello(JSONObject message, String expected) throws Exception {
    token(expected); fields(message, "token", "type");
    String supplied = message.getString("token"); token(supplied);
    if (!message.getString("type").equals("hello") || !MessageDigest.isEqual(expected.getBytes("US-ASCII"), supplied.getBytes("US-ASCII")))
      throw new SecurityException("Настройка не авторизована");
  }
  static KeyPair keyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair();
  }
  static JSONObject publicHello(KeyPair keys) { return Json.obj("version", 1, "public_key", encode(keys.getPublic().getEncoded())); }
  static JSONObject decrypt(JSONObject message, PrivateKey privateKey, String token) throws Exception {
    token(token); fields(message, "wrapped", "iv", "payload");
    byte[] wrapped = decode(message.getString("wrapped"), 256), iv = decode(message.getString("iv"), 12), ciphertext = decode(message.getString("payload"), 2048);
    if (wrapped.length != 256 || iv.length != 12 || ciphertext.length < 16) throw new IOException("Неверный пакет настройки");
    Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
    rsa.init(Cipher.DECRYPT_MODE, privateKey, new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
    byte[] secret = rsa.doFinal(wrapped);
    try {
      if (secret.length != 32) throw new IOException("Неверный ключ сессии");
      Cipher aes = Cipher.getInstance("AES/GCM/NoPadding"); aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secret, "AES"), new GCMParameterSpec(128, iv));
      aes.updateAAD(token.getBytes("US-ASCII"));
      JSONObject payload = json(new String(aes.doFinal(ciphertext), "UTF-8")); validate(payload); return payload;
    } finally { Arrays.fill(secret, (byte)0); }
  }
  static void validate(JSONObject payload) throws Exception {
    if (payload.has("provider")) fields(payload, "provider", "model", "key"); else fields(payload, "model", "key");
    String provider = provider(payload);
    String key = payload.getString("key");
    if (provider.equals("openrouter") && !key.matches("sk-or-[A-Za-z0-9_-]{26,506}")) throw new IllegalArgumentException("Нужен отдельный API-ключ OpenRouter");
    ModelConfig.validate(provider, payload.getString("model"), key);
  }
  static String provider(JSONObject payload) throws Exception {
    String provider = payload.has("provider") ? payload.getString("provider") : "openrouter";
    if (!provider.equals("openrouter") && !provider.equals("xkiro")) throw new IllegalArgumentException("Сервис не поддерживает передачу ключа с ПК");
    return provider;
  }
}
