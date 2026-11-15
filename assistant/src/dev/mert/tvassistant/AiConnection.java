package dev.mert.tvassistant;

import org.json.JSONObject;

/** One provider per task; never fall back to another service or replay an uncertain request. */
interface AiConnection extends AutoCloseable {
  JSONObject create(JSONObject request, Net.Events events) throws Exception;
  int requests();
  int resets();
  String transport();
  void close();

  final class ChatGPT implements AiConnection {
    private final ChatAuth auth;
    private final ResponseSocket socket;
    ChatGPT(ChatAuth auth) throws Exception {
      this.auth = auth;
      socket = new ResponseSocket(auth.token());
    }
    public JSONObject create(JSONObject request, Net.Events events) throws Exception {
      return socket.create(auth.token(), request, events);
    }
    public int requests() { return socket.requests; }
    public int resets() { return socket.resets; }
    public String transport() { return socket.transport; }
    public void close() { socket.close(); }
  }

  final class Qwen implements AiConnection {
    private final ModelConfig config;
    private int requests;
    private volatile boolean closed;
    Qwen(ModelConfig config) { this.config = config; }
    public JSONObject create(JSONObject request, Net.Events events) throws Exception {
      if (closed || events.cancelled()) throw new InterruptedException("Задача остановлена");
      JSONObject body = QwenProtocol.request(request, config.provider());
      requests++;
      JSONObject raw = Net.inferJson(config.endpoint() + "/chat/completions", config.token(), body, events);
      if (closed || events.cancelled()) throw new InterruptedException("Задача остановлена");
      JSONObject response = QwenProtocol.response(raw, body.getJSONArray("tools"));
      try { config.markVerified(); } catch (Exception ignored) { /* Keep an already received result usable. */ }
      return response;
    }
    public int requests() { return requests; }
    public int resets() { return 0; }
    public String transport() { return "qwen-https"; }
    public void close() { closed = true; }
  }
}
