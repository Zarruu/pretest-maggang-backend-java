# API Reference

Semua body request/response menggunakan JSON. Nilai uang dalam rupiah. ID menggunakan UUID. Timestamp menggunakan ISO-8601 dengan offset.

## Header

| Header | Digunakan pada | Contoh |
|---|---|---|
| `Content-Type` | Request dengan body | `application/json` |
| `X-Customer-Id` | Semua API Cart dan Transaction | UUID pelanggan demo |
| `Idempotency-Key` | `POST /api/checkouts` | UUID unik untuk satu intent checkout |
| `X-Internal-Key` | Endpoint `/internal/**` | Sama dengan konfigurasi `INTERNAL_KEY` |

`X-Customer-Id` adalah simulasi identitas untuk practice; caller dapat menggantinya. Sistem login sungguhan harus memasok identitas terverifikasi.

## Catalog — localhost:8081

| Method | Path | Fungsi |
|---|---|---|
| GET | `/api/stores?page=0&size=20` | Daftar toko |
| POST | `/api/stores` | Membuat toko; 201 |
| GET | `/api/stores/{id}` | Detail toko |
| PUT | `/api/stores/{id}` | Memperbarui toko |
| GET | `/api/products?storeId={id}&q=MacBook&category=Laptop&sort=newest&page=0&size=20` | Katalog/pencarian |
| POST | `/api/products` | Membuat produk; 201 |
| GET | `/api/products/{id}` | Detail produk, toko, dan varian |
| PUT | `/api/products/{id}` | Memperbarui informasi produk; toko pemilik tidak boleh berubah |
| POST | `/api/products/{id}/variants` | Membuat varian beserta harga dan stok awal; 201 |
| GET | `/api/variants/{id}` | Detail varian, harga, dan stok terkini |

`storeId`, `q`, dan `category` opsional. `sort`: `newest`, `price_asc`, atau `price_desc`. `page >= 0`, `size` antara 1–100. Pagination mengembalikan `content`, `page`, `size`, dan `totalElements`. Produk baru tanpa varian belum dapat dibeli; `startingPrice` bernilai null.

Toko:

```json
{"name":"iStore Bandung","city":"Bandung"}
```

Produk:

```json
{
  "storeId":"UUID_TOKO",
  "name":"MacBook Pro",
  "description":"MacBook Pro M2 Pro",
  "category":"Laptop",
  "imageUrl":""
}
```

Varian:

```json
{"sku":"MBP-16-512-SILVER","variantName":"16GB / 512GB / Silver","price":36499000,"stock":10}
```

## Cart — localhost:8082

| Method | Path | Fungsi |
|---|---|---|
| GET | `/api/cart` | Item keranjang berikut detail varian, lineTotal, dan subtotal terbaru |
| POST | `/api/cart/items` | Tambah item; varian sama menambah quantity; 201 |
| PATCH | `/api/cart/items/{id}` | Ganti quantity dan note |
| DELETE | `/api/cart/items/{id}` | Hapus item; 204 |

Tambah item:

```json
{"variantId":"UUID_VARIAN","quantity":1,"note":"Mohon kemasan aman"}
```

Ubah item:

```json
{"quantity":2,"note":"Warna sesuai pilihan"}
```

`note` wajib ada, boleh string kosong. Maksimal 100 varian per keranjang, 1–1000 unit per varian. Validasi stok saat menambah keranjang tidak menjamin stok masih tersedia saat checkout. Respons tambah/ubah mengandung `id` item keranjang yang digunakan saat checkout.

## Transaction — localhost:8083

| Method | Path | Fungsi |
|---|---|---|
| POST | `/api/checkouts` | Membuat checkout dari item terpilih |
| GET | `/api/transactions` | Daftar transaksi pelanggan |
| GET | `/api/transactions/{id}` | Detail transaksi, pesanan per toko, dan item snapshot |

Checkout:

```json
{
  "itemIds":["UUID_ITEM_KERANJANG"],
  "recipientName":"Budi",
  "recipientPhone":"081234567890",
  "shippingAddress":"Bandung, Jawa Barat",
  "courier":"REGULAR"
}
```

`itemIds` adalah ID **baris keranjang**, bukan product ID atau variant ID. Kurir `REGULAR` Rp15.000 dan `EXPRESS` Rp25.000 per toko. Body tidak menerima harga atau total dari client.

Respons checkout:

- **201**: `status=CREATED`, pembelian dibuat. Key yang sama dan request yang sama mengembalikan transaksi yang sama dengan 201.
- **202**: `status=PENDING`, hasil service terkait belum pasti; worker melanjutkan otomatis. Simpan `id` dan cek `GET /api/transactions/{id}`.
- **409** dengan body detail transaksi: `status=FAILED` jika pemrosesan stok ditolak. Gunakan key baru untuk percobaan pembelian baru setelah memperbaiki masalah.
- **409** ProblemDetail: key sama tetapi isi request berbeda, atau masih ada checkout lain yang outstanding.

Contoh struktur detail:

```json
{
  "id":"UUID_TRANSAKSI",
  "customerId":"UUID_PELANGGAN",
  "transactionDate":"2026-09-21T16:00:00Z",
  "status":"CREATED",
  "totalAmount":36514000,
  "failureReason":null,
  "cartCleaned":true,
  "orders":[{
    "id":"UUID_PESANAN",
    "storeId":"UUID_TOKO",
    "storeName":"iStore Bandung",
    "invoiceNumber":"INV-UUID_PESANAN",
    "recipientName":"Budi",
    "recipientPhone":"081234567890",
    "shippingAddress":"Bandung, Jawa Barat",
    "courier":"REGULAR",
    "shippingCost":15000,
    "subtotal":36499000,
    "status":"CREATED",
    "items":[{
      "id":"UUID_ITEM_TRANSAKSI",
      "variantId":"UUID_VARIAN",
      "productName":"MacBook Pro",
      "variantName":"16GB / 512GB / Silver",
      "unitPrice":36499000,
      "quantity":1,
      "lineTotal":36499000,
      "note":"Mohon kemasan aman"
    }]
  }]
}
```

Filter daftar: `status=PENDING|CREATED|FAILED`, `q` (nama produk atau invoice), `from`/`to` (timestamp ISO-8601), `page`, `size`. Semua opsional. Daftar mengembalikan ringkasan transaksi; detail menyediakan seluruh pesanan dan item.

Contoh:

```text
GET /api/transactions?status=CREATED&q=MacBook&page=0&size=20
GET /api/transactions?from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z
```

## Endpoint internal

- Catalog `POST /internal/stock-operations/{checkoutId}`: `{"items":[{"variantId":"UUID","quantity":1}]}`. Mengurangi stok secara atomik dan mengembalikan snapshot item. Pengulangan checkout ID yang sama wajib membawa item yang sama.
- Cart `GET /internal/carts/{customerId}`: snapshot item tanpa panggilan ke Catalog.
- Cart `POST /internal/carts/cleanup`: `{"customerId":"UUID","items":[{"id":"UUID_ITEM","version":0}]}`. Hanya menghapus item pelanggan dengan versi yang cocok; 204.

Endpoint internal dipakai antarservice, bukan untuk alur client biasa.

## Error umum

400: input tidak valid; 403: internal key salah; 404: data tidak ditemukan atau bukan milik pelanggan; 409: konflik data/stok; 503: service terkait tidak tersedia sebelum intent checkout dapat disimpan.

Contoh error validasi menggunakan ProblemDetail:

```json
{"type":"about:blank","title":"Bad Request","status":400,"detail":"quantity: must be greater than or equal to 1"}
```
