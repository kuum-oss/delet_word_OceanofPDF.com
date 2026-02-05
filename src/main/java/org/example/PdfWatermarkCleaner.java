package org.example;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PdfWatermarkCleaner {

    private static final String WATERMARK_TEXT = "oceanofpdf";

    public void clean(File input, File output) throws Exception {
        try (PDDocument doc = PDDocument.load(input)) {

            int totalPages = doc.getNumberOfPages();
            List<Integer> pagesToRemove = new ArrayList<>();

            for (int i = 0; i < totalPages; i++) {
                PDPage page = doc.getPage(i);
                PDRectangle mediaBox = page.getMediaBox();
                float pageHeight = mediaBox.getHeight();

                // --- ШАГ 1: Поиск текста и анализ содержимого ---
                WatermarkLocator locator = new WatermarkLocator();
                locator.setSortByPosition(true);
                locator.setStartPage(i + 1);
                locator.setEndPage(i + 1);
                locator.writeText(doc, new OutputStreamWriter(new ByteArrayOutputStream()));

                boolean foundWatermark = !locator.getFoundAreas().isEmpty();

                // Если водяной знак найден, решаем: удалять страницу или чистить
                if (foundWatermark) {
                    boolean hasImages = hasImages(page);
                    int textLength = locator.getFullTextLength();

                    // ЭВРИСТИКА:
                    // Если на странице НЕТ картинок И мало текста (меньше 300 символов) -> Это мусорная страница
                    if (!hasImages && textLength < 300) {
                        pagesToRemove.add(i);
                        continue; // Переходим к следующей, эту удалим позже
                    }

                    // Иначе (если это манга или страница книги) -> Чистим (код ниже)
                }

                // --- ШАГ 2: Удаление ссылок (если страницу оставляем) ---
                List<PDAnnotation> annotations = page.getAnnotations();
                List<PDAnnotation> toRemove = new ArrayList<>();
                for (PDAnnotation ann : annotations) {
                    if (ann instanceof PDAnnotationLink) {
                        PDAnnotationLink link = (PDAnnotationLink) ann;
                        if (link.getAction() instanceof PDActionURI) {
                            PDActionURI uri = (PDActionURI) link.getAction();
                            if (uri.getURI() != null && uri.getURI().toLowerCase().contains(WATERMARK_TEXT)) {
                                toRemove.add(ann);
                            }
                        }
                    }
                }
                if (!toRemove.isEmpty()) {
                    annotations.removeAll(toRemove);
                }

                // --- ШАГ 3: Визуальная замазка (если страницу оставляем) ---
                if (foundWatermark) {
                    List<PDRectangle> areasToCover = locator.getFoundAreas();
                    if (!areasToCover.isEmpty()) {
                        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                            cs.setNonStrokingColor(Color.WHITE);

                            for (PDRectangle rect : areasToCover) {
                                float yFromBottom = pageHeight - rect.getLowerLeftY();
                                cs.addRect(
                                        rect.getLowerLeftX() - 2,
                                        yFromBottom - 2,
                                        rect.getWidth() + 6,
                                        rect.getHeight() + 6
                                );
                                cs.fill();
                            }
                        }
                    }
                }
            }

            // --- ШАГ 4: Физическое удаление мусорных страниц ---
            // Удаляем с конца, чтобы не сбились номера страниц
            Collections.sort(pagesToRemove, Collections.reverseOrder());
            for (Integer pageIndex : pagesToRemove) {
                doc.removePage(pageIndex);
            }

            doc.save(output);
        }
    }

    // Проверка наличия картинок на странице (для Манги)
    private boolean hasImages(PDPage page) {
        PDResources resources = page.getResources();
        if (resources == null) return false;
        for (COSName name : resources.getXObjectNames()) {
            try {
                if (resources.isImageXObject(name)) {
                    return true;
                }
            } catch (Exception e) {
                // Игнорируем ошибки чтения ресурсов
            }
        }
        return false;
    }

    private static class WatermarkLocator extends PDFTextStripper {
        private final List<PDRectangle> foundAreas = new ArrayList<>();
        private final StringBuilder fullTextBuilder = new StringBuilder();

        public WatermarkLocator() throws Exception {
            super();
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            if (text != null) {
                fullTextBuilder.append(text); // Собираем весь текст для подсчета длины

                if (text.toLowerCase().contains(WATERMARK_TEXT)) {
                    float minX = Float.MAX_VALUE;
                    float minY = Float.MAX_VALUE;
                    float maxX = Float.MIN_VALUE;
                    float maxY = Float.MIN_VALUE;

                    for (TextPosition pos : textPositions) {
                        float x = pos.getXDirAdj();
                        float y = pos.getYDirAdj();
                        float w = pos.getWidthDirAdj();
                        float h = pos.getHeightDir();

                        if (x < minX) minX = x;
                        if (y < minY) minY = y;
                        if (x + w > maxX) maxX = x + w;
                        if (y + h > maxY) maxY = y + h;
                    }

                    if (minX != Float.MAX_VALUE) {
                        // Сохраняем координаты (от верха страницы)
                        foundAreas.add(new PDRectangle(minX, maxY, maxX - minX, maxY - minY));
                    }
                }
            }
        }

        public List<PDRectangle> getFoundAreas() {
            return foundAreas;
        }

        public int getFullTextLength() {
            return fullTextBuilder.length();
        }
    }
}