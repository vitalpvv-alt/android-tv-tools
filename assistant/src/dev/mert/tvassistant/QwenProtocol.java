package dev.mert.tvassistant;

import java.util.*;
import org.json.*;

/** Strict chat-completions bridge. Model prose is never interpreted as an executable command. */
final class QwenProtocol {
  private static final Set<String> TV_TOOLS = new HashSet<>(Arrays.asList(
      "list_apps", "open_app", "search_app", "open_url", "navigate", "screen_read",
      "screen_click", "screen_type", "screen_scroll", "volume", "playback", "list_sessions",
      "open_settings", "device_info", "clock", "wait"));

  static JSONObject request(JSONObject source, String provider) throws Exception {
    JSONArray messages = new JSONArray();
    messages.put(Json.obj("role", "system", "content", source.getString("instructions")));
    JSONArray input = source.getJSONArray("input");
    JSONObject pending = null;
    for (int i = 0; i < input.length(); i++) {
      JSONObject item = input.getJSONObject(i);
      String type = item.optString("type");
      if (type.equals("function_call")) {
        if (pending == null) {
          pending = Json.obj("role", "assistant", "content", JSONObject.NULL, "tool_calls", new JSONArray());
          messages.put(pending);
        }
        pending.getJSONArray("tool_calls").put(Json.obj("id", item.getString("call_id"), "type", "function",
            "function", Json.obj("name", item.getString("name"), "arguments", item.getString("arguments"))));
      } else if (type.equals("function_call_output")) {
        pending = null;
        messages.put(Json.obj("role", "tool", "tool_call_id", item.getString("call_id"), "content", item.getString("output")));
      } else {
        pending = null;
        String role = item.optString("role");
        if (!role.equals("user") && !role.equals("assistant"))
          throw new IllegalArgumentException("Неподдерживаемый тип истории ИИ");
        Object content = item.opt("content");
        if (content instanceof String) messages.put(Json.obj("role", role, "content", content));
        else if (content instanceof JSONArray) {
          StringBuilder text = new StringBuilder();
          JSONArray parts = (JSONArray)content;
          for (int j = 0; j < parts.length(); j++) {
            JSONObject part = parts.getJSONObject(j);
            if (!part.optString("type").equals("input_text") && !part.optString("type").equals("output_text"))
              throw new IllegalArgumentException("Этот режим Qwen использует текст экрана; снимки не отправляются");
            text.append(part.optString("text"));
          }
          messages.put(Json.obj("role", role, "content", text.toString()));
        } else throw new IllegalArgumentException("Некорректная история ИИ");
      }
    }
    JSONArray functions = new JSONArray();
    JSONArray namespaces = source.getJSONArray("tools");
    for (int i = 0; i < namespaces.length(); i++) {
      JSONObject group = namespaces.getJSONObject(i);
      JSONArray definitions = group.getJSONArray("tools");
      for (int j = 0; j < definitions.length(); j++) {
        JSONObject def = definitions.getJSONObject(j);
        if (!TV_TOOLS.contains(def.getString("name"))) continue;
        JSONObject f = Json.obj("name", def.getString("name"),
            "description", RuUi.toolDescription(def.getString("name"), def.optString("description")),
            "parameters", def.getJSONObject("parameters"));
        functions.put(Json.obj("type", "function", "function", f));
      }
    }
    JSONObject body = Json.obj("model", source.getString("model"), "messages", messages,
        "tools", functions, "stream", false, "max_tokens", 1536, "temperature", 0.2,
        "parallel_tool_calls", false);
    if (source.has("tool_choice")) body.put("tool_choice", source.get("tool_choice"));
    if (provider.equals("modelstudio") || provider.equals("qwencloud")) body.put("enable_thinking", false);
    return body;
  }

  static JSONObject response(JSONObject raw, JSONArray advertised) throws Exception {
    if (raw.has("error")) throw new IllegalStateException("Сервис Qwen вернул ошибку");
    JSONArray choices = raw.optJSONArray("choices");
    if (choices == null || choices.length() != 1) throw new IllegalStateException("Qwen вернул неполный ответ");
    JSONObject choice = choices.getJSONObject(0);
    String finish = choice.optString("finish_reason");
    if (!finish.equals("stop") && !finish.equals("tool_calls"))
      throw new IllegalStateException("Ответ Qwen оборван или отклонён; действия не выполнялись");
    JSONObject message = choice.getJSONObject("message");
    if (!message.optString("role").equals("assistant")) throw new IllegalStateException("Некорректный ответ Qwen");
    Map<String, JSONObject> allowed = new HashMap<>();
    for (int i = 0; i < advertised.length(); i++) {
      JSONObject f = advertised.getJSONObject(i).getJSONObject("function");
      allowed.put(f.getString("name"), f.getJSONObject("parameters"));
    }
    JSONArray output = new JSONArray();
    String text = message.isNull("content") ? "" : message.optString("content");
    if (!text.isEmpty()) output.put(Json.obj("type", "message", "role", "assistant", "content",
        Json.arr(Json.obj("type", "output_text", "text", text))));
    JSONArray calls = message.optJSONArray("tool_calls");
    if (calls != null && calls.length() > 8) throw new IllegalStateException("Qwen запросил слишком много действий");
    if (finish.equals("tool_calls") && (calls == null || calls.length() == 0))
      throw new IllegalStateException("Qwen не передал запрошенные действия");
    Set<String> ids = new HashSet<>();
    if (calls != null) for (int i = 0; i < calls.length(); i++) {
      JSONObject c = calls.getJSONObject(i), f = c.getJSONObject("function");
      String id = c.getString("id"), name = f.getString("name");
      if (!c.optString("type").equals("function") || !id.matches("[A-Za-z0-9_-]{1,200}")
          || !ids.add(id) || !allowed.containsKey(name))
        throw new IllegalStateException("Qwen запросил неподдерживаемое действие");
      Object rawArgs = f.get("arguments");
      if (!(rawArgs instanceof String) || ((String)rawArgs).length() > 16000)
        throw new IllegalStateException("Некорректные параметры действия Qwen");
      JSONObject args = new JSONObject((String)rawArgs);
      ToolArguments.validate(allowed.get(name), args);
      output.put(Json.obj("type", "function_call", "namespace", "tv", "name", name,
          "call_id", id, "arguments", args.toString()));
    }
    if (output.length() == 0) throw new IllegalStateException("Qwen не вернул текст или действие");
    JSONObject usage = raw.optJSONObject("usage");
    return Json.obj("output", output, "usage", usage == null ? Json.obj() :
        Json.obj("input_tokens", usage.optInt("prompt_tokens"), "output_tokens", usage.optInt("completion_tokens")));
  }
}
