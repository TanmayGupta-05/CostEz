package com.costez;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Protects queued email bodies, including bearer links, at rest. */
@Service
public class MailCipher {
  private final byte[] key;

  public MailCipher(
      @Value("${TOKEN_ENCRYPTION_KEY:}") String configured,
      @Value("${spring.datasource.url}") String databaseUrl)
      throws Exception {
    if (configured.isBlank()) {
      if (databaseUrl.startsWith("jdbc:postgresql:")) {
        throw new IllegalStateException(
            "Set TOKEN_ENCRYPTION_KEY to a base64-encoded 32-byte key for PostgreSQL deployments.");
      }
      Path file = Path.of("data", ".mail-key");
      Files.createDirectories(file.getParent());
      if (!Files.exists(file)) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        try {
          Files.writeString(
              file, Base64.getEncoder().encodeToString(bytes), StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException ignored) {
        }
      }
      configured = Files.readString(file).trim();
    }
    key = Base64.getDecoder().decode(configured);
    if (key.length != 32)
      throw new IllegalStateException("TOKEN_ENCRYPTION_KEY must decode to 32 bytes.");
  }

  public String encrypt(String text) {
    try {
      byte[] nonce = new byte[12];
      new SecureRandom().nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
      return "enc:"
          + Base64.getEncoder().encodeToString(nonce)
          + ":"
          + Base64.getEncoder()
              .encodeToString(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Could not encrypt email payload", e);
    }
  }

  public String decrypt(String text) {
    try {
      String[] parts = text.split(":", 3);
      if (parts.length != 3 || !parts[0].equals("enc")) throw new IllegalArgumentException();
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(key, "AES"),
          new GCMParameterSpec(128, Base64.getDecoder().decode(parts[1])));
      return new String(
          cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Could not decrypt queued email. Check encryption key.", e);
    }
  }
}
