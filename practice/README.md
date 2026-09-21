# 🛒 E-commerce Practice — Spring Boot Monorepo

Proyek ini adalah implementasi backend *microservices* untuk sistem e-commerce dasar. Proyek ini dibangun menggunakan **Spring Boot** dengan arsitektur monorepo yang memisahkan fitur menjadi tiga layanan mandiri.

## 🚀 Fitur Utama (Microservices)

Proyek ini terdiri dari 3 service terpisah yang memiliki database masing-masing:
1. **📦 Catalog Service (Port 8081):** Mengelola data toko, produk, dan varian.
2. **🛒 Cart Service (Port 8082):** Mengelola keranjang belanja pengguna.
3. **💳 Transaction Service (Port 8083):** Mengelola proses *checkout* dan riwayat pembelian.

## 🛠️ Teknologi yang Digunakan

- **Framework:** Spring Boot 3.0.13
- **Bahasa:** Java 17 (Target Bytecode)
- **Database:** PostgreSQL 15
- **Build Tool:** Maven (Multi-module)
- **Database Migration:** Flyway
- **Testing:** JUnit 5, Mockito, Node.js (untuk skrip pengujian)

## 🏃‍♂️ Cara Menjalankan Aplikasi (Quick Start)

Cara termudah dan paling direkomendasikan untuk menjalankan proyek ini adalah menggunakan **Docker**.

**Prasyarat:**
- Java JDK 17 (atau 21)
- Maven 3.6.3+
- Docker & Docker Compose (harus sudah berjalan)

**Langkah-langkah:**

1. Buka terminal (CMD/PowerShell) di folder root proyek (folder yang berisi file `pom.xml`).
2. Jalankan perintah berikut untuk *build* aplikasi dan menjalankan container:
   ```bash
   mvn clean verify
   docker compose up --build -d
   ```
3. Pantau log aplikasi untuk memastikan semuanya berjalan lancar:
   ```bash
   docker compose logs -f
   ```
4. Pastikan ketiga service sudah siap (*readiness check*) dengan membuka URL berikut di browser:
   - Catalog: http://localhost:8081/actuator/health
   - Cart: http://localhost:8082/actuator/health
   - Transaction: http://localhost:8083/actuator/health
   
   *(Pastikan semuanya menampilkan `{"status":"UP"}` sebelum mencoba API)*

Untuk menghentikan aplikasi tanpa menghapus data database:
```bash
docker compose down
```

## 🧪 Simulasi & Pengujian

Setelah semua service berstatus `UP`, Anda bisa menjalankan skrip pengujian yang sudah disediakan:

- **Skrip Demo Alur Pembelian (PowerShell):** 
  Mensimulasikan pembuatan toko, penambahan barang ke keranjang, hingga *checkout*.
  ```powershell
  ./scripts/demo.ps1
  ```
- **Pengujian Integrasi Lanjutan (Node.js 18+):**
  Menguji seluruh alur fungsionalitas dan keamanan (seperti persaingan stok).
  ```bash
  node scripts/smoke-test.mjs
  ```

*(Untuk mencoba endpoint API secara manual, silakan lihat file `docs/requests.http` atau `docs/API.md`)*

## 📌 Catatan & Batasan Proyek

- Proyek ini murni **Backend REST API** (tidak ada antarmuka UI/Frontend).
- Otentikasi pengguna hanya disimulasikan melalui header `X-Customer-Id` (tidak menggunakan JWT/Login sungguhan).
- Tidak ada integrasi dengan sistem pembayaran nyata (*Payment Gateway*) atau jasa pengiriman.
- Komunikasi antar-service menggunakan sinkronisasi HTTP standar agar mudah dijalankan (tanpa *Message Broker* tambahan).

---
*Untuk detail arsitektur lebih lanjut, silakan baca [ARCHITECTURE.md](docs/ARCHITECTURE.md).*
