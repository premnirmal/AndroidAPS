# OSAID key-manager: accepting Android (hardware key attestation)

**Status:** proposal from the AndroidAPS side, for the Nightscout / OSAID key-manager maintainers.
**Goal:** let a self-built Android app obtain an Omnipod 5 certificate with the same
assurance the iOS apps get today, by proving genuine device hardware.
**Scope:** server-side only. The Android client already exists (see the reference
implementation note at the end); this document says what the server must accept and check.

This mirrors the existing iOS flow (`O5AppAttestService.swift`) step for step so the server
change stays small. The only new work is an Android verification path parallel to the App
Attest one.

---

## 1. Why this works, and how it compares to iOS

On iOS the key-manager trusts **Apple App Attest**: proof that a genuine, uncompromised Apple
device is running the app. The Android equivalent is **hardware key attestation**. The app
asks the phone's secure hardware to generate a key pair and hand back an X.509 **certificate
chain**, signed by the hardware and rooted in **Google's attestation root**, that states:

- the key lives in secure hardware (StrongBox or the TEE), and
- it was **generated there**, not imported, and
- the device's boot state (bootloader locked, verified-boot state, OS version, patch level), and
- a **challenge** chosen by the server, embedded in the leaf certificate so the attestation
  cannot be replayed.

The server verifies that chain the way it verifies an App Attest object.

What this proves is **at least** what iOS proves, and on a StrongBox device strictly more:
"genuine hardware, uncompromised, key held in a dedicated secure chip, generated there."

**Honest limitation, identical to iOS today:** this proves the device is real. It does **not**,
by itself, stop one device requesting more than one certificate. See §6.

---

## 2. Endpoints

Parallel to the iOS endpoints (`/api/status/ios`, `/api/auth/ios/challenge`, `/api/o5/keypair`).
Names below are a proposal; the client reads them from one constant and can change easily.

| Step | Method | Path | Purpose |
|------|--------|------|---------|
| 1 | POST | `/api/status/android` | Is the service open? Is it token-gated? |
| 2 | POST | `/api/auth/android/challenge` | Get a fresh, single-use challenge |
| 3 | POST | `/api/o5/keypair/android` | Send the attestation, receive the credential |

### 2.1 `POST /api/status/android`

Request body:
```json
{ "omnipodkit_api_version": "1.1" }
```
Response, service open:
```json
{ "available": true }
```
Response, gated behind a setup token (client will then prompt the user and retry with a
`Authorization: Bearer <token>` header on the following calls, exactly as iOS does):
```json
{ "available": false, "authSupported": true }
```
Response, closed:
```json
{ "available": false, "message": "Android is not supported yet." }
```
The client shows `message` verbatim. **Until Android is live, returning this is the correct
response** — the client already handles it gracefully.

### 2.2 `POST /api/auth/android/challenge`

Request body: `{}` (plus the bearer header if token-gated).
Response:
```json
{ "challenge": "<base64url, >= 16 random bytes>" }
```
The challenge **must** be server-generated, single-use, short-lived (a few minutes), and bound
to this pending request. The client hashes nothing and passes the raw challenge bytes into the
key's attestation (see §3), then returns the same challenge string in step 3.

### 2.3 `POST /api/o5/keypair/android`

Request body:
```json
{
  "challenge": "<the challenge string from step 2>",
  "app_id": "info.nightscout.androidaps",
  "security_level": "StrongBox",
  "signing_cert_sha256": "<64 hex chars>",
  "attestation_chain": ["<base64 DER cert>", "..."]
}
```
- `attestation_chain` is **leaf-first**: element 0 is the attested key's certificate, the last
  element is (or chains to) Google's root. Each element is base64-encoded DER.
- `security_level` is the client's own reading (`StrongBox` or `TEE`); treat it as a hint only
  and derive the real value from the certificate (§5), never trust this field.
- `signing_cert_sha256` is the SHA-256 of the app's signing certificate, lowercase hex. It is
  the app's stable identity (see §6.1). Like `security_level`, treat it as a hint only: verify
  it equals the signing-cert digest inside the chain's `attestationApplicationId` (§5.3), and
  never trust the sent field on its own. The field may be missing if the client could not read
  it; fall back to the value from the chain.

Response on success is the **existing `o5keypair` JSON** — identical shape to the iOS response,
so the client reuses the same parser:
```json
{
  "controllerId": "...",
  "privateKey": "<hex>",
  "publicKey": "<hex>",
  "intermediateCA": "<base64>",
  "tlsCertificate": "<base64>"
}
```
Response on refusal: any non-2xx with `{ "message": "..." }` (or `{ "error": "..." }`); the
client shows the text.

---

## 3. What the client puts in the attestation (for context)

The client generates an EC P-256 key in the Android Keystore with the server challenge as the
attestation challenge, trying StrongBox first and falling back to the TEE:

