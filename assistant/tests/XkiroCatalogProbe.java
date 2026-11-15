package dev.mert.tvassistant;
import java.nio.file.*;
import org.json.*;
/** Replays the public catalog through the production zero-price guard, no credential or inference. */
public final class XkiroCatalogProbe {
  public static void main(String[] args) throws Exception {
    JSONObject catalog=new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8"));
    for(String model:ModelConfig.XKIRO_MODELS) {
      FreeCommands.requireFreeXkiroModel(model,catalog);
      System.out.println(Json.obj("model",model,"free_price_guard_passed",true));
    }
  }
}
