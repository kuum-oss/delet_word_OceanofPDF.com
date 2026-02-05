package org.example;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class MangaResizer {

    // ПРОВЕРКА: Нужно ли растягивать?
    public boolean needsResizing(File file) {
        try (PDDocument doc = PDDocument.load(file)) {
            // Проверяем несколько страниц
            int checkLimit = Math.min(doc.getNumberOfPages(), 5);

            for (int i = 0; i < checkLimit; i++) {
                PDPage page = doc.getPage(i);
                PDImageXObject img = findMainImage(page);

                if (img != null) {
                    float pageWidth = page.getMediaBox().getWidth();
                    // Если картинка меньше 99% ширины страницы — покажем окно!
                    // (Сделал 0.99 специально, чтобы окно точно появилось)
                    if (img.getWidth() < pageWidth * 0.99) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    // Создание картинки для превью (берем 3-ю страницу, чтобы не обложку)
    public BufferedImage getPreviewImage(File file) throws IOException {
        try (PDDocument doc = PDDocument.load(file)) {
            int total = doc.getNumberOfPages();
            if (total < 1) return null;

            // Берем страницу из середины начала (индекс 2), если страниц мало — последнюю
            int pageIndex = (total > 3) ? 2 : total - 1;

            PDFRenderer renderer = new PDFRenderer(doc);
            return renderer.renderImage(pageIndex, 1.0f);
        }
    }

    // ГЛАВНОЕ ДЕЙСТВИЕ
    public void applyResize(File file, CropMode mode) throws IOException {
        if (mode == CropMode.SKIP) return;

        try (PDDocument doc = PDDocument.load(file)) {
            // Проходим по всем страницам, КРОМЕ ПЕРВОЙ (обложки)
            // i = 1 (вторая страница)
            for (int i = 1; i < doc.getNumberOfPages(); i++) {
                PDPage page = doc.getPage(i);
                PDImageXObject img = findMainImage(page);

                if (img == null) continue;

                float pw = page.getMediaBox().getWidth();
                float ph = page.getMediaBox().getHeight();
                float iw = img.getWidth();
                float ih = img.getHeight();

                float newW = iw;
                float newH = ih;

                switch (mode) {
                    case FIT_WIDTH:
                        float scaleW = pw / iw;
                        newW = pw;
                        newH = ih * scaleW;
                        break;
                    case FIT_HEIGHT:
                        float scaleH = ph / ih;
                        newW = iw * scaleH;
                        newH = ph;
                        break;
                    case STRETCH:
                        newW = pw;
                        newH = ph;
                        break;
                }

                // Центрируем
                float x = (pw - newW) / 2;
                float y = (ph - newH) / 2;

                // Рисуем
                page.setContents(new java.util.ArrayList<>());
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.OVERWRITE, true, true)) {
                    cs.drawImage(img, x, y, newW, newH);
                }
            }
            doc.save(file);
        }
    }

    private PDImageXObject findMainImage(PDPage page) throws IOException {
        PDResources res = page.getResources();
        if (res == null) return null;
        PDImageXObject maxImg = null;
        int maxPixels = 0;

        for (COSName name : res.getXObjectNames()) {
            if (res.isImageXObject(name)) {
                try {
                    PDImageXObject img = (PDImageXObject) res.getXObject(name);
                    int px = img.getWidth() * img.getHeight();
                    if (px > 50000 && px > maxPixels) { // Игнорируем мелкие иконки
                        maxPixels = px;
                        maxImg = img;
                    }
                } catch (Exception e) {}
            }
        }
        return maxImg;
    }
}