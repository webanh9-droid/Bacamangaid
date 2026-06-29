# Baca Manga ID

App Android native buat baca manga, datanya diambil langsung dari GitHub repo `Bacamangaid` (bukan WebView).

## Cara kerja

1. **List Manga** — app fetch isi root repo lewat GitHub API, baca semua nama file PDF, deteksi judul manga dari teks **sebelum kata "Chapter"** di nama file
2. **List Chapter** — tap manga → app filter semua PDF yang judulnya cocok, urutkan berdasar nomor chapter
3. **Reader** — tap chapter → app download PDF-nya, render tiap halaman jadi gambar pakai `PdfRenderer` bawaan Android, ditampilin scroll vertikal kayak baca manga

## Format nama file yang WAJIB dipakai

Semua PDF taruh **flat di root repo** (nggak perlu folder), dengan format nama:

```
Judul Manga Chapter 1.pdf
Judul Manga Chapter 2.pdf
Judul Lain Chapter 1.pdf
Judul Lain Chapter 2.pdf
```

Contoh nyata:
```
Cultural Exchange with a Game Centre Girl Chapter 1.pdf
Cultural Exchange with a Game Centre Girl Chapter 2.pdf
Si Ocong Chapter 1.pdf
Si Ocong Chapter 2.pdf
```

App otomatis mengelompokkan berdasarkan teks sebelum kata "Chapter" (case-insensitive, spasi fleksibel). Jadi:
- `Si Ocong Chapter 1.pdf` dan `Si Ocong Chapter 2.pdf` -> dianggap 1 manga "Si Ocong" dengan 2 chapter
- Pastikan judul ditulis konsisten persis sama di semua chapter (termasuk kapitalisasi/spasi), kalau beda dikit app bisa anggap itu manga yang berbeda

PENTING: file lama bab1.pdf dst (format chapter tanpa judul) tidak akan terdeteksi app ini, perlu di-rename ulang pakai format di atas.

## Build APK

Sama kayak project sebelumnya, push ke GitHub, workflow .github/workflows/build.yml otomatis build APK, download dari tab Actions -> Artifacts.

## Catatan

- GitHub API gratis dibatasi ~60 request/jam per IP tanpa login.
- Render PDF makin banyak halaman = makin berat di RAM HP (semua halaman di-render sekaligus saat buka chapter).
