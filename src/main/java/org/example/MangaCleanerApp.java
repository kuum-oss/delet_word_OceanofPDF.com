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
        setTitle("Manga Cleaner v5.2 (Smart Output)");
        setSize(500, 350);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout());
        JLabel dropLabel = new JLabel("<html><center><h2>ПЕРЕТАЩИ ПАПКУ СЮДА</h2>Сохраню результат рядом с папкой</center></html>", SwingConstants.CENTER);

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

                if (droppedFiles.isEmpty()) return;

                // ОПРЕДЕЛЯЕМ ПАПКУ КУДА СОХРАНЯТЬ (Родительская папка)
                // Если кинули папку "C:/Манга/Том1", сохраним в "C:/Манга/"
                // Если кинули файл "C:/Манга/Том1/глава.pdf", сохраним в "C:/Манга/Том1/"
                File firstItem = droppedFiles.get(0);
                File outputDirectory;

                if (firstItem.isDirectory()) {
                    outputDirectory = firstItem.getParentFile();
                } else {
                    outputDirectory = firstItem.getParentFile();
                }

                // Собираем файлы
                List<File> allFiles = new ArrayList<>();
                for (File file : droppedFiles) {
                    collectFiles(file, allFiles);
                }

                if (!allFiles.isEmpty()) {
                    processBatchAsync(allFiles, outputDirectory);
                } else {
                    JOptionPane.showMessageDialog(MangaCleanerApp.this, "Не найдено PDF или EPUB файлов.");
                }

            } catch (Exception e) { showError(e); }
        }
    }

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

    private void processBatchAsync(List<File> inputs, File outputDir) {
        progressBar.setVisible(true);
        progressBar.setMaximum(inputs.size());
        progressBar.setValue(0);

        new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                AtomicReference<CropMode> batchMode = new AtomicReference<>(null);

                for (int i = 0; i < inputs.size(); i++) {
                    File input = inputs.get(i);
                    publish("Обработка (" + (i + 1) + "/" + inputs.size() + "): " + input.getName());

                    // Создаем файл в ЦЕЛЕВОЙ папке (рядом с исходной папкой)
                    File output = createCleanFile(input, outputDir);

                    try {
                        String name = input.getName().toLowerCase();

                        if (name.endsWith(".pdf")) {
                            pdfCleaner.clean(input, output);

                            if (batchMode.get() == null) {
                                BufferedImage preview = mangaResizer.getPreviewImage(output);
                                if (preview != null) {
                                    SwingUtilities.invokeAndWait(() -> {
                                        CropMode choice = showResizeDialog(preview, input.getName(), inputs.size());
                                        batchMode.set(choice);
                                    });
                                } else {
                                    batchMode.set(CropMode.SKIP);
                                }
                            }

                            if (batchMode.get() != CropMode.SKIP) {
                                mangaResizer.applyResize(output, batchMode.get());
                            }

                        } else if (name.endsWith(".epub")) {
                            epubCleaner.clean(input, output);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

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
                statusLabel.setText("Готово! Файлы сохранены в: " + outputDir.getName());
                progressBar.setVisible(false);
                JOptionPane.showMessageDialog(MangaCleanerApp.this,
                        "Готово!\nФайлы сохранены в папку:\n" + outputDir.getAbsolutePath());
            }
        }.execute();
    }

    // Теперь метод принимает папку назначения
    private File createCleanFile(File input, File targetDir) {
        String originalName = input.getName();
        String namePart;
        String extPart;

        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex >= 0) {
            namePart = originalName.substring(0, dotIndex);
            extPart = originalName.substring(dotIndex);
        } else {
            namePart = originalName;
            extPart = "";
        }

        // Чтобы избежать дублирования имени, если файл уже там есть, можно добавить счетчик,
        // но пока просто _clean
        return new File(targetDir, namePart + "_clean" + extPart);
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
                "Применить ко всем <b>" + totalFiles + "</b> файлам?</html>";
        btnPanel.add(new JLabel(headerText));

        JButton b1 = new JButton("По ширине (Рекомендую)");
        JButton b2 = new JButton("По высоте");
        JButton b3 = new JButton("Растянуть");
        JButton b4 = new JButton("Только очистка (Без ресайза)");

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

    private void showError(Exception e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(this, "Ошибка: " + e.getMessage());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MangaCleanerApp().setVisible(true));
    }
}