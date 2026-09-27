-- Jalankan ini di Supabase SQL Editor (Project > SQL Editor > New query)
-- Tabel baru: chapters — relasi ke manga lewat manga_id (bukan cocokin title string lagi)

create table if not exists chapters (
  id bigint generated always as identity primary key,
  manga_id bigint not null references manga(id) on delete cascade,
  chapter_number integer not null,
  pdf_url text not null,
  created_at timestamptz not null default now(),
  unique (manga_id, chapter_number)
);

alter table chapters enable row level security;

-- Semua orang (termasuk yang belum login) boleh baca daftar chapter
drop policy if exists "Public read chapters" on chapters;
create policy "Public read chapters" on chapters
  for select using (true);

-- Cuma admin (ada di tabel admins) yang boleh insert/update chapter
drop policy if exists "Admins can insert chapters" on chapters;
create policy "Admins can insert chapters" on chapters
  for insert with check (
    exists (select 1 from admins where user_id = auth.uid())
  );

drop policy if exists "Admins can update chapters" on chapters;
create policy "Admins can update chapters" on chapters
  for update using (
    exists (select 1 from admins where user_id = auth.uid())
  );

-- OPSIONAL: kalau mau mindahin data chapter yang lama (yang selama ini cuma
-- ada sebagai file PDF di GitHub, hasil parsing nama file) ke tabel chapters
-- yang baru, harus manual insert satu-satu, karena datanya sekarang cuma ada
-- sebagai nama file, bukan baris database. Untuk manga yang sudah lanjut jalan,
-- paling gampang: re-upload tiap chapter lewat admin panel yang baru (upload
-- PDF-nya lagi lewat tombol upload chapter) — sekali ini aja, abis itu nggak
-- perlu upload ulang apa pun lagi tiap ada update.
