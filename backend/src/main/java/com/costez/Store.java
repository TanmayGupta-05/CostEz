package com.costez;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.Principal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class Store {
  final JdbcTemplate db;
  final ObjectMapper json;

  public Store(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  public Map<String, Object> user(Principal p) {
    return db
        .queryForList(
            "SELECT u.id,u.email,u.name,u.role,u.workspace_id,w.name AS workspace FROM users u JOIN"
                + " workspaces w ON w.id=u.workspace_id WHERE u.email=?",
            p.getName())
        .stream()
        .findFirst()
        .orElseThrow(() -> new ApiError(401, "Sign in again."));
  }

  public String workspace(Principal p) {
    return (String) user(p).get("workspace_id");
  }

  public Map<String, Object> require(String table, String w, String id) {
    if (!Set.of("subscriptions", "domains", "reports").contains(table))
      throw new IllegalArgumentException();
    return db
        .queryForList("SELECT * FROM " + table + " WHERE workspace_id=? AND id=?", w, id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ApiError(404, "Record not found."));
  }

  public List<Map<String, Object>> subscriptions(String w) {
    return db
        .queryForList("SELECT * FROM subscriptions WHERE workspace_id=? ORDER BY name", w)
        .stream()
        .map(this::subscription)
        .toList();
  }

  public Map<String, Object> subscription(Map<String, Object> row) {
    var m = new LinkedHashMap<String, Object>();
    m.put("id", row.get("id"));
    m.put("name", row.get("name"));
    m.put("category", row.get("category"));
    m.put("owner", row.get("owner"));
    m.put("ownerEmail", row.get("owner_email"));
    m.put("cycle", row.get("cycle"));
    m.put("amount", ((Number) row.get("amount_minor")).longValue());
    m.put("renewal", row.get("next_renewal").toString());
    m.put("lastUsed", row.get("last_used") == null ? null : row.get("last_used").toString());
    m.put("active", row.get("active"));
    m.put("reviewed", row.get("reviewed"));
    m.put(
        "capabilities",
        Arrays.stream(((String) row.get("capabilities")).split(","))
            .filter(s -> !s.isBlank())
            .toList());
    m.put("notes", row.get("notes"));
    m.put("pulse", row.get("pulse_answer"));
    m.put("source", row.get("source"));
    m.put("version", row.get("version"));
    m.put("unused", unused(m));
    return m;
  }

  public static LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Kolkata"));
  }

  public static boolean unused(Map<String, Object> s) {
    return Boolean.TRUE.equals(s.get("active"))
        && Boolean.TRUE.equals(s.get("reviewed"))
        && ("No".equals(s.get("pulse"))
            || (s.get("lastUsed") != null
                && LocalDate.parse(s.get("lastUsed").toString()).isBefore(today().minusDays(60))));
  }

  public static long monthly(Map<String, Object> s) {
    long a = ((Number) s.get("amount")).longValue();
    return "Yearly".equals(s.get("cycle")) ? Math.round(a / 12.0) : a;
  }

  public Map<String, Object> metrics(String w) {
    var all = subscriptions(w);
    var live =
        all.stream()
            .filter(
                s -> Boolean.TRUE.equals(s.get("active")) && Boolean.TRUE.equals(s.get("reviewed")))
            .toList();
    long total = live.stream().mapToLong(Store::monthly).sum(),
        waste = live.stream().filter(Store::unused).mapToLong(Store::monthly).sum(),
        renewing =
            live.stream()
                .filter(
                    s -> {
                      var d = LocalDate.parse(s.get("renewal").toString());
                      return !d.isBefore(today()) && !d.isAfter(today().plusDays(30));
                    })
                .mapToLong(s -> ((Number) s.get("amount")).longValue())
                .sum();
    return Map.of(
        "total",
        total,
        "waste",
        waste,
        "renewing",
        renewing,
        "flagged",
        live.stream().filter(Store::unused).count());
  }

  public String write(Object v) {
    try {
      return json.writeValueAsString(v);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  public Object read(String v) {
    try {
      return json.readValue(v, Object.class);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  public List<Map<String, Object>> domains(String w) {
    return db
        .queryForList("SELECT * FROM domains WHERE workspace_id=? ORDER BY expires_on", w)
        .stream()
        .<Map<String, Object>>map(
            r -> {
              var m = new LinkedHashMap<String, Object>();
              m.put("id", r.get("id"));
              m.put("name", r.get("fqdn"));
              m.put("registrar", r.get("registrar"));
              m.put("expires", r.get("expires_on").toString());
              m.put("auto", r.get("auto_renew"));
              m.put("source", r.get("source"));
              m.put("lastChecked", r.get("last_checked_at"));
              return m;
            })
        .toList();
  }
}
