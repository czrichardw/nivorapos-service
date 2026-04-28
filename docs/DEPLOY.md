# Deploy Guide — pos-service

Deploy manual menggunakan Docker image yang di-transfer langsung ke server (tanpa Docker Hub).

## Prasyarat

| Kebutuhan | Versi |
|-----------|-------|
| Docker (lokal) | 20+ |
| Docker (server) | 20+ |
| Akses SSH ke server | — |
| PostgreSQL | Sudah berjalan di server/eksternal |

---

## 1. Build Image di Lokal

```bash
docker build -t pos-service:latest .
```

Verifikasi image berhasil dibuat:

```bash
docker images | grep pos-service
```

---

## 2. Export Image ke File `.tar`

```bash
docker save -o pos-service.tar pos-service:latest
```

> File `pos-service.tar` akan muncul di direktori saat ini. Ukurannya sekitar 150–250 MB.

---

## 3. Transfer ke Server via SCP

```bash
scp pos-service.tar shananfamily@172.30.220.43:/opt/nivorapos-service/
```

Sekaligus transfer `docker-compose.yml` jika belum ada di server:

```bash
scp docker-compose.yml shananfamily@172.30.220.43:/opt/nivorapos-service/
```

---

## 4. Load Image di Server

SSH ke server, lalu load image:

```bash
ssh shananfamily@namboserver
cd /opt/nivorapos-service

docker load -i pos-service.tar
```

Verifikasi image tersedia:

```bash
docker images | grep pos-service
```

---

## 5. Konfigurasi Environment

Edit `docker-compose.yml` di server dan sesuaikan nilai environment:

```yaml
services:
  pos-service:
    image: pos-service:latest
    container_name: pos-service
    restart: always
    ports:
      - "8083:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://<DB_HOST>:<DB_PORT>/<DB_NAME>
      SPRING_DATASOURCE_USERNAME: <DB_USER>
      SPRING_DATASOURCE_PASSWORD: <DB_PASSWORD>
      SPRING_JPA_HIBERNATE_DDL_AUTO: update
      JWT_SECRET: <JWT_SECRET>
      JWT_EXPIRATION: 86400000
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
```

> Jangan commit `docker-compose.yml` yang mengandung credential ke repository.

---

## 6. Jalankan Container

```bash
docker compose up -d
```

Cek status container:

```bash
docker compose ps
docker compose logs -f pos-service
```

Tunggu hingga health check hijau (`healthy`), biasanya 60–90 detik pertama setelah start.

---

## 7. Verifikasi

```bash
curl http://localhost:8083/actuator/health
```

Response yang diharapkan:

```json
{"status":"UP"}
```

---

## Update / Redeploy

Ulangi langkah 1–4, lalu restart container dengan image baru:

```bash
docker compose down
docker compose up -d
```

Hapus image lama setelah verifikasi berjalan normal:

```bash
docker image prune -f
```

---

## Troubleshooting

**Container langsung mati / restart loop**
```bash
docker compose logs pos-service
```
Periksa koneksi ke database dan validasi env variable.

**Port 8083 tidak bisa diakses**
```bash
# Pastikan firewall membuka port
ufw allow 8083
```

**Gagal load image**
```bash
# Pastikan file tar tidak corrupt saat transfer
md5sum pos-service.tar   # bandingkan hash di lokal dan server
```