```kotlin
KeyGenParameterSpec.Builder(alias, PURPOSE_SIGN)
    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
    .setDigests(DIGEST_SHA256)
    .setAttestationChallenge(challengeBytes)   // raw bytes of the base64url challenge
    .setIsStrongBoxBacked(true)                // dropped on fallback
    .build()
```
The certificate chain returned by `KeyStore.getCertificateChain(alias)` is what goes in
`attestation_chain`. The key is deleted immediately after; it exists only to carry the
attestation for this one request.

---

## 4. Verification overview (the App Attest parallel)

| App Attest step (iOS) | Android equivalent (this spec) |
|---|---|
| Verify the attestation's certificate chain to Apple's App Attest root | Verify `attestation_chain` to **Google's hardware attestation root** (§5.1) |
| Check the nonce/clientDataHash = your challenge | Check the **attestation challenge** in the leaf = the challenge you issued (§5.2) |
| Check the app id (Team ID + bundle ID) | Check `attestationApplicationId` = your package name (§5.3) |
| Trust that it ran on genuine hardware | Check **security level, key origin, boot state** in the leaf (§5.4) |
| (risk metric / DeviceCheck for abuse) | See §6 — the parts that do **not** have a self-build-friendly equivalent |

Use Google's maintained library rather than hand-parsing:
**`https://github.com/android/keyattestation`** (Kotlin; server-side). The older
`google/android-key-attestation` is sample code only.

---

## 5. Verification steps, with a real worked example

Every value below is from a real attestation produced by this client on a Pixel-class phone
(Android 16, August 2026 patch). The server must perform all of these.

### 5.1 Chain and root

- Verify each certificate is signed by the next, up to the root.
- The root must be **Google's hardware attestation root**. In this sample:
  - root is self-signed, subject/issuer `serialNumber=f92009e853b6b045`
  - root public-key SHA-256 = `feb2ea7551ee316ed4bb443c8293b884dbfdea40b603ee3e4f4a897e4580fbae`
  Pin against Google's published roots. Note Google is rotating to an **ECDSA P-384 root** in
  2026 under Remote Key Provisioning; accept both the classic RSA root and the new P-384 root.
- Check the leaf against Google's **revocation list**:
  `https://android.googleapis.com/attestation/status` (cache it; it is signed and updated).
  Reject a revoked or suspended serial.

### 5.2 Challenge

- Parse the key-attestation extension (**OID `1.3.6.1.4.1.11129.2.1.17`**) in the **leaf**.
- Its `attestationChallenge` field must equal the exact bytes of the challenge you issued in
  step 2. In this sample:
  `0d70945556ec521520946498295f28932b785e5f3cac21324878d387a30dd52c` (32 bytes), which is the
  base64url `DXCUVVbsUhUglGSYKV8okyt4Xl88rCEySHjTh6MN1Sw` the server issued.
- Enforce single use and expiry server-side. This is what stops replay.

### 5.3 App identity

- In the extension's `softwareEnforced` block, `attestationApplicationId` must contain the
  expected package name. In this sample: **`info.nightscout.androidaps`**.
- That same field also carries the **SHA-256 of the app's signing certificate**. Do not use it
  to allowlist specific builds (it varies per self-builder), but do **read it** — it is the
  stable per-builder identity the pool should key on. See §6.1. Confirm it equals the
  `signing_cert_sha256` the client sent, and use the value from here (the chain) as the trusted
  one.

### 5.4 Hardware and boot state (all from the leaf's `teeEnforced` block)

Require, in this sample all present:

| Field | Required value | Sample |
|---|---|---|
| `attestationSecurityLevel` | `StrongBox (2)` or `TrustedEnvironment (1)` — **not** `Software (0)` | StrongBox |
| `keymasterSecurityLevel` / keyMint | same tier as above | StrongBox |
| `origin` | **`GENERATED (0)`** — reject `IMPORTED` | GENERATED |
| `purpose` | includes `SIGN (2)` | SIGN |
| `rootOfTrust.deviceLocked` | policy choice, recommend **true** | true |
| `rootOfTrust.verifiedBootState` | policy choice, recommend **Verified/green (0)** | Verified |
| `osVersion` / `osPatchLevel` | optional minimum-floor policy | 16.0.0 / 2026-08 |

**The `deviceLocked` / `verifiedBootState` choice is the key policy call.** Requiring
`Verified` + locked is the strongest, and matches what iOS effectively guarantees — but it
**excludes GrapheneOS, custom ROMs, and rooted phones**, which are common in this community.
Options:

