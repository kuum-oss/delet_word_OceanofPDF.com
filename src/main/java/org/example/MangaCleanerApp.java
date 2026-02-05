package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class MangaCleanerApp extends JFrame {

    private final PdfWatermarkCleaner pdfCleaner = new PdfWatermarkCleaner();
    private final EpubWatermarkCleaner epubCleaner = new EpubWatermarkCleaner();
    private final MangaResizer mangaResizer = new MangaResizer();

    private final JLabel statusLabel;
    private final JProgressBar progressBar;

    public MangaCleanerApp() {
        setTitle("Manga Cleaner v5.0 (Batch Mode)");
        setSize(500, 350);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout());
        JLabel dropLabel = new JLabel("<html><center><h2>ПЕРЕТАЩИ ПАПКУ ИЛИ ФАЙЛЫ</h2>Обработаю всё сразу</center></html>", SwingConstants.CENTER);

        // Панель статуса с прогресс-баром
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Готов к работе", SwingConstants.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        statusPanel.add(statusLabel, BorderLayout.NORTH);
        statusPanel.add(progressBar, BorderLayout.SOUTH);

        panel.add(dropLabel, BorderLayout.CENTER);
        panel.add(statusPanel, BorderLayout.SOUTH);

        new DropTarget(panel, new FileDropHandler());
        add(panel);
    }

    private class FileDropHandler extends DropTargetAdapter {
        @Override
        public void drop(DropTargetDropEvent event) {
            try {
                event.acceptDrop(DnDConstants.ACTION_COPY);
                List<File> droppedFiles = (List<File>) event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);

                // Собираем все файлы (раскрываем папки)
                List<File> allFiles = new ArrayList<>();
                for (File file : droppedFiles) {
                    collectFiles(file, allFiles);
                }

                if (!allFiles.isEmpty()) {
                    processBatchAsync(allFiles);
                } else {
                    JOptionPane.showMessageDialog(MangaCleanerApp.this, "Не найдено PDF или EPUB файлов.");
                }

            } catch (Exception e) { showError(e); }
        }
    }

    // Рекурсивный поиск файлов в папках
    private void collectFiles(File root, List<File> result) {
        if (root.isDirectory()) {
            File[] children = root.listFiles();
            if (children != null) {
                for (File child : children) collectFiles(child, result);
            }
        } else {
            String name = root.getName().toLowerCase();
            if (name.endsWith(".pdf") || name.endsWith(".epub")) {
                result.add(root);
            }
        }
    }

    private void processBatchAsync(List<File> inputs) {
        progressBar.setVisible(true);
        progressBar.setMaximum(inputs.size());
        progressBar.setValue(0);

        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Переменная для хранения выбора пользователя (один раз на всю пачку)
                AtomicReference<CropMode> batchMode = new AtomicReference<>(null);

                for (int i = 0; i < inputs.size(); i++) {
                    File input = inputs.get(i);
                    publish("Обработка (" + (i + 1) + "/" + inputs.size() + "): " + input.getName());

                    String name = input.getName().toLowerCase();
                    File output = outputFile(input, "_clean" + (name.endsWith(".pdf") ? ".pdf" : ".epub"));

                    try {
                        if (name.endsWith(".pdf")) {
                            // 1. Очистка
                            pdfCleaner.clean(input, output);

                            // 2. Ресайз
                            // Если мы еще не спрашивали пользователя (batchMode == null), спрашиваем сейчас
                            if (batchMode.get() == null) {
                                BufferedImage preview = mangaResizer.getPreviewImage(output);
                                if (preview != null) {
                                    SwingUtilities.invokeAndWait(() -> {
                                        // Показываем диалог
                                        CropMode choice = showResizeDialog(preview, input.getName(), inputs.size());
                                        batchMode.set(choice);
                                    });
                                } else {
                                    // Если превью не вышло, ставим SKIP по умолчанию, чтобы не зависло
                                    batchMode.set(CropMode.SKIP);
                                }
                            }

                            // Применяем выбранный режим (или тот, который только что выбрали)
                            if (batchMode.get() != CropMode.SKIP) {
                                mangaResizer.applyResize(output, batchMode.get());
                            }

                        } else if (name.endsWith(".epub")) {
                            epubCleaner.clean(input, output);
                        }
                    } catch (Exception e) {
                        e.printStackTrace(); // Логируем ошибку, но не останавливаем очередь
                    }

                    // Обновляем прогресс бар
                    int progress = i + 1;
                    SwingUtilities.invokeLater(() -> progressBar.setValue(progress));
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                statusLabel.setText(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                statusLabel.setText("Готово! Обработано файлов: " + inputs.size());
                progressBar.setVisible(false);
                JOptionPane.showMessageDialog(MangaCleanerApp.this, "Пакетная обработка завершена!");
            }
        }.execute();
    }

    private CropMode showResizeDialog(BufferedImage image, String filename, int totalFiles) {
        JDialog dialog = new JDialog(this, "Настройка пакетной обработки", true);
        dialog.setLayout(new BorderLayout());

        if (image != null) {
            int h = 500;
            double s = (double) h / image.getHeight();
            int w = (int) (image.getWidth() * s);
            Image scaled = image.getScaledInstance(w, h, Image.SCALE_SMOOTH);
            dialog.add(new JScrollPane(new JLabel(new ImageIcon(scaled))), BorderLayout.CENTER);
        }

        JPanel btnPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        btnPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        String headerText = "<html><b>" + filename + "</b><br>" +
                "Этот выбор применится ко всем <b>" + totalFiles + "</b> файлам в очереди!</html>";
        btnPanel.add(new JLabel(headerText));

        JButton b1 = new JButton("По ширине (Для всей папки)");
        JButton b2 = new JButton("По высоте (Для всей папки)");
        JButton b3 = new JButton("Растянуть (Для всей папки)");
        JButton b4 = new JButton("Оставить как есть (Все файлы)");

        b1.setBackground(new Color(220, 255, 220));

        final CropMode[] res = {CropMode.SKIP};

        b1.addActionListener(e -> { res[0] = CropMode.FIT_WIDTH; dialog.dispose(); });
        b2.addActionListener(e -> { res[0] = CropMode.FIT_HEIGHT; dialog.dispose(); });
        b3.addActionListener(e -> { res[0] = CropMode.STRETCH; dialog.dispose(); });
        b4.addActionListener(e -> { res[0] = CropMode.SKIP; dialog.dispose(); });

        btnPanel.add(b1); btnPanel.add(b2); btnPanel.add(b3); btnPanel.add(new JSeparator()); btnPanel.add(b4);

        dialog.add(btnPanel, BorderLayout.EAST);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        return res[0];
    }

    private File outputFile(File input, String suffix) {
        String name = input.getName();
        int dot = name.lastIndexOf(".");
        String base = (dot == -1) ? name : name.substring(0, dot);
        return new File(input.getParent(), base + suffix);
    }

    private void showError(Exception e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(this, "Ошибка: " + e.getMessage());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MangaCleanerApp().setVisible(true));
    }
}