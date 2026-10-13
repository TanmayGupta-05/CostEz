package com.costez;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DemoSeed implements ApplicationRunner {
  final Store st;
  final PasswordEncoder passwords;
  final boolean seed;
  final String password;

  public DemoSeed(
      Store st,
      PasswordEncoder passwords,
      @Value("${costez.seed-demo}") boolean seed,
      @Value("${costez.demo-password}") String password) {
    this.st = st;
    this.passwords = passwords;
    this.seed = seed;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(org.springframework.boot.ApplicationArguments args) {
    st.db.update(
        "UPDATE notifications SET status='PENDING',next_attempt_at=CURRENT_TIMESTAMP WHERE"
            + " status='SENDING'");
    if (!seed
        || st.db.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email='demo@costez.local'", Integer.class)
            > 0) return;
    String w = UUID.randomUUID().toString();
    st.db.update("INSERT INTO workspaces(id,name) VALUES (?,'Kiran Labs')", w);
    st.db.update(
        "INSERT INTO users(id,workspace_id,email,name,password_hash) VALUES"
            + " (?,?,'demo@costez.local','Priya',?)",
        UUID.randomUUID().toString(),
        w,
        passwords.encode(password));
    Object[][] rows = {
      {"Slack", "Team chat", "Priya", 8400, 0, 21, "chat", "Monthly"},
      {"Zoom", "Video meetings", "Arjun", 1500, 41, 15, "video", "Monthly"},
      {"Google Workspace", "Email & office", "Priya", 4200, 0, 19, "video,email", "Monthly"},
      {"Figma", "Design", "Meera", 3600, 3, 27, "design", "Monthly"},
      {"Canva Pro", "Design", "Meera", 4999, 20, 93, "design", "Yearly"},
      {"Adobe Creative Cloud", "Design", "Meera", 4230, 74, 23, "design", "Monthly"},
      {"Notion", "Docs & wiki", "Priya", 1600, 0, 36, "docs", "Monthly"},
      {"Confluence", "Docs & wiki", "Rohan", 2900, 58, 40, "docs", "Monthly"},
      {"Jira", "Project management", "Rohan", 7200, 1, 30, "projects", "Monthly"},
      {"GitHub Team", "Developer tools", "Rohan", 3300, 0, 34, "code", "Monthly"},
      {"ChatGPT Team", "AI assistant", "Priya", 5000, 2, 38, "ai", "Monthly"},
      {"Mailchimp", "Email marketing", "Arjun", 1200, 92, 17, "marketing", "Monthly"},
      {"Loom", "Video recordings", "Arjun", 1100, 0, 25, "recording", "Monthly"}
    };
    for (int i = 0; i < rows.length; i++) {
      Object[] r = rows[i];
      String id = UUID.randomUUID().toString();
      st.db.update(
          "INSERT INTO"
              + " subscriptions(id,workspace_id,name,category,owner,owner_email,amount_minor,last_used,next_renewal,capabilities,cycle,reviewed,pulse_answer,notes,source)"
              + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
          id,
          w,
          r[0],
          r[1],
          r[2],
          r[2].toString().toLowerCase() + "@example.com",
          ((int) r[3]) * 100L,
          Store.today().minusDays((int) r[4]),
          Store.today().plusDays((int) r[5]),
          r[6],
          r[7],
          i != 12,
          i == 1 ? "No" : null,
          i == 1 ? "Owner confirmed this tool is no longer used." : "Fictional demonstration data.",
          "DEMO");
      for (int j = 0; j < 3; j++)
        st.db.update(
            "INSERT INTO charges(id,workspace_id,subscription_id,amount_minor,charged_on) VALUES"
                + " (?,?,?,?,?)",
            UUID.randomUUID().toString(),
            w,
            id,
            ((int) r[3]) * 100L,
            Store.today().minusDays(15 + j * (r[7].equals("Yearly") ? 365 : 30)));
    }
    String[] names = {"kiranlabs.com", "kiranlabs.in", "kiranlabs.app"};
    int[] days = {30, 87, 152};
    for (int i = 0; i < 3; i++)
      st.db.update(
          "INSERT INTO domains(id,workspace_id,fqdn,registrar,expires_on,auto_renew,source) VALUES"
              + " (?,?,?,'Demo registrar',?,?,'MANUAL')",
          UUID.randomUUID().toString(),
          w,
          names[i],
          Store.today().plusDays(days[i]),
          i != 0);
  }
}