- **Strict:** require locked + Verified. Highest assurance, excludes custom-ROM users.
- **Lenient:** accept `SelfSigned/yellow` (user's own verified-boot key) and/or unlocked, but
  still require StrongBox/TEE + GENERATED origin. Lets custom-ROM users in while still proving
  real secure hardware.

Recommend making it configurable, and starting lenient with a logged warning, since excluding
custom ROMs would turn away a meaningful share of AndroidAPS users for a benefit (boot-state
assurance) that iOS gets only because iOS has no custom-ROM equivalent.

---

## 6. The per-device limit — what does and does not carry over

The per-device limit is structurally the same on both platforms. On iOS, App Attest keys are
**per install** (a reinstall makes a new key), so the attestation alone does not tie a request
to a device that has asked before. Apple's genuinely per-device tools — DeviceCheck bits, the
App Attest risk metric — both require a request signed by the **app's own Apple
developer-account key**, which a self-build's developer does not have any special claim to on
behalf of the pool. So the pool is protected today mainly by the certificate supply and by
rate limiting, not by a hard per-device cap.

On Android the same holds, for the same structural reason. Hardware key attestation is
deliberately **unlinkable** (per-app keys, rotated ~2 months), so it cannot say "this is the
same phone as before." Google's only per-device memory is **Play Integrity device recall**,
which requires the app to be **installed from Google Play** (a self-built app reads as
`UNLICENSED` and cannot use it).

Practical options, in increasing strength:

1. **Parity with iOS (recommended first step):** accept the attestation as proof of real
   hardware, and rate-limit per source / per challenge issuance as today. No worse than iOS.
2. **Bind the certificate to the device:** issue the credential **wrapped to the attested
   hardware key** (`WrappedKeyEntry`, Android 9+), so the private key can never leave the phone
   it was issued to. Stops a downloaded certificate being copied or sold. Does not cap count,
   but removes the incentive to farm. This is a client + server change and can come later.
3. **True per-device cap:** would need a Play-published helper app owned by the OSAID operator
   (so device recall works), which is a larger undertaking. Documented for completeness, not
   proposed now.

### 6.1 A stable per-builder identity: the app signing certificate

There is one identity a self-built Android app already has that **is** stable and singular in
normal use: the **signing certificate** the builder makes at first build. It is a better anchor
than any of the above for the honest majority, and it is worth using.

- **Where it comes from.** Every self-builder creates a keystore for their first build and signs
  the APK with it. Android then **requires every future update to be signed by that same
  certificate** — an update signed by a different key is refused, and the only way to install one
  is to uninstall first, which wipes all app data. So for anyone who keeps their app updatable
  and keeps their data, the signing certificate is effectively **one per install, for the life
  of that install**. Making a second identity is not "run a command"; it means throwing the
  whole setup away.
- **It is already in the attestation.** The chain's `attestationApplicationId` (§5.3) carries the
  SHA-256 of the signing certificate. So the server can read it from evidence it already
  verifies — the client also sends it as `signing_cert_sha256`, but that is only a hint; use the
  value from the chain.
- **What to do with it.** Treat the signing-cert digest as the pool's **"account"**: issue at
  most one active credential per digest, and use it as the key for rate-limiting and revocation.
  This gives the DIY pool the per-identity accountability a real Omnipod account gives Insulet,
  reconstructed from something the builder already has and cannot casually duplicate.

**Honest limits.** This is a good identifier, not a hard cap. It does not stop a determined
person with several **real** phones (one install and one signing cert each), and it cannot be
made scarce — anyone can generate another keystore. What actually taxes multi-device farming is
the genuine-hardware requirement of the attestation itself (§5.4), not the signing key. Also, a
user who **loses** their keystore is forced to uninstall/reinstall and will show up with a new
certificate, so the per-cert cap must be **soft** (allow a genuine new identity) — which an
abuser can use too. And `attestationApplicationId` is usually **software-enforced**, so its trust
rides on verified boot: strong on a locked device, weaker on an unlocked custom ROM (§5.4). Net:
use it as the primary accountability handle for the honest population, layered on the
genuine-hardware proof, not as a wall.

---

## 7. Reference implementation

The Android client is in this pull request, in the `omnipod5` module:
`pump/omnipod/omnipod5/src/main/kotlin/app/aaps/pump/omnipod/omnipod5/ui/O5KeyAttestationService.kt`.
It has a **"Create test attestation"** action that produces a real attestation chain from a
device with no network call — use it to generate test vectors for the server checks above, and
it now also prints the app's `signing_cert_sha256` so you can see the pool identity for a build.
The worked-example values in §5 come from one such sample and verify cleanly end to end
(`openssl verify` to the Google root; challenge, package, StrongBox, GENERATED origin, locked +
Verified all confirmed).

Happy to adjust endpoint names, payload field names, or the boot-state policy to whatever the
key-manager prefers, and to provide more test attestations (StrongBox and TEE, locked and
unlocked) on request.
