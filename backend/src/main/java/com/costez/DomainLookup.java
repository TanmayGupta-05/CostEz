package com.costez;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DomainLookup {
  final ObjectMapper json;
  final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public DomainLookup(ObjectMapper json) {
    this.json = json;
  }

  public LocalDate expiry(String domain) {
    String tld = domain.substring(domain.lastIndexOf('.') + 1);
    String base =
        Map.of(
                "com",
                "https://rdap.verisign.com/com/v1/domain/",
                "net",
                "https://rdap.verisign.com/net/v1/domain/",
                "org",
                "https://rdap.publicinterestregistry.org/rdap/domain/")
            .get(tld);
    if (base == null)
      throw new ApiError(
          422,
          "Registry lookup is currently supported for .com, .net and .org. Enter this domain's"
              + " expiry manually.");
    try {
      var res =
          http.send(
              HttpRequest.newBuilder(URI.create(base + domain))
                  .timeout(Duration.ofSeconds(10))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() != 200)
        throw new ApiError(422, "Registry lookup unavailable. Keep the manual expiry date.");
      for (var event : json.readTree(res.body()).path("events"))
        if ("expiration".equals(event.path("eventAction").asText()))
          return Instant.parse(event.path("eventDate").asText())
              .atZone(ZoneOffset.UTC)
              .toLocalDate();
      throw new ApiError(422, "Registry does not expose expiry. Enter it manually.");
    } catch (ApiError e) {
      throw e;
    } catch (Exception e) {
      throw new ApiError(
          502, "Could not reach the public registry. Try again or use manual entry.");
    }
  }
}
