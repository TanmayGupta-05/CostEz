package com.costez;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:tests;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "costez.mail-enabled=false"
    })
@AutoConfigureMockMvc
class ApiIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired Store st;

  @Test
  void authenticationAndCsrf() throws Exception {
    mvc.perform(get("/api/subscriptions")).andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/auth/login")
                .param("email", "demo@costez.local")
                .param("password", "CostezDemo2026!"))
        .andExpect(status().isForbidden());
    MvcResult login =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .param("email", "demo@costez.local")
                    .param("password", "CostezDemo2026!"))
            .andExpect(status().isOk())
            .andReturn();
    mvc.perform(get("/api/me").cookie(login.getResponse().getCookies()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workspace").value("Kiran Labs"));
  }

  @Test
  void totalsAndIsolation() throws Exception {
    mvc.perform(get("/api/overview").with(user("demo@costez.local")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.waste").value(693000))
        .andExpect(jsonPath("$.total").value(4354658));
    String email = "isolation@costez.test";
    mvc.perform(
            post("/api/auth/register")
                .with(csrf())
                .contentType("application/json")
                .content(
                    "{\"email\":\""
                        + email
                        + "\",\"password\":\"LongPassword123!\",\"name\":\"Owner\",\"workspace\":\"Other"
                        + " workspace\"}"))
        .andExpect(status().isOk());
    String id =
        st.db.queryForObject("SELECT id FROM subscriptions FETCH FIRST 1 ROW ONLY", String.class);
    mvc.perform(get("/api/subscriptions/" + id).with(user(email))).andExpect(status().isNotFound());
    mvc.perform(delete("/api/subscriptions/" + id).with(user(email)).with(csrf()))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/subscriptions").with(user(email))).andExpect(content().json("[]"));
  }

  @Test
  void importIdempotencyValidationAndReports() throws Exception {
    String csv =
        "name,category,owner,cycle,amount_inr,next_renewal\n"
            + "Imported,Other,Priya,Monthly,100,2026-12-10\n"
            + "Bad,Other,Priya,Monthly,-1,2026-12-10\n";
    var file = new MockMultipartFile("file", "subscriptions.csv", "text/csv", csv.getBytes());
    mvc.perform(
            multipart("/api/imports/csv").file(file).with(user("demo@costez.local")).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imported").value(1))
        .andExpect(jsonPath("$.errors.length()").value(1));
    mvc.perform(
            multipart("/api/imports/csv").file(file).with(user("demo@costez.local")).with(csrf()))
        .andExpect(status().isOk());
    assertEquals(
        1,
        st.db.queryForObject(
            "SELECT COUNT(*) FROM subscriptions WHERE name='Imported'", Integer.class));
    mvc.perform(post("/api/reports").with(user("demo@costez.local")).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.model").value("deterministic"));
  }

  @Test
  void reminderQueueIsIdempotent() throws Exception {
    mvc.perform(post("/api/notifications/run").with(user("demo@costez.local")).with(csrf()))
        .andExpect(status().isOk());
    int before = st.db.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
    mvc.perform(post("/api/notifications/run").with(user("demo@costez.local")).with(csrf()))
        .andExpect(status().isOk());
    assertEquals(before, st.db.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class));
    assertTrue(before > 0);
  }

  @Test
  void numberVerificationRejectsInventedValues() {
    assertTrue(ReportService.validNumbers("Spend INR 100.50", java.util.List.of("INR 100.50")));
    assertFalse(ReportService.validNumbers("Save 75%", java.util.List.of("INR 100.50")));
  }
}
