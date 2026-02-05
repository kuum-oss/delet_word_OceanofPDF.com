package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.File;
import java.util.List;

public class MangaCleanerApp extends JFrame {

    private final PdfWatermarkCleaner pdfCleaner = new PdfWatermarkCleaner();
    private final EpubWatermarkCleaner epubCleaner = new EpubWatermarkCleaner();
    private final JLabel statusLabel;

    public MangaCleanerApp() {
        setTitle("Manga Cleaner");
        setSize(500, 300);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout());

        JLabel dropLabel = new JLabel(
                "<html><center><h2>Перетащи файлы сюда</h2>PDF или EPUB<br/><br/><font color='gray'>OceanofPDF.com будет удалён</font></center></html>",
                SwingConstants.CENTER
        );
        dropLabel.setFont(new Font("Arial", Font.PLAIN, 14));

        statusLabel = new JLabel("Ожидание файлов...", SwingConstants.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        panel.add(dropLabel, BorderLayout.CENTER);
        panel.add(statusLabel, BorderLayout.SOUTH);
        panel.setBorder(BorderFactory.createDashedBorder(Color.GRAY, 2, 5));

        // Настройка Drag & Drop
        new DropTarget(panel, new FileDropHandler());

        add(panel);
    }

    private class FileDropHandler extends DropTargetAdapter {
        @Override
        public void drop(DropTargetDropEvent event) {
            try {
                event.acceptDrop(DnDConstants.ACTION_COPY);
                List<File> files = (List<File>) event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);

                if (files.isEmpty()) return;

                // Обрабатываем первый файл (можно переделать под цикл)
                for (File file : files) {
                    processAsync(file);
                }

            } catch (Exception e) {
                showError(e);
            }
        }
    }

    private void processAsync(File input) {
        statusLabel.setText("Обработка: " + input.getName() + "...");
        statusLabel.setForeground(Color.BLUE);

        new SwingWorker<File, Void>() {
            @Override
            protected File doInBackground() throws Exception {
                String name = input.getName().toLowerCase();
                File output;

                if (name.endsWith(".pdf")) {
                    output = outputFile(input, "_clean.pdf");
                    pdfCleaner.clean(input, output);
                } else if (name.endsWith(".epub")) {
                    output = outputFile(input, "_clean.epub");
                    epubCleaner.clean(input, output);
                } else {
                    throw new IllegalArgumentException("Неподдерживаемый формат: " + name);
                }
                return output;
            }

            @Override
            protected void done() {
                try {
                    File result = get(); // Здесь вылетит ошибка, если она была в doInBackground
                    statusLabel.setText("Готово!");
                    statusLabel.setForeground(new Color(0, 150, 0));
                    JOptionPane.showMessageDialog(MangaCleanerApp.this,
                            "Файл сохранен:\n" + result.getName());
                } catch (Exception e) {
                    statusLabel.setText("Ошибка!");
                    statusLabel.setForeground(Color.RED);
                    showError(e);
                }
            }
        }.execute();
    }

    private File outputFile(File input, String suffix) {
        String name = input.getName().replaceAll("\\.(pdf|epub)$", "");
        return new File(input.getParent(), name + suffix);
    }

    private void showError(Exception e) {
        e.printStackTrace(); // Вывод в консоль для отладки
        String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        JOptionPane.showMessageDialog(this, msg, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MangaCleanerApp().setVisible(true));
    }
}