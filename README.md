# HMCTS Audit Annotation Starter (Spring Boot 4, Java 25)

A drop-in Spring Boot **starter** that audits REST endpoints via controller annotations,
publishing structured audit events to **ActiveMQ Artemis**.

Endpoints are opted in with `@AuditDetail` and opted out with `@AuditExclude`.
Unannotated endpoints are **blocked by default** (403). The `X-Correlation-ID` header is
required on every audited request.

---

## Key Features

- **Annotation-driven**: no OpenAPI spec, no path-param extraction config.
- **Zero component scanning**: all beans created via a single `@AutoConfiguration`.
- **Blocks by default**: unannotated endpoints return 403 until explicitly annotated.
- **MDC-aware**: domain IDs (`materialId`, `caseId`, etc.) are read from MDC at response time.
- **Active–Passive Artemis HA** support.
- **SSL & non-SSL** Artemis connections.
- **Fully externalised connection tuning**.

---

## Building Locally

```bash
./gradlew clean build
```

The jar is produced at `build/libs/cp-audit-springboot-annotations-<version>.jar`.

---

## CI Pipeline & Publishing

Publishing to Azure Artifacts is handled entirely by CI — you do not need ADO credentials locally.

### On every push / PR to `main`

The `ci-draft.yml` workflow triggers `ci-build-publish.yml` which runs three jobs in sequence:

| Job | What it does |
|---|---|
| **Artefact-Version** | Generates a draft version number via `hmcts/artefact-version-action` |
| **Build** | Runs `./gradlew build`, uploads the jar as a GitHub Actions artifact |
| **Provider-Deploy** | Runs `./gradlew publish` using `AZURE_DEVOPS_ARTIFACT_USERNAME` and `AZURE_DEVOPS_ARTIFACT_TOKEN` from GitHub repo secrets — publishes to Azure Artifacts |

### On GitHub Release (published)

The `ci-released.yml` workflow runs the same jobs but with `is_release: true`, which produces a fixed release version number rather than a draft/snapshot.

### Where it publishes

```
https://pkgs.dev.azure.com/hmcts/Artifacts/_packaging/hmcts-lib/maven/v1
```

Group: `uk.gov.hmcts.cp` · Artifact: `cp-audit-springboot-annotations`

> The `AZURE_DEVOPS_ARTIFACT_USERNAME` and `AZURE_DEVOPS_ARTIFACT_TOKEN` secrets must be configured
> in the GitHub repository settings for publishing to succeed.

---

## Getting Started

### 1) Add the dependency

```gradle
dependencies {
    implementation 'uk.gov.hmcts.cp:cp-audit-springboot-annotations:1.0.0'
}
```

### 2) Annotate your controllers

```java
@RestController
@RequestMapping("/client-subscriptions")
public class DocumentController {

    @GetMapping("/{clientSubscriptionId}/documents/{documentId}")
    @AuditDetail(
        eventName = "hearing-results-document.get-document",
        action    = "Download",
        pathParams = { "clientSubscriptionId", "documentId" }
    )
    public ResponseEntity<byte[]> getDocument(...) { ... }

    @GetMapping("/health")
    @AuditExclude
    public ResponseEntity<String> health() { ... }
}
```

### 3) Set the `X-Correlation-ID` header

Every audited request must carry `X-Correlation-ID` (typically set upstream by a tracing filter).
Requests without it are blocked with 403.

### 4) Populate MDC for domain IDs (optional)

Domain-specific IDs are read from MDC on the **response** event. Set them in your service layer:

```java
MDC.put(AuditMdcKeys.MATERIAL_ID,       materialId.toString());
MDC.put(AuditMdcKeys.CASE_ID,           caseId.toString());
MDC.put(AuditMdcKeys.HEARING_ID,        hearingId.toString());
MDC.put(AuditMdcKeys.COURT_DOCUMENT_ID, courtDocumentId.toString());
```

### 5) Minimal configuration

