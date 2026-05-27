# Haskell Derivation Service — Integration Guide

> Companion document to the main [README](./README.md). This file is the single
> source of truth for everything related to the **external Haskell derivation
> service** that X-Men can optionally call to make derivability decisions for
> the **Forget** mutation.

---

## Table of Contents

1. [Overview](#1-overview)
2. [When (and why) you need it](#2-when-and-why-you-need-it)
3. [Architecture](#3-architecture)
4. [Setup & Configuration](#4-setup--configuration)
   1. [Service prerequisites](#41-service-prerequisites)
   2. [Spring configuration](#42-spring-configuration)
   3. [Environment variable overrides](#43-environment-variable-overrides)
   4. [Starting the Haskell service](#44-starting-the-haskell-service)
   5. [Health check](#45-health-check)
5. [How X-Men calls the service](#5-how-x-men-calls-the-service)
   1. [Conversion pipeline](#51-conversion-pipeline)
   2. [Example: SPTHY → Haskell input](#52-example-spthy--haskell-input)
   3. [Console output you should expect](#53-console-output-you-should-expect)
6. [Usage from X-Men](#6-usage-from-x-men)
   1. [Per-endpoint activation](#61-per-endpoint-activation)
   2. [Combined endpoint activation](#62-combined-endpoint-activation)
   3. [Postman](#63-postman)
   4. [Disabling Haskell (default)](#64-disabling-haskell-default)
7. [Direct calls to the Derivation Service](#7-direct-calls-to-the-derivation-service)
8. [Operational notes](#8-operational-notes)
   1. [Networking in Docker / Compose](#81-networking-in-docker--compose)
   2. [Timeouts and retries](#82-timeouts-and-retries)
   3. [Failure semantics](#83-failure-semantics)
9. [Troubleshooting](#9-troubleshooting)
10. [Glossary](#10-glossary)

---

## 1. Overview

X-Men ships with a **built-in Java derivation engine** that decides, for every
*Forget* mutation, whether the message the human is about to send can still be
constructed from their remaining knowledge. That engine is fast and works for
the vast majority of ceremonies modelled in Tamarin.

For more complex protocols — particularly those that mix custom function
symbols, equational reasoning, or large initial knowledge — X-Men can delegate
the derivation step to an **external Haskell microservice**. The Haskell
service runs in its own process, exposes an HTTP API, and is consulted by
X-Men **per request** through a single HTTP header (`Haskell-Activate: true`).

The integration is:

- **Optional.** If the header is absent, X-Men never contacts the service.
- **Stateless.** Each derivation is an isolated `POST /derive` call.
- **Header-driven.** You toggle it per request, not per deployment.
- **Side-effect-free on the request body.** You upload the same `.spthy` file
  as for any other mutation endpoint — X-Men handles the conversion internally.

---

## 2. When (and why) you need it

You need the Haskell service when **all** of the following are true:

1. You are generating **Forget** mutations (`/api/forget/mutations`, or the
   combined endpoint with `Forget-Mutation: true`).
2. Your ceremony uses **complex term structures** — typically user-defined
   functions, nested encryptions over opaque keys, or initial knowledge that
   the Java DY engine can't fully reconstruct.
3. You want X-Men's *Forget* decisions to be made by the Haskell derivation
   tree analysis rather than the Java fallback.

For simple ceremonies (the Bank Login example shipped with the repo, the
Oyster card example, classroom-sized protocols) you do **not** need the
Haskell service — the Java engine handles them.

---

## 3. Architecture

```
┌──────────────────────────────────────────┐         ┌────────────────────────────┐
│           X-Men (Spring Boot)            │         │   Haskell Derivation Svc   │
│                                          │         │                            │
│   POST /api/forget/mutations             │         │   POST /derive             │
│   Header: Haskell-Activate: true         │  HTTP   │   Content-Type: text/plain │
│   Body : multipart .spthy                ├────────►│   Body: converted model    │
│                                          │         │                            │
│   HybridDerivationService                │  ◄──────┤   derivation tree (text)   │
│   ├── (off) DerivationServiceImpl  (Java)│         │                            │
│   └── (on)  HaskellDerivationFetcher     │         └────────────────────────────┘
└──────────────────────────────────────────┘
                  │
                  ▼
        ZIP of mutated .m files
```

Key Java collaborators on the X-Men side:

| Component | Responsibility |
| --- | --- |
| `HybridDerivationService` | Router. Switches between Java and Haskell at runtime based on the request header. |
| `HaskellFormatConverter` | Turns parsed Tamarin rules into the plain-text format the Haskell service expects. |
| `HaskellDerivationFetcher` | Performs the HTTP call to `/derive`, parses the response, and surfaces derivability. |
| `DerivationTreePrinter` | Pretty-prints the returned derivation tree to the X-Men console for inspection. |
| `ForgetMutationStrategy` | Consumes the derivability verdict and decides between *keep send*, *substitute*, *skip + neglect*. |

---

## 4. Setup & Configuration

### 4.1 Service prerequisites

The Haskell derivation microservice is maintained in its own repository.
Build and run it according to its own README. The contract X-Men relies on is:

| Property | Value |
| --- | --- |
| Default port | `9091` (configurable) |
| Health endpoint | `GET /health` |
| Derivation endpoint | `POST /derive` |
| Request body | `Content-Type: text/plain`, plain-text protocol description |
| Response body | Plain-text derivation tree |

### 4.2 Spring configuration

`src/main/resources/application.yaml` declares the URL:

```yaml
derivation:
  service:
    url: ${DERIVATION_SERVICE_URL:http://localhost:9091}
```

The `${VAR:default}` syntax means: take the value from the environment if
present, otherwise fall back to `http://localhost:9091`.

### 4.3 Environment variable overrides

To point X-Men at a non-default host or port:

```bash
# Linux / macOS
export DERIVATION_SERVICE_URL=http://derivation:9091
mvn spring-boot:run

# Windows (PowerShell)
$env:DERIVATION_SERVICE_URL = "http://derivation:9091"
mvn spring-boot:run

# Docker
docker run -e DERIVATION_SERVICE_URL=http://derivation:9091 -p 8081:8081 x-men:latest
```

You can also drop the variable into a local `.env` file at the project root
(see [ENVIRONMENT_CONFIG.md](./ENVIRONMENT_CONFIG.md)).

### 4.4 Starting the Haskell service

From the Derivation-Service repository:

```bash
# Typical invocation – follow the upstream README for specifics
./derivation-service
# or, if you containerize it
docker run -p 9091:9091 derivation-service:latest
```

### 4.5 Health check

Before running an X-Men request with `Haskell-Activate: true`, verify the
service is up:

```bash
curl -i http://localhost:9091/health
```

You should receive an HTTP 200 with a small JSON or text body confirming the
service is alive.

---

## 5. How X-Men calls the service

### 5.1 Conversion pipeline

Each Forget-with-Haskell request goes through this pipeline inside X-Men:

```
1. Parse the uploaded .spthy into the X-Men rule model.
2. Run the Forget strategy until a derivability check is required for m2.
3. HybridDerivationService inspects the per-request flag and selects Haskell.
4. HaskellFormatConverter renders the relevant slice of the model as plain text.
5. HaskellDerivationFetcher issues `POST /derive` and waits for the response.
6. DerivationTreePrinter logs the returned tree to the X-Men console.
7. The textual response is parsed to a boolean derivability verdict.
8. ForgetMutationStrategy uses the verdict:
       derivable          → keep the original send
       not derivable      → attempt substitution → fall back to skip + neglect
```

### 5.2 Example: SPTHY → Haskell input

Given a fragment of input:

```tamarin
rule setup:
  [Fr(~kS)]
  --[OnlyOnce(), Roles($Client,$S,$D)]->
  [ State($S,'1',<~kS,$D>)
  , State($Client,'1',<$S>)
  ]

rule H_1:
  [ State($Client,'1',<$S>) ]
  -->
  [ SndS($Client,$S,journey) ]
```

The converter produces something equivalent to:

```
PROTOCOL: CoachService

INITIAL_KNOWLEDGE:
  Client
  S
  D
  pub(a)
  pub(b)

MESSAGES:
  M1: from Client to S: journey
  M2: from S to Client: solution

GOAL: derive(shared_key)
```

This plain-text payload is sent unchanged in the HTTP request body.

### 5.3 Console output you should expect

When the Haskell path is active, X-Men prints a clearly delimited block to
its console:

```
================================================================================
HASKELL DERIVATION TREE FOR: CoachService
Target: (ticket, encTicket)
================================================================================

========================================
       DERIVATION TREE ANALYSIS
========================================

Initial intruder knowledge
l_0 -> Client
l_1 -> S
l_2 -> D
l_3 -> Pub(privk_a)

Receiving first message from Client and analyzing
l_4 -> <date, dtime, from, to>

Can the intruder derive session key?
[Aenc(l_3, l_4)]

========================================

================================================================================

Haskell derivation result: target is DERIVABLE
Target IS derivable from knowledge. Skipping mutation.
```

If you don't see this block when you expect to, double-check the request
header spelling and the service URL — the most common cause is a silent
fallback to the Java engine.

---

## 6. Usage from X-Men

### 6.1 Per-endpoint activation

The dedicated Forget endpoint:

```bash
# Without Haskell (Java fallback)
curl -X POST "http://localhost:8081/api/forget/mutations" \
  -F "file=@Bank_revised_new.spthy" \
  -o forget_mutations_java.zip

# With Haskell
curl -X POST "http://localhost:8081/api/forget/mutations" \
  -H "Haskell-Activate: true" \
  -F "file=@Bank_revised_new.spthy" \
  -o forget_mutations_haskell.zip
```

### 6.2 Combined endpoint activation

`POST /api/generateMutations` runs any combination of mutations selected via
headers. Forget + Haskell:

```bash
curl -X POST "http://localhost:8081/api/generateMutations" \
  -H "Forget-Mutation: true" \
  -H "Haskell-Activate: true" \
  -F "file=@CoachService.spthy" \
  -o mutants_forget_haskell.zip
```

You can stack other mutation headers (`Skip-Send: true`, `Add-Mutation: true`,
…) alongside `Forget-Mutation: true`; the `Haskell-Activate` header still only
affects the Forget step.

### 6.3 Postman

1. Open the request **Forget Mutation (Complex Input)** inside the imported
   X-Men collection.
2. Switch to the **Headers** tab.
3. Ensure `Haskell-Activate` is present and set to `true`.
4. Click **Send**.
5. Save the streamed response as a `.zip` — the file name is up to you.

### 6.4 Disabling Haskell (default)

Simply **omit** the `Haskell-Activate` header (or set it to `false`). X-Men
falls back to its built-in Java derivation engine and never touches the
external service.

---

## 7. Direct calls to the Derivation Service

You can talk to the Haskell service directly without going through X-Men. This
is useful for testing the service in isolation.

```bash
# Health
curl http://localhost:9091/health

# Sample derivation
curl -X POST "http://localhost:9091/derive" \
  -H "Content-Type: text/plain" \
  -d "derive ex_f1 ex_r"
```

For the exact body grammar accepted by `/derive`, refer to the upstream
Derivation-Service repository — X-Men's `HaskellFormatConverter` produces a
superset of valid inputs but is not the canonical specification.

---

## 8. Operational notes

### 8.1 Networking in Docker / Compose

When X-Men runs inside a container, `localhost` resolves to the container,
not the host. Either:

- Run both services on a shared Compose network (recommended) and use the
  service name as host:

  ```yaml
  services:
    xmen:
      image: x-men:latest
      environment:
        DERIVATION_SERVICE_URL: http://derivation:9091

    derivation:
      image: derivation-service:latest
      ports: ["9091:9091"]
  ```

- Or run the Haskell service on the host and pass
  `DERIVATION_SERVICE_URL=http://host.docker.internal:9091` on platforms
  where that alias is supported.

A reference `docker-compose.yml` shipped with X-Men already wires this up.

### 8.2 Timeouts and retries

X-Men issues a single synchronous HTTP call per derivation. If the service is
slow to respond:

- Increase the relevant client timeout in `application.yaml` (or via env vars
  if exposed). Long-running derivations on large protocols can take seconds.
- Consider running the Haskell service on the same host as X-Men to minimize
  latency.

### 8.3 Failure semantics

If the call to `/derive` fails (network error, non-2xx response, malformed
body), X-Men logs the failure and treats it as **derivability undetermined**.
The Forget strategy then proceeds along its fallback path:

1. Attempt textual substitution with a type-compatible replacement.
2. If no replacement is available, *Skip* the send and *Neglect* internal
   actions that used the forgotten term — exactly as in the offline path.

This means a missing Haskell service degrades gracefully: you'll still get
mutated `.m` files, just based on the Java fallback.

---

## 9. Troubleshooting

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| Java derivation always runs even with the header set. | Header name mis-spelled or sent as `false`. | Verify `Haskell-Activate: true` exactly. |
| HTTP 502 / connection refused. | Service not running or wrong URL. | `curl http://localhost:9091/health`; fix `DERIVATION_SERVICE_URL`. |
| Derivation tree block missing from console. | Request never reached the Haskell branch. | Check X-Men logs at INFO level for the `HybridDerivationService` switch. |
| Mutations look identical with/without Haskell. | Ceremony is simple enough that Java + Haskell agree. | Expected. The header changes *how* derivability is decided, not the input. |
| Very slow requests. | Network latency or large model. | Co-locate the services; raise client timeouts; trim initial knowledge. |
| "fail to validate distribution" on `mvn` build. | Maven Wrapper SHA mismatch — unrelated. | See main README's troubleshooting section. |

---

## 10. Glossary

- **Forget mutation.** The mutation pattern this paper formalizes: the human
  participant cannot use a piece of their knowledge during message
  construction. See the project paper for the full definition.
- **Derivability.** Whether a target term can be reconstructed from a given
  set of knowledge under Dolev–Yao inference rules.
- **DY rules.** Standard Dolev–Yao term-rewriting rules for pairs, symmetric
  and asymmetric encryption, projection, etc.
- **Hybrid derivation.** The runtime dispatch pattern X-Men uses: pick the
  Java engine by default, switch to the Haskell service when the header is
  present.
- **Derivation tree.** The proof-like artefact produced by the Haskell
  service: a tree of inference steps showing how (or whether) the target is
  obtained from the initial knowledge.

---

> Back to the main project README: [`README.md`](./README.md)
