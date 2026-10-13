# Verification

The frontend compiles with strict TypeScript and produces a Vite production bundle. Three Vitest tests cover monthly paise normalization, cross-category capability overlap and status precedence. Backend integration tests cover authentication/CSRF/session cookies, tenant isolation, CSV errors/idempotency, deterministic report creation, reminder queue deduplication, and rejection of invented AI numbers.

Six backend tests passed (five API integration tests and one AES-GCM authentication/randomization test). Three frontend tests passed. The production frontend dependency audit reports no known vulnerabilities; this is not a complete application security audit.

`node scripts/smoke.mjs` passed against the executable application using the persistent H2 database and then against PostgreSQL 17.6. The live test covers serving the built frontend, actual login and session cookies, CSRF, subscription/domain creation-edit-deletion, optimistic locking, charge history, public single-use owner responses, CSV file deduplication and invalid dates, deterministic reports, reminder deduplication and logout. PostgreSQL Flyway schema creation and Spring Session storage succeeded. New queued email bodies are encrypted with AES-256-GCM.

No live OpenAI or SMTP credentials were supplied, so their successful external delivery/generation is not claimed. The AI no-key fallback, numerical validator and mail payload encryption are tested. Docker Engine is absent locally; Docker/Compose and Caddy configuration are supplied but their build/HTTPS deployment has not been executed here. Visual browser review was attempted using Computer Use; access to Brave was rejected as unapproved, so browser visual/layout verification is not claimed. No public hosting has been provisioned.
