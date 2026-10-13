package com.costez;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final JdbcTemplate db;
  private final PasswordEncoder encoder;
  private final boolean registration;

  public AuthController(
      JdbcTemplate db,
      PasswordEncoder encoder,
      @Value("${costez.allow-registration}") boolean registration) {
    this.db = db;
    this.encoder = encoder;
    this.registration = registration;
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @GetMapping("/config")
  public Map<String, Boolean> config() {
    return Map.of(
        "registration",
        registration,
        "demo",
        db.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email='demo@costez.local'", Integer.class)
            > 0);
  }

  public record Registration(
      @Email @NotBlank @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 100) String password,
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Size(max = 100) String workspace) {}

  @PostMapping("/register")
  @Transactional
  public Map<String, Object> register(@Valid @RequestBody Registration r) {
    if (!registration)
      throw new ApiError(403, "Registration is disabled. Ask your administrator for access.");
    String email = r.email().trim().toLowerCase(Locale.ROOT);
    if (db.queryForObject("SELECT COUNT(*) FROM users WHERE email=?", Integer.class, email) > 0)
      throw new ApiError(409, "This email is already registered.");
    String w = UUID.randomUUID().toString();
    db.update("INSERT INTO workspaces(id,name) VALUES (?,?)", w, r.workspace().trim());
    db.update(
        "INSERT INTO users(id,workspace_id,email,name,password_hash,role) VALUES"
            + " (?,?,?,?,?,'OWNER')",
        UUID.randomUUID().toString(),
        w,
        email,
        r.name().trim(),
        encoder.encode(r.password()));
    return Map.of("ok", true);
  }
}
