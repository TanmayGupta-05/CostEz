package com.costez;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MailCipherTest {
  @Test
  void randomizedAuthenticatedEncryption() throws Exception {
    var cipher = new MailCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "jdbc:h2:mem:test");
    var one = cipher.encrypt("A bearer link: secret-token");
    var two = cipher.encrypt("A bearer link: secret-token");
    assertNotEquals(one, two);
    assertFalse(one.contains("secret-token"));
    assertEquals("A bearer link: secret-token", cipher.decrypt(one));
    assertThrows(
        IllegalStateException.class,
        () -> cipher.decrypt(one.substring(0, one.length() - 4) + "AAAA"));
  }
}
