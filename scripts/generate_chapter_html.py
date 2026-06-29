import sys
import os
import re
import glob

def main():
    pdf_path = sys.argv[1]  # contoh: "cultural-exchange/bab5.pdf"
    folder = os.path.dirname(pdf_path)
    filename = os.path.basename(pdf_path)

    m = re.match(r'bab(\d+)\.pdf$', filename, re.IGNORECASE)
    if not m:
        print(f"Skip {pdf_path}: nama file PDF harus format babN.pdf (contoh: bab5.pdf)")
        return

    chapter_num = int(m.group(1))
    target_html = os.path.join(folder, f"bab{chapter_num}.html")

    if os.path.exists(target_html):
        print(f"{target_html} sudah ada, skip (tidak ditimpa).")
        return

    # Cari chapter HTML lain di folder yang sama sebagai template
    candidates = sorted(glob.glob(os.path.join(folder, "bab*.html")))
    candidates = [c for c in candidates if os.path.basename(c) != f"bab{chapter_num}.html"]

    if not candidates:
        print(f"Tidak ada template HTML chapter lain di '{folder}'. "
              f"Buat dulu bab1.html secara manual sebagai template sebelum upload chapter selanjutnya.")
        return

    template_path = candidates[-1]  # pakai chapter terakhir yang ada sebagai template
    with open(template_path, "r", encoding="utf-8") as f:
        content = f.read()

    next_chapter = chapter_num + 1

    # 1) Ganti variabel currentChapter di JS
    content = re.sub(r'let currentChapter = \d+;', f'let currentChapter = {chapter_num};', content)

    # 2) Ganti nomor chapter di <title>
    content = re.sub(r'(Chapter\s*)\d+(\s*</title>)', rf'\g<1>{chapter_num}\g<2>', content)

    # 3) Ganti teks "Membaca: Chapter X" (tampilan awal sebelum JS jalan)
    content = re.sub(r'(Membaca: Chapter )\d+', rf'\g<1>{chapter_num}', content)

    # 4) Ganti link tombol "Bab Selanjutnya" (cosmetic, nanti di-update lagi otomatis oleh JS saat halaman dibuka)
    content = re.sub(
        r'(id="nextBtnTop" href=")bab\d+\.html"',
        rf'\g<1>bab{next_chapter}.html"',
        content
    )
    content = re.sub(
        r'(id="nextBtnBottom" href=")bab\d+\.html"',
        rf'\g<1>bab{next_chapter}.html"',
        content
    )

    with open(target_html, "w", encoding="utf-8") as f:
        f.write(content)

    print(f"Generated {target_html} (clone dari {template_path}, chapter={chapter_num})")


if __name__ == "__main__":
    main()
