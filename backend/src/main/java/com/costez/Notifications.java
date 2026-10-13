package com.costez;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class Notifications {
  final Store st;
  final JavaMailSender mail;
  final MailCipher cipher;
  final String baseUrl, from;
  final boolean enabled;

  public Notifications(
      Store st,
      JavaMailSender mail,
      MailCipher cipher,
      @Value("${costez.base-url}") String baseUrl,
      @Value("${costez.mail-from}") String from,
      @Value("${costez.mail-enabled}") boolean enabled) {
    this.st = st;
    this.mail = mail;
    this.cipher = cipher;
    this.baseUrl = baseUrl.replaceAll("/$", "");
    this.from = from;
    this.enabled = enabled;
  }

  public void queue(
      String w,
      String target,
      String kind,
      LocalDate due,
      String recipient,
      String subject,
      String body) {
    st.db.update(
        "INSERT INTO"
            + " notifications(id,workspace_id,target_id,kind,due_on,recipient,subject,body,status)"
            + " SELECT ?,?,?,?,?,?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE"
            + " target_id=? AND kind=? AND due_on=?)",
        UUID.randomUUID().toString(),
        w,
        target,
        kind,
        due,
        recipient,
        subject,
        cipher.encrypt(body),
        enabled ? "PENDING" : "PREVIEW",
        target,
        kind,
        due);
  }

  public void scheduleWorkspace(String w) {
    String email =
        st.db.queryForObject(
            "SELECT email FROM users WHERE workspace_id=? AND role='OWNER' ORDER BY email FETCH"
                + " FIRST 1 ROW ONLY",
            String.class,
            w);
    LocalDate today = Store.today();
    for (var s : st.subscriptions(w)) {
      if (!Boolean.TRUE.equals(s.get("active")) || !Boolean.TRUE.equals(s.get("reviewed")))
        continue;
      var renewal = LocalDate.parse(s.get("renewal").toString());
      long days = ChronoUnit.DAYS.between(today, renewal);
      if (Set.of(30L, 15L, 7L, 1L).contains(days))
        queue(
            w,
            (String) s.get("id"),
            "RENEWAL",
            today,
            email,
            s.get("name") + " renews soon",
            s.get("name")
                + " renews on "
                + renewal
                + " for "
                + ReportService.money(((Number) s.get("amount")).longValue())
                + ". Review: "
                + baseUrl
                + "/#subscriptions");
    }
    for (var d : st.domains(w)) {
      var expires = LocalDate.parse(d.get("expires").toString());
      long days = ChronoUnit.DAYS.between(today, expires);
      if (Set.of(60L, 30L, 14L, 7L, 1L).contains(days))
        queue(
            w,
            (String) d.get("id"),
            "DOMAIN",
            today,
            email,
            d.get("name") + " expiry reminder",
            d.get("name")
                + " expires on "
                + expires
                + ". Auto-renew: "
                + d.get("auto")
                + ". Review: "
                + baseUrl
                + "/#domains");
    }
  }

  @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Kolkata")
  public void schedule() {
    String id = UUID.randomUUID().toString();
    st.db.update(
        "INSERT INTO job_runs(id,job_name,status) VALUES (?,'daily-reminders','RUNNING')", id);
    try {
      for (var w : st.db.queryForList("SELECT id FROM workspaces"))
        scheduleWorkspace((String) w.get("id"));
      st.db.update(
          "UPDATE job_runs SET status='SUCCESS',finished_at=CURRENT_TIMESTAMP WHERE id=?", id);
    } catch (Exception e) {
      st.db.update(
          "UPDATE job_runs SET status='FAILED',finished_at=CURRENT_TIMESTAMP WHERE id=?", id);
    }
  }

  @Scheduled(fixedDelay = 30000, initialDelay = 15000)
  public synchronized void deliver() {
    if (!enabled) return;
    for (var n :
        st.db.queryForList(
            "SELECT * FROM notifications WHERE status='PENDING' AND next_attempt_at <="
                + " CURRENT_TIMESTAMP ORDER BY due_on FETCH FIRST 25 ROWS ONLY")) {
      String id = (String) n.get("id");
      if (st.db.update(
              "UPDATE notifications SET status='SENDING',attempts=attempts+1 WHERE id=? AND"
                  + " status='PENDING'",
              id)
          == 0) continue;
      try {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo((String) n.get("recipient"));
        message.setSubject((String) n.get("subject"));
        message.setText(cipher.decrypt((String) n.get("body")));
        mail.send(message);
        st.db.update(
            "UPDATE notifications SET status='SENT',sent_at=CURRENT_TIMESTAMP,last_error=NULL WHERE"
                + " id=?",
            id);
      } catch (Exception e) {
        int attempts = ((Number) n.get("attempts")).intValue() + 1;
        st.db.update(
            "UPDATE notifications SET status=?,next_attempt_at=?,last_error=? WHERE id=?",
            attempts >= 5 ? "FAILED" : "PENDING",
            java.sql.Timestamp.valueOf(LocalDateTime.now().plusMinutes(Math.min(60, attempts * 5))),
            "SMTP delivery failed. Check server mail configuration.",
            id);
      }
    }
  }
}
