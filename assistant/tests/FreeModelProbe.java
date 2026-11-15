package dev.mert.tvassistant;

import java.io.*;
import org.json.*;

/** Synthetic fixtures only. Exports the app's actual request/parser, never invokes Android tools. */
public final class FreeModelProbe {
  public static void main(String[] args) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] buf = new byte[8192]; int n;
    while ((n = System.in.read(buf)) != -1) bytes.write(buf, 0, n);
    String input = new String(bytes.toByteArray(), "UTF-8");
    JSONArray apps = Json.arr(Json.obj("label", "Wink", "package", "ru.rt.video.app.tv"),
        Json.obj("label", "YouTube", "package", "com.google.android.youtube.tv"));
    if (args[0].equals("request")) {
      String provider = args.length > 1 ? args[1] : "pollinations";
      String model = args.length > 2 ? args[2] : ModelConfig.defaultModel(provider);
      System.out.print(FreeCommands.request(input, apps, model, provider));
    } else {
      FreeCommands.Plan p = FreeCommands.parse(new JSONObject(input), apps);
      System.out.print(Json.obj("steps", p.steps, "reply", p.reply));
    }
  }
}
