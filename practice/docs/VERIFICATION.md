# Hasil verifikasi

Pengujian dilakukan pada 21 September 2026.

| Pemeriksaan | Hasil |
|---|---|
| Framework | Spring Boot 3.0.13 |
| Runtime pengujian | Oracle JDK 21.0.10, target kompilasi Java 17 |
| Database aktual | PostgreSQL 15.18, tiga database dengan role berbeda |
| Maven reactor verify | Berhasil, seluruh module |
| Unit test | 7 lulus, 0 gagal, 0 dilewati |
| Integrasi HTTP | 86 assertion lulus |
| Fault injection / recovery | 22 assertion lulus |
| Script demo PowerShell | Berhasil membuat pembelian dan membaca daftar transaksi |
| Docker Compose | Konfigurasi valid (`docker compose config --quiet`) |

Unit test memeriksa pemakaian ulang idempotency key, perubahan payload pada key yang sama, pemilihan item duplikat, kepemilikan keranjang, serta pembedaan kegagalan definitif dan gangguan jaringan.

Integrasi HTTP memeriksa katalog, validasi input, keranjang, checkout multi-toko, nilai total, snapshot nama produk, isolasi data pelanggan berdasarkan header demo, dua pembeli memperebutkan stok terakhir, rollback seluruh item ketika stok tidak cukup, dan cleanup yang mempertahankan perubahan keranjang terbaru.

Fault injection menyembunyikan respons Catalog setelah pengurangan stok committed, lalu membuktikan bahwa worker melanjutkan transaksi tanpa mengurangi stok dua kali. Tes juga menolak cleanup sementara dan memastikan pembelian tetap tersimpan serta keranjang dibersihkan setelah layanan pulih. Jumlah assertion polling dapat berbeda tergantung waktu pemulihan.

## Kondisi lingkungan

JAR dijalankan langsung menggunakan Oracle JDK dengan PostgreSQL 15 lokal yang terisolasi untuk pengujian. Docker daemon tidak tersedia pada lingkungan pengujian, sehingga build dan startup container belum dijalankan; konfigurasi Compose sudah divalidasi.

Sandbox Windows memunculkan kendala canonical-path saat compiler membaca direktori classpath. Kompilasi test menggunakan JAR classpath sementara dari kelas project yang sama, tanpa mengubah source. Verifikasi Maven berikutnya berhasil dengan `-Dmaven.compiler.fork=true -Dmaven.compiler.useIncrementalCompilation=false`. Opsi khusus sandbox dan file sementara tersebut tidak dimasukkan ke konfigurasi project. Build bersih `mvn clean verify` di luar sandbox belum diuji di sini.

Oracle JDK 17 ditetapkan pada Dockerfile; eksekusi pengujian menggunakan Oracle JDK 21 yang masih sesuai spesifikasi "17 or later". Kredensial dan header identitas pada project adalah konfigurasi development, bukan implementasi autentikasi produksi.
