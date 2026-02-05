package org.example;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.epub.EpubReader;
import nl.siegmann.epublib.epub.EpubWriter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

public class EpubWatermarkCleaner {

    private static final String WATERMARK = "oceanofpdf.com";

    public void clean(File input, File output) throws IOException {
        Book book;
        // Читаем исходный EPUB
        try (InputStream in = new FileInputStream(input)) {
            book = new EpubReader().readEpub(in);
        }

        Collection<Resource> resources = book.getResources().getAll();

        for (Resource res : resources) {
            // Обрабатываем только HTML файлы
            if (!isHtml(res)) continue;

            // Получаем текст ресурса
            String originalHtml = new String(res.getData(), StandardCharsets.UTF_8);

            // Быстрая проверка, чтобы не использовать регулярки зря
            if (!originalHtml.toLowerCase().contains(WATERMARK)) continue;

            // Удаляем watermark (нечувствительно к регистру)
            String cleanedHtml = originalHtml.replaceAll("(?i)\\s*oceanofpdf\\.com\\s*", "");

            // Сохраняем обратно в ресурс
            res.setData(cleanedHtml.getBytes(StandardCharsets.UTF_8));
        }

        // --- ФИКС БАГА С ОБЛОЖКОЙ ---
        // Если у книги была обложка, мы принудительно устанавливаем её заново.
        // Это гарантирует, что она корректно пропишется в метаданных при сохранении.
        if (book.getCoverImage() != null) {
            book.setCoverImage(book.getCoverImage());
        }
        // ---------------------------

        // Записываем новый EPUB
        try (OutputStream out = new FileOutputStream(output)) {
            new EpubWriter().write(book, out);
        }
    }

    private boolean isHtml(Resource res) {
        String href = res.getHref();
        if (href == null) return false;
        String lower = href.toLowerCase();
        return lower.endsWith(".html") || lower.endsWith(".xhtml") || lower.endsWith(".htm");
    }
}