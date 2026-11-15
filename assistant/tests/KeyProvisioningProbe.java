package dev.mert.tvassistant;

import java.io.*;
import java.net.*;
import java.security.*;
import org.json.*;

/** JVM transport fixture for cross-language tests, never saves a credential or invokes Android. */
public final class KeyProvisioningProbe {
  public static void main(String[] args) throws Exception {
    String token = args[0]; KeyProvisioning.token(token);
    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
      server.setSoTimeout(30000); System.out.println(Json.obj("port", server.getLocalPort())); System.out.flush();
      try (Socket socket = server.accept()) {
        socket.setSoTimeout(20000);
        try {
          KeyProvisioning.hello(KeyProvisioning.read(socket.getInputStream()), token);
          if (args.length > 1 && args[1].equals("diagnose")) {
            JSONObject report=Json.obj("provider","openrouter","model","qwen/qwen3.5-9b","configured",true,"key_fingerprint","abcdef123456","probes",Json.arr(
              Json.obj("stage","catalog","http",200,"format","json","kind","ok","recognized",false,"elapsed_ms",1),
              Json.obj("stage","key","http",403,"format","html","kind","browser_challenge","recognized",false,"elapsed_ms",2)));
            KeyProvisioning.write(socket.getOutputStream(),Json.obj("version",1,"diagnostics",report));
            System.out.println("{\"diagnostics_sent\":true}"); return;
          }
          KeyPair keys = KeyProvisioning.keyPair(); KeyProvisioning.write(socket.getOutputStream(), KeyProvisioning.publicHello(keys));
          JSONObject packet = KeyProvisioning.read(socket.getInputStream());
          if (args.length > 1 && args[1].equals("tamper")) {
            byte[] ciphertext = KeyProvisioning.decode(packet.getString("payload"), 2048);
            ciphertext[ciphertext.length-1] ^= 1; packet.put("payload", KeyProvisioning.encode(ciphertext));
          }
          JSONObject payload = KeyProvisioning.decrypt(packet, keys.getPrivate(), token);
          KeyProvisioning.write(socket.getOutputStream(), Json.obj("ok", true, "provider", KeyProvisioning.provider(payload), "model", payload.getString("model")));
          System.out.println(Json.obj("model", payload.getString("model"), "key_sha256", KeyProvisioning.encode(MessageDigest.getInstance("SHA-256").digest(payload.getString("key").getBytes("UTF-8")))));
        } catch (Exception e) { KeyProvisioning.write(socket.getOutputStream(), Json.obj("ok", false)); System.out.println("{\"rejected\":true}"); }
      }
    }
  }
}