```yaml
cp:
  audit:
    hosts:
      - artemis-primary.internal
      - artemis-secondary.internal   # optional (HA)
    port: 61616
    user: ${ARTEMIS_USER}
    password: ${ARTEMIS_PASSWORD}
    ssl-enabled: false

material-client:
  cjscppuid: ${SYSTEM_USER_ID}       # platform UUID for the authenticated system user
```

---

## How It Works

`ArtemisAuditAutoConfiguration` creates:

- `ActiveMQConnectionFactory` with HA URL and property-driven timeouts/retries.
- `JmsTemplate` (topic mode, persistent delivery).
- `ObjectMapper` with JavaTimeModule.
- `AuditDecisionService` — evaluates `@AuditDetail` / `@AuditExclude` and checks `X-Correlation-ID`.
- `AuditPayloadGenerationService` — builds the structured JSON payload from the annotation and MDC.
- `AuditSenderService` — publishes to `jms.topic.auditing.event`.
- `AuditService` — orchestrates decision → payload → send.
- `AuditFilter` — resolves the Spring MVC handler method and delegates to `AuditService`.

**Decision logic per request:**

| Condition | Outcome |
|---|---|
| Method/class annotated `@AuditExclude` | Pass through, no audit |
| Method/class annotated `@AuditDetail`, `X-Correlation-ID` present | Audit request + response |
| Method/class annotated `@AuditDetail`, `X-Correlation-ID` missing | 403 |
| No annotation | 403 |

---

## `@AuditDetail` Reference

| Attribute | Default | Purpose |
|---|---|---|
| `eventName` | *(required)* | Event name in the audit payload (e.g. `"my-service.get-item"`). |
| `origin` | `"hearing-results-document"` | Service identifier in the audit envelope. |
| `component` | `"QUERY_API"` | Component identifier in the audit envelope. |
| `action` | `"View"` | Action label (e.g. `"Download"`, `"View"`). |
| `pathParams` | `{}` | Path variable names to extract from the URI and include in the payload. |

---

## Configuration Reference

### `cp.audit.*`

| Property | Type | Default | Purpose |
|---|---|---|---|
| `cp.audit.hosts` | list\<string\> | | One or more Artemis hosts (HA supported). |
| `cp.audit.port` | int | `61616` | Broker port. |
| `cp.audit.user` | string | | Username. |
| `cp.audit.password` | string | | Password. |
| `cp.audit.ssl-enabled` | boolean | `false` | Enable TLS. |
| `cp.audit.truststore` | path | | JKS truststore path (TLS only). |
| `cp.audit.truststore-password` | string | | JKS truststore password (TLS only). |
| `cp.audit.enabled` | boolean | `true` | Disable the entire auto-configuration. |

### `cp.audit.jms.*`

| Property | Default |
|---|---|
| `reconnect-attempts` | `-1` (infinite) |
| `initial-connect-attempts` | `10` |
| `retry-interval-ms` | `2000` |
| `retry-multiplier` | `1.5` |
| `max-retry-interval-ms` | `30000` |
| `connection-ttl-ms` | `60000` |
| `call-timeout-ms` | `15000` |

---

## Testing Guidance

Mock `AuditSenderService` to verify audit events without a real broker:

```java
@SpringBootTest
@AutoConfigureMockMvc
class MyControllerAuditTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuditSenderService auditSenderService;

    @Test
    void annotated_endpoint_should_produce_two_audit_events() throws Exception {
        mockMvc.perform(get("/my-endpoint/123")
                .header("X-Correlation-ID", "test-corr-id"))
                .andExpect(status().isOk());

        verify(auditSenderService, times(2)).send(any());
    }
}
```

---

## SSL & HA

The starter builds a single HA connection URL:

```
tcp://brokerA:61617?sslEnabled=true&trustStorePath=/opt/trust.jks&...,
tcp://brokerB:61617?sslEnabled=true&trustStorePath=/opt/trust.jks&...
```

All parameters (SSL paths, retries, TTL) come from `cp.audit.*` properties.

---

## License

MIT — see [LICENSE](LICENSE).
