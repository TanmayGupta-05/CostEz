package com.costez;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class ApiController {
  final Store st;
  final ReportService report;
  final Notifications notifications;
  final DomainLookup lookup;

  public ApiController(
      Store st, ReportService report, Notifications notifications, DomainLookup lookup) {
    this.st = st;
    this.report = report;
    this.notifications = notifications;
    this.lookup = lookup;
  }

  @GetMapping("/me")
  public Object me(Principal p) {
    return st.user(p);
  }

  @GetMapping("/overview")
  public Object overview(Principal p) {
    return st.metrics(st.workspace(p));
  }

  @GetMapping("/subscriptions")
  public Object subscriptions(Principal p) {
    return st.subscriptions(st.workspace(p));
  }

  @GetMapping("/subscriptions/{id}")
  public Object detail(Principal p, @PathVariable String id) {
    String w = st.workspace(p);
    var result = st.subscription(st.require("subscriptions", w, id));
    result.put(
        "charges",
        st.db.queryForList(
            "SELECT id,amount_minor AS amount,charged_on AS date FROM charges WHERE workspace_id=?"
                + " AND subscription_id=? ORDER BY charged_on DESC",
            w,
            id));
    result.put(
        "pulses",
        st.db.queryForList(
            "SELECT id,sent_to,created_at,answered_at,answer FROM usage_pulses WHERE workspace_id=?"
                + " AND subscription_id=? ORDER BY created_at DESC",
            w,
            id));
    return result;
  }

  public record SubscriptionInput(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Size(max = 80) String category,
      @NotBlank @Size(max = 100) String owner,
      @Email @Size(max = 254) String ownerEmail,
      @NotBlank @Pattern(regexp = "Monthly|Yearly") String cycle,
      @Min(1) @Max(10000000000L) long amount,
      @NotNull LocalDate renewal,
      @PastOrPresent LocalDate lastUsed,
      boolean active,
      boolean reviewed,
      @NotNull @Size(max = 20) List<@Size(max = 40) String> capabilities,
      @Size(max = 4000) String notes,
      Long version) {}

  String createSubscription(String w, SubscriptionInput s, String source) {
    String id = UUID.randomUUID().toString();
    st.db.update(
        "INSERT INTO"
            + " subscriptions(id,workspace_id,name,category,owner,owner_email,cycle,amount_minor,next_renewal,last_used,active,reviewed,capabilities,notes,source)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        w,
        s.name().trim(),
        s.category().trim(),
        s.owner().trim(),
        s.ownerEmail() == null ? "" : s.ownerEmail().trim(),
        s.cycle(),
        s.amount(),
        s.renewal(),
        s.lastUsed(),
        s.active(),
        s.reviewed(),
        capabilities(s),
        s.notes() == null ? "" : s.notes(),
        source);
    return id;
  }

  String capabilities(SubscriptionInput s) {
    return String.join(
        ",",
        s.capabilities().stream()
            .map(c -> c.trim().toLowerCase(Locale.ROOT))
            .filter(c -> !c.isBlank())
            .distinct()
            .toList());
  }

  @PostMapping("/subscriptions")
  @Transactional
  public Object add(Principal p, @Valid @RequestBody SubscriptionInput s) {
    String w = st.workspace(p);
    return st.subscription(st.require("subscriptions", w, createSubscription(w, s, "MANUAL")));
  }

  @PatchMapping("/subscriptions/{id}")
  public Object update(
      Principal p, @PathVariable String id, @Valid @RequestBody SubscriptionInput s) {
    String w = st.workspace(p);
    st.require("subscriptions", w, id);
    if (s.version() == null)
      throw new ApiError(400, "The record version is required. Refresh and try again.");
    int changed =
        st.db.update(
            "UPDATE subscriptions SET"
                + " name=?,category=?,owner=?,owner_email=?,cycle=?,amount_minor=?,next_renewal=?,last_used=?,active=?,reviewed=?,capabilities=?,notes=?,version=version+1"
                + " WHERE id=? AND workspace_id=? AND version=?",
            s.name().trim(),
            s.category().trim(),
            s.owner().trim(),
            s.ownerEmail() == null ? "" : s.ownerEmail().trim(),
            s.cycle(),
            s.amount(),
            s.renewal(),
            s.lastUsed(),
            s.active(),
            s.reviewed(),
            capabilities(s),
            s.notes() == null ? "" : s.notes(),
            id,
            w,
            s.version());
    if (changed == 0)
      throw new ApiError(409, "Someone changed this record. Refresh before saving.");
    return st.subscription(st.require("subscriptions", w, id));
  }

  @DeleteMapping("/subscriptions/{id}")
  public void delete(Principal p, @PathVariable String id) {
    String w = st.workspace(p);
    st.require("subscriptions", w, id);
    st.db.update("DELETE FROM subscriptions WHERE id=? AND workspace_id=?", id, w);
  }

  public record ChargeInput(@Min(1) @Max(10000000000L) long amount, @NotNull LocalDate date) {}

  @PostMapping("/subscriptions/{id}/charges")
  public Object charge(Principal p, @PathVariable String id, @Valid @RequestBody ChargeInput c) {
    String w = st.workspace(p);
    st.require("subscriptions", w, id);
    st.db.update(
        "INSERT INTO charges(id,workspace_id,subscription_id,amount_minor,charged_on) VALUES"
            + " (?,?,?,?,?)",
        UUID.randomUUID().toString(),
        w,
        id,
        c.amount(),
        c.date());
    return Map.of("ok", true);
  }

  @GetMapping("/domains")
  public Object domains(Principal p) {
    return st.domains(st.workspace(p));
  }

  public record DomainInput(
      @NotBlank
          @Size(max = 253)
          @Pattern(regexp = "(?i)([a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,63}")
          String name,
      @NotBlank @Size(max = 100) String registrar,
      @NotNull LocalDate expires,
      boolean auto) {}

  @PostMapping("/domains")
  public Object addDomain(Principal p, @Valid @RequestBody DomainInput d) {
    String w = st.workspace(p), id = UUID.randomUUID().toString();
    st.db.update(
        "INSERT INTO domains(id,workspace_id,fqdn,registrar,expires_on,auto_renew) VALUES"
            + " (?,?,?,?,?,?)",
        id,
        w,
        d.name().toLowerCase(Locale.ROOT),
        d.registrar(),
        d.expires(),
        d.auto());
    return Map.of("id", id);
  }

  @PatchMapping("/domains/{id}")
  public Object updateDomain(
      Principal p, @PathVariable String id, @Valid @RequestBody DomainInput d) {
    String w = st.workspace(p);
    st.require("domains", w, id);
    st.db.update(
        "UPDATE domains SET fqdn=?,registrar=?,expires_on=?,auto_renew=? WHERE id=? AND"
            + " workspace_id=?",
        d.name().toLowerCase(Locale.ROOT),
        d.registrar(),
        d.expires(),
        d.auto(),
        id,
        w);
    return Map.of("ok", true);
  }

  @DeleteMapping("/domains/{id}")
  public void deleteDomain(Principal p, @PathVariable String id) {
    String w = st.workspace(p);
    st.require("domains", w, id);
    st.db.update("DELETE FROM domains WHERE id=? AND workspace_id=?", id, w);
  }

  @PostMapping("/domains/{id}/refresh")
  public Object refreshDomain(Principal p, @PathVariable String id) {
    String w = st.workspace(p);
    var d = st.require("domains", w, id);
    LocalDate expiry = lookup.expiry((String) d.get("fqdn"));
    st.db.update(
        "UPDATE domains SET expires_on=?,source='RDAP',last_checked_at=CURRENT_TIMESTAMP WHERE id=?"
            + " AND workspace_id=?",
        expiry,
        id,
        w);
    return Map.of("expires", expiry.toString());
  }

  @GetMapping("/imports/template")
  public ResponseEntity<String> template() {
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=costez-template.csv")
        .contentType(MediaType.parseMediaType("text/csv"))
        .body(
            "name,category,owner,owner_email,cycle,amount_inr,next_renewal,last_used,capabilities\n"
                + "Linear,Project management,Rohan,rohan@example.com,Monthly,1200,"
                + Store.today().plusDays(30)
                + ","
                + Store.today()
                + ",projects\n");
  }

  @PostMapping("/imports/csv")
  @Transactional
  public Object csv(Principal p, @RequestParam("file") MultipartFile file) throws Exception {
    String w = st.workspace(p);
    byte[] bytes = file.getBytes();
    String hash = hash(bytes);
    var prior =
        st.db.queryForList(
            "SELECT result_json FROM import_runs WHERE workspace_id=? AND file_hash=?", w, hash);
    if (!prior.isEmpty()) return st.read((String) prior.getFirst().get("result_json"));
    List<String> errors = new ArrayList<>();
    int imported = 0;
    try (var parser =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .get()
            .parse(
                new StringReader(
                    new String(bytes, StandardCharsets.UTF_8).replace("\uFEFF", "")))) {
      if (!parser
          .getHeaderNames()
          .containsAll(List.of("name", "category", "owner", "cycle", "amount_inr", "next_renewal")))
        throw new ApiError(400, "Required CSV columns are missing. Download the template.");
      for (CSVRecord r : parser) {
        if (r.getRecordNumber() > 1000)
          throw new ApiError(400, "Import at most 1000 rows at a time.");
        try {
          long amount =
              new java.math.BigDecimal(r.get("amount_inr").trim())
                  .movePointRight(2)
                  .longValueExact();
          String name = r.get("name").trim(),
              category = r.get("category").trim(),
              owner = r.get("owner").trim(),
              cycle = r.get("cycle").trim(),
              email = r.isMapped("owner_email") ? r.get("owner_email").trim() : "",
              caps = r.isMapped("capabilities") ? r.get("capabilities").trim() : "";
          LocalDate renewal = LocalDate.parse(r.get("next_renewal").trim()),
              used =
                  r.isMapped("last_used") && !r.get("last_used").isBlank()
                      ? LocalDate.parse(r.get("last_used").trim())
                      : null;
          if (name.isEmpty()
              || name.length() > 100
              || category.isEmpty()
              || category.length() > 80
              || owner.isEmpty()
              || owner.length() > 100
              || amount <= 0
              || amount > 10000000000L
              || !Set.of("Monthly", "Yearly").contains(cycle)
              || caps.length() > 800
              || email.length() > 254
              || (!email.isBlank() && !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
              || (used != null && used.isAfter(Store.today())))
            throw new IllegalArgumentException();
          createSubscription(
              w,
              new SubscriptionInput(
                  name,
                  category,
                  owner,
                  email,
                  cycle,
                  amount,
                  renewal,
                  used,
                  true,
                  false,
                  List.of(caps.split(",")),
                  "Imported from CSV",
                  null),
              "CSV");
          imported++;
        } catch (IllegalArgumentException | ArithmeticException | java.time.DateTimeException e) {
          errors.add(
              "Row "
                  + (r.getRecordNumber() + 1)
                  + ": invalid field, amount, billing cycle or date.");
        }
      }
    } catch (IOException e) {
      throw new ApiError(400, "Could not parse the CSV. Check quoting and use the template.");
    }
    var result = Map.of("imported", imported, "errors", errors, "needsReview", true);
    st.db.update(
        "INSERT INTO import_runs(workspace_id,file_hash,result_json) VALUES (?,?,?)",
        w,
        hash,
        st.write(result));
    return result;
  }

  static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @PostMapping("/subscriptions/{id}/pulse")
  @Transactional
  public Object pulse(Principal p, @PathVariable String id) {
    String w = st.workspace(p);
    var s = st.require("subscriptions", w, id);
    String email = (String) s.get("owner_email");
    if (email.isBlank())
      throw new ApiError(400, "Add an owner email before requesting a usage check.");
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random),
        pid = UUID.randomUUID().toString();
    st.db.update(
        "INSERT INTO usage_pulses(id,workspace_id,subscription_id,token_hash,sent_to,expires_on)"
            + " VALUES (?,?,?,?,?,?)",
        pid,
        w,
        id,
        hash(token.getBytes(StandardCharsets.UTF_8)),
        email,
        Store.today().plusDays(14));
    String link = notifications.baseUrl + "/#pulse/" + token;
    notifications.queue(
        w,
        pid,
        "PULSE",
        Store.today(),
        email,
        "Still using " + s.get("name") + "?",
        "Are you still using "
            + s.get("name")
            + "? Confirm your response here: "
            + link
            + "\nThis link expires in 14 days.");
    return Map.of("url", link, "emailEnabled", notifications.enabled);
  }

  @GetMapping("/pulse/{token}")
  public Object publicPulse(@PathVariable String token) {
    var p = validPulse(token);
    return Map.of("name", p.get("name"), "answered", p.get("answered_at") != null);
  }

  Map<String, Object> validPulse(String token) {
    if (token.length() > 100) throw new ApiError(404, "Request not found.");
    var rows =
        st.db.queryForList(
            "SELECT p.*,s.name FROM usage_pulses p JOIN subscriptions s ON s.id=p.subscription_id"
                + " WHERE token_hash=?",
            hash(token.getBytes(StandardCharsets.UTF_8)));
    if (rows.isEmpty()) throw new ApiError(404, "This request is unavailable.");
    var p = rows.getFirst();
    if (LocalDate.parse(p.get("expires_on").toString()).isBefore(Store.today()))
      throw new ApiError(410, "This request has expired.");
    return p;
  }

  public record Answer(@Pattern(regexp = "Yes|No") @NotBlank String answer) {}

  @PostMapping("/pulse/{token}/answer")
  @Transactional
  public Object answer(@PathVariable String token, @Valid @RequestBody Answer a) {
    var p = validPulse(token);
    int changed =
        st.db.update(
            "UPDATE usage_pulses SET answered_at=CURRENT_TIMESTAMP,answer=? WHERE id=? AND"
                + " answered_at IS NULL",
            a.answer(),
            p.get("id"));
    if (changed == 0) throw new ApiError(409, "This request has already been answered.");
    if (a.answer().equals("Yes"))
      st.db.update(
          "UPDATE subscriptions SET pulse_answer=?,last_used=?,version=version+1 WHERE id=? AND"
              + " workspace_id=?",
          a.answer(),
          Store.today(),
          p.get("subscription_id"),
          p.get("workspace_id"));
    else
      st.db.update(
          "UPDATE subscriptions SET pulse_answer=?,version=version+1 WHERE id=? AND workspace_id=?",
          a.answer(),
          p.get("subscription_id"),
          p.get("workspace_id"));
    return Map.of("ok", true);
  }

  @GetMapping("/reports")
  public Object reports(Principal p) {
    return st
        .db
        .queryForList(
            "SELECT id,created_on,output_text,model,input_snapshot FROM reports WHERE"
                + " workspace_id=? ORDER BY created_on DESC,id",
            st.workspace(p))
        .stream()
        .map(
            r -> {
              var m = new LinkedHashMap<>(r);
              m.put("input_snapshot", st.read((String) r.get("input_snapshot")));
              return m;
            })
        .toList();
  }

  @PostMapping("/reports")
  public Object generate(Principal p) {
    return report.generate(st.workspace(p));
  }

  @GetMapping("/reports/{id}")
  public Object getReport(Principal p, @PathVariable String id) {
    var r = st.require("reports", st.workspace(p), id);
    r.put("input_snapshot", st.read((String) r.get("input_snapshot")));
    return r;
  }

  @GetMapping("/notifications")
  public Object notifications(Principal p) {
    return st.db.queryForList(
        "SELECT id,kind,subject,recipient,status,attempts,due_on,sent_at,last_error FROM"
            + " notifications WHERE workspace_id=? ORDER BY due_on DESC",
        st.workspace(p));
  }

  @PostMapping("/notifications/run")
  public Object runReminders(Principal p) {
    notifications.scheduleWorkspace(st.workspace(p));
    return Map.of("ok", true);
  }

  public record WorkspaceInput(@NotBlank @Size(max = 100) String name) {}

  @PatchMapping("/workspace")
  public Object workspace(Principal p, @Valid @RequestBody WorkspaceInput w) {
    st.db.update("UPDATE workspaces SET name=? WHERE id=?", w.name().trim(), st.workspace(p));
    return Map.of("ok", true);
  }

  @GetMapping("/google/status")
  public Object google() {
    return Map.of(
        "status",
        "NOT_CONFIGURED",
        "message",
        "Gmail connector is deferred. Manual entry and CSV import are available.");
  }

  @GetMapping("/integrations")
  public Object integrations() {
    return Map.of("gmail", false, "ai", report.aiEnabled(), "email", notifications.enabled);
  }

  @GetMapping("/export")
  public Object export(Principal p) {
    String w = st.workspace(p);
    return Map.of(
        "workspace",
        st.user(p).get("workspace"),
        "subscriptions",
        st.subscriptions(w),
        "domains",
        st.domains(w),
        "reports",
        reports(p));
  }

  @DeleteMapping("/account")
  @Transactional
  public Object account(Principal p, HttpServletRequest request) {
    String w = st.workspace(p);
    st.db.update("DELETE FROM workspaces WHERE id=?", w);
    var session = request.getSession(false);
    if (session != null) session.invalidate();
    return Map.of("ok", true);
  }
}
