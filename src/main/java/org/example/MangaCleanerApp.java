package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class MangaCleanerApp extends JFrame {

    private final PdfWatermarkCleaner pdfCleaner = new PdfWatermarkCleaner();
    private final EpubWatermarkCleaner epubCleaner = new EpubWatermarkCleaner();
    private final MangaResizer mangaResizer = new MangaResizer();

    private final JLabel statusLabel;

    public MangaCleanerApp() {
        setTitle("Manga Cleaner v4.0 (Always Ask)");
        setSize(500, 300);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout());
        JLabel dropLabel = new JLabel("<html><center><h2>ПЕРЕТАЩИ ФАЙЛ СЮДА</h2>PDF / EPUB</center></html>", SwingConstants.CENTER);
        statusLabel = new JLabel("Готов к работе", SwingConstants.CENTER);

        panel.add(dropLabel, BorderLayout.CENTER);
        panel.add(statusLabel, BorderLayout.SOUTH);

        new DropTarget(panel, new FileDropHandler());
        add(panel);
    }

    private class FileDropHandler extends DropTargetAdapter {
        @Override
        public void drop(DropTargetDropEvent event) {
            try {
                event.acceptDrop(DnDConstants.ACTION_COPY);
                List<File> files = (List<File>) event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                for (File file : files) processAsync(file);
            } catch (Exception e) { showError(e); }
        }
    }

    private void processAsync(File input) {
        statusLabel.setText("Обработка: " + input.getName());

        new SwingWorker<File, Void>() {
            @Override
            protected File doInBackground() throws Exception {
                String name = input.getName().toLowerCase();
                File output = outputFile(input, "_clean" + (name.endsWith(".pdf") ? ".pdf" : ".epub"));

                if (name.endsWith(".pdf")) {
                    // 1. Очистка водяных знаков
                    System.out.println("Шаг 1: Очистка...");
                    pdfCleaner.clean(input, output);

                    // 2. ВСЕГДА показываем окно выбора размера
                    System.out.println("Шаг 2: Подготовка превью...");

                    // Генерируем превью
                    BufferedImage preview = mangaResizer.getPreviewImage(output);

                    // Если не удалось получить картинку (например, пустой PDF), просто пропускаем
                    if (preview != null) {
                        AtomicReference<CropMode> selectedMode = new AtomicReference<>(CropMode.SKIP);

                        // Показываем окно в потоке интерфейса
                        SwingUtilities.invokeAndWait(() -> {
                            CropMode choice = showResizeDialog(preview, input.getName());
                            selectedMode.set(choice);
                        });

                        // Если пользователь выбрал что-то кроме "Оставить как есть"
                        if (selectedMode.get() != CropMode.SKIP) {
                            System.out.println("Применяем ресайз: " + selectedMode.get());
                            mangaResizer.applyResize(output, selectedMode.get());
                        }
                    }

                } else if (name.endsWith(".epub")) {
                    epubCleaner.clean(input, output);
                }
                return output;
            }

            @Override
            protected void done() {
                try {
                    File result = get();
                    statusLabel.setText("Готово!");
                    JOptionPane.showMessageDialog(MangaCleanerApp.this, "Файл готов:\n" + result.getName());
                } catch (Exception e) {
                    showError(e);
                }
            }
        }.execute();
    }

    private CropMode showResizeDialog(BufferedImage image, String filename) {
        JDialog dialog = new JDialog(this, "Настройка страницы", true);
        dialog.setLayout(new BorderLayout());

        // Показываем картинку
        if (image != null) {
            int h = 500; // Высота окна превью
            double s = (double) h / image.getHeight();
            int w = (int) (image.getWidth() * s);
            Image scaled = image.getScaledInstance(w, h, Image.SCALE_SMOOTH);
            dialog.add(new JScrollPane(new JLabel(new ImageIcon(scaled))), BorderLayout.CENTER);
        }

        // Панель кнопок
        JPanel btnPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        btnPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        btnPanel.add(new JLabel("<html><b>" + filename + "</b><br>Выберите действие:</html>"));

        JButton b1 = new JButton("По ширине (Стандарт)");
        JButton b2 = new JButton("По высоте");
        JButton b3 = new JButton("Растянуть (Весь экран)");
        JButton b4 = new JButton("Оставить как есть"); // Кнопка пропуска

        // Зеленая подсветка для "По ширине"
        b1.setBackground(new Color(220, 255, 220));

        final CropMode[] res = {CropMode.SKIP};

        b1.addActionListener(e -> { res[0] = CropMode.FIT_WIDTH; dialog.dispose(); });
        b2.addActionListener(e -> { res[0] = CropMode.FIT_HEIGHT; dialog.dispose(); });
        b3.addActionListener(e -> { res[0] = CropMode.STRETCH; dialog.dispose(); });
        b4.addActionListener(e -> { res[0] = CropMode.SKIP; dialog.dispose(); });

        btnPanel.add(b1);
        btnPanel.add(b2);
        btnPanel.add(b3);
        btnPanel.add(new JSeparator());
        btnPanel.add(b4);

        dialog.add(btnPanel, BorderLayout.EAST);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        return res[0];
    }

    private File outputFile(File input, String suffix) {
        String name = input.getName();
        int dot = name.lastIndexOf(".");
        return new File(input.getParent(), (dot == -1 ? name : name.substring(0, dot)) + suffix);
    }

    private void showError(Exception e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(this, "Ошибка: " + e.getMessage());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MangaCleanerApp().setVisible(true));
    }
}