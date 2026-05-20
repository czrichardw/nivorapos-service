# Image Upload API Spec

## Upload Product Image

Uploads one product image through POS service. POS validates the file, creates a thumbnail, stores both files using the configured storage provider, and returns URLs for the full image and thumbnail.

### Endpoint

```http
POST /NivoraPos/images/upload
Content-Type: multipart/form-data
```

If `server.servlet.context-path` is changed, replace `/NivoraPos` with the configured context path.

### Authentication

`/images/upload` and `/images/delete` accept either:

- a valid POS/PSGS bearer token, or
- a timestamped HMAC upload signature.

When bearer authentication is not present or cannot be validated, send:

| Header | Required | Description |
| --- | --- | --- |
| `X-Upload-Timestamp` | yes | ISO-8601 timestamp with timezone offset. Use GMT+7 / WIB as `+07:00`, for example `2026-05-20T15:05:40+07:00`. Epoch seconds or milliseconds are also accepted for compatibility. Must be within the configured clock skew. |
| `X-Upload-Signature` | yes | Lowercase hex HMAC-SHA256 signature. |

Signature config:

```properties
app.image-upload.api-secret=${IMAGE_UPLOAD_API_SECRET:}
app.image-upload.signature-skew-seconds=${IMAGE_UPLOAD_SIGNATURE_SKEW_SECONDS:300}
```

Canonical string:

```text
HTTP_METHOD|REQUEST_URI_WITH_QUERY|X_UPLOAD_TIMESTAMP
```

The canonical string is the exact one-line text signed by both the client and backend. It uses `|` as the delimiter, and the timestamp part must be exactly the same value sent in `X-Upload-Timestamp`.

Examples:

```text
POST|/NivoraPos/images/upload|2026-05-20T15:05:40+07:00
```

```text
DELETE|/NivoraPos/images/delete?url=https%3A%2F%2Fassets.example.com%2Fnivora-pos-images%2Fimages%2Fproduct%2F2026-05-03%2Fproduct_abc123.jpg|2026-05-20T15:05:40+07:00
```

Compute:

```text
X-Upload-Signature = hex(HMAC_SHA256(IMAGE_UPLOAD_API_SECRET, canonical_string))
```

### Request

Multipart form data:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `file` | file | yes | Image file to upload. |

Allowed content types:

| Content-Type | Extension |
| --- | --- |
| `image/jpeg` | `jpg`, `jpeg` |
| `image/png` | `png` |
| `image/webp` | `webp` |

Default max file size:

```text
5 MB
```

Configured by:

```properties
spring.servlet.multipart.max-file-size
spring.servlet.multipart.max-request-size
app.upload.max-size-bytes
```

### cURL Example

```bash
curl -X POST "https://api.example.com/NivoraPos/images/upload" \
  -H "X-Upload-Timestamp: 2026-05-20T15:05:40+07:00" \
  -H "X-Upload-Signature: <hmac-sha256-hex>" \
  -F "file=@/path/to/product.jpg"
```

For delete, sign the exact request URI including the encoded `url` query string:

```bash
curl -X DELETE "https://api.example.com/NivoraPos/images/delete?url=https%3A%2F%2Fassets.example.com%2Fnivora-pos-images%2Fimages%2Fproduct%2F2026-05-03%2Fproduct_abc123.jpg" \
  -H "X-Upload-Timestamp: 2026-05-20T15:05:40+07:00" \
  -H "X-Upload-Signature: <hmac-sha256-hex>"
```

### Success Response

HTTP `200`

```json
{
  "status": "SUCCESS",
  "message": "Image uploaded successfully",
  "data": {
    "urlFull": "https://assets.example.com/nivora-pos-images/images/product/2026-05-03/product_abc123.jpg",
    "urlThumb": "https://assets.example.com/nivora-pos-images/images/product/2026-05-03/product_abc123_thumb.jpg"
  }
}
```

Use these values when creating or updating products:

| Upload response | Product request field |
| --- | --- |
| `data.urlFull` | `imageUrl` |
| `data.urlThumb` | `imageThumbUrl` |

### Error Responses

Invalid request, unsupported file, or file too large:

HTTP `400`

```json
{
  "status": "ERROR",
  "message": "file content type must be image/jpeg, image/png, or image/webp",
  "data": null
}
```

Storage/server failure:

HTTP `500`

```json
{
  "status": "ERROR",
  "message": "Failed to upload image",
  "data": null
}
```

## Storage Configuration

### MinIO Upload Through POS

```env
APP_STORAGE_PROVIDER=minio
APP_STORAGE_MINIO_ENDPOINT=http://minio:9000
APP_STORAGE_MINIO_ACCESS_KEY=<access-key>
APP_STORAGE_MINIO_SECRET_KEY=<secret-key>
APP_STORAGE_MINIO_BUCKET=nivora-pos-images
APP_STORAGE_MINIO_OBJECT_PREFIX=images/product
```

`APP_STORAGE_MINIO_ENDPOINT` is the private/internal URL used by POS to write to MinIO.

Examples:

```env
# Same Docker network
APP_STORAGE_MINIO_ENDPOINT=http://minio:9000

# Separate server/private network
APP_STORAGE_MINIO_ENDPOINT=http://10.0.0.20:9000
```

### Public Read URL

If MinIO images are exposed for public read through a domain or reverse proxy:

```env
APP_STORAGE_MINIO_PUBLIC_BASE_URL=https://assets.example.com/nivora-pos-images
```

Returned image URLs will be:

```text
https://assets.example.com/nivora-pos-images/images/product/{yyyy-MM-dd}/{filename}
```

If MinIO public access is closed:

```env
APP_STORAGE_MINIO_PUBLIC_BASE_URL=
```

In that setup, clients should not depend on direct MinIO URLs. POS will still upload images to MinIO, but serving private images requires a POS read gateway or presigned URL API.

### Local Storage Fallback

```env
APP_STORAGE_PROVIDER=local
UPLOAD_DIR=uploads/images
UPLOAD_PUBLIC_BASE_URL=https://api.example.com/NivoraPos
```

Returned image URLs will be:

```text
https://api.example.com/NivoraPos/images/product/{yyyy-MM-dd}/{filename}
```
