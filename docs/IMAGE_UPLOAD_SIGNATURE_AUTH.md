# Image Upload Signature Auth

This guide explains how to call the POS image upload and delete APIs when the normal POS/PSGS bearer token is not available or is invalid.

The image APIs accept either:

1. A valid POS/PSGS bearer token, or
2. A timestamped HMAC signature using the configured image upload secret.

## Server Configuration

Set a strong shared secret in the backend environment:

```env
IMAGE_UPLOAD_API_SECRET=<strong-random-secret>
IMAGE_UPLOAD_SIGNATURE_SKEW_SECONDS=300
```

The backend reads those values through:

```properties
app.image-upload.api-secret=${IMAGE_UPLOAD_API_SECRET:}
app.image-upload.signature-skew-seconds=${IMAGE_UPLOAD_SIGNATURE_SKEW_SECONDS:300}
```

`IMAGE_UPLOAD_SIGNATURE_SKEW_SECONDS` is the allowed clock difference between client and server. The default is 300 seconds.

If `IMAGE_UPLOAD_API_SECRET` is empty, unauthenticated signature access is disabled. Requests with a valid bearer token still work.

## Creating The Secret

Generate a random secret once per environment and store it in the deployment secret/env manager.

OpenSSL:

```bash
openssl rand -base64 32
```

PowerShell:

```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Minimum 0 -Maximum 256 }))
```

Do not send this secret in API requests. Clients use it only to calculate `X-Upload-Signature`.

## Required Headers

When bearer auth is not used, send:

| Header | Description |
| --- | --- |
| `X-Upload-Timestamp` | ISO-8601 timestamp with timezone offset. Use GMT+7 / WIB as `+07:00`, for example `2026-05-20T15:05:40+07:00`. Epoch seconds or milliseconds are also accepted for compatibility. |
| `X-Upload-Signature` | Lowercase hex HMAC-SHA256 signature. |

## Canonical String

The `canonical_string` is the exact text that both the client and backend sign.
It makes the signature bound to one HTTP method, one endpoint path, and one timestamp.
If any part is different between client and server, the generated HMAC will be different and the request will be rejected.

Create the signature from this exact one-line format:

```text
HTTP_METHOD|REQUEST_URI_WITH_QUERY|X_UPLOAD_TIMESTAMP
```

This means the string contains three parts separated by the pipe character `|`:

1. The HTTP method, uppercase.
2. The request URI path exactly as sent to the backend, including the context path and query string if present.
3. The same timestamp value sent in the `X-Upload-Timestamp` header.

Rules:

- `HTTP_METHOD` must be uppercase, for example `POST` or `DELETE`.
- `REQUEST_URI_WITH_QUERY` includes the context path and query string exactly as sent to the server.
- For upload without query string: `/NivoraPos/images/upload`
- For delete with query string: `/NivoraPos/images/delete?url=<url-encoded-image-url>`
- Use `|` as the delimiter. Do not use spaces or line breaks.
- Do not include scheme, host, request body, or headers in the canonical string.
- The query string must keep the same encoding and parameter order used in the actual request.
- The timestamp should include the GMT+7 offset as `+07:00`, for example `2026-05-20T15:05:40+07:00`.

Upload canonical string example:

```text
POST|/NivoraPos/images/upload|2026-05-20T15:05:40+07:00
```

Delete canonical string example:

```text
DELETE|/NivoraPos/images/delete?url=https%3A%2F%2Fassets.example.com%2Fnivora-pos-images%2Fimages%2Fproduct%2F2026-05-03%2Fproduct_abc123.jpg|2026-05-20T15:05:40+07:00
```

## Signature Formula

```text
X-Upload-Signature = hex(HMAC_SHA256(IMAGE_UPLOAD_API_SECRET, canonical_string))
```

## Client Examples

### JavaScript

```javascript
const crypto = require("crypto");

const secret = process.env.IMAGE_UPLOAD_API_SECRET;
const timestamp = new Intl.DateTimeFormat("sv-SE", {
  timeZone: "Asia/Jakarta",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hour12: false
}).format(new Date()).replace(" ", "T") + "+07:00";
const path = "/NivoraPos/images/upload";
const canonical = ["POST", path, timestamp].join("|");

const signature = crypto
  .createHmac("sha256", secret)
  .update(canonical)
  .digest("hex");

console.log(timestamp);
console.log(signature);
```

### Kotlin

```kotlin
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun hmacSha256Hex(secret: String, payload: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") {
        "%02x".format(it)
    }
}

val timestamp = OffsetDateTime.now(ZoneOffset.ofHours(7))
    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
val path = "/NivoraPos/images/upload"
val canonical = listOf("POST", path, timestamp).joinToString("|")
val signature = hmacSha256Hex(secret, canonical)
```

## Upload Request

```bash
curl -X POST "https://api.example.com/NivoraPos/images/upload" \
  -H "X-Upload-Timestamp: 2026-05-20T15:05:40+07:00" \
  -H "X-Upload-Signature: <hmac-sha256-hex>" \
  -F "file=@/path/to/product.jpg"
```

## Delete Request

Sign the exact encoded request URI, including the `url` query parameter.

```bash
curl -X DELETE "https://api.example.com/NivoraPos/images/delete?url=https%3A%2F%2Fassets.example.com%2Fnivora-pos-images%2Fimages%2Fproduct%2F2026-05-03%2Fproduct_abc123.jpg" \
  -H "X-Upload-Timestamp: 2026-05-20T15:05:40+07:00" \
  -H "X-Upload-Signature: <hmac-sha256-hex>"
```

## Common Errors

| Response | Meaning |
| --- | --- |
| `missing X-Upload-Timestamp` | Timestamp header was not sent. |
| `missing X-Upload-Signature` | Signature header was not sent. |
| `expired X-Upload-Timestamp` | Client/server clocks differ beyond allowed skew. |
| `invalid X-Upload-Signature` | Canonical string or secret does not match the backend. |
| `image upload api secret is not configured` | Backend env `IMAGE_UPLOAD_API_SECRET` is empty. |

## Operational Notes

- Store `IMAGE_UPLOAD_API_SECRET` in the server secret manager or deployment environment, not in source control.
- Mobile clients must use the same secret provisioned for the target environment.
- Rotate the secret by deploying a new value and updating clients at the same time.
- Keep client clocks synchronized because stale timestamps are rejected.
