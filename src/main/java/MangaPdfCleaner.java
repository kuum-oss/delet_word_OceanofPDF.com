import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.util.List;

public class MangaPdfCleaner extends JFrame {

    public MangaPdfCleaner() {
        setTitle("Manga PDF Cleaner");
        setSize(420, 200);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JLabel label = new JLabel(
                "<html><center>Перетащи PDF манги сюда<br/>OceanofPDF.com будет скрыт</center></html>",
                SwingConstants.CENTER
        );
        label.setFont(new Font("Arial", Font.BOLD, 15));
        label.setBorder(BorderFactory.createDashedBorder(Color.GRAY));

        new DropTarget(label, new DropHandler());
        add(label);
    }

    class DropHandler extends DropTargetAdapter {
        @Override
        public void drop(DropTargetDropEvent dtde) {
            try {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);

                List<File> files = (List<File>) dtde
                        .getTransferable()
                        .getTransferData(DataFlavor.javaFileListFlavor);

                for (File file : files) {
                    if (file.getName().toLowerCase().endsWith(".pdf")) {
                        cleanPdf(file);
                        JOptionPane.showMessageDialog(
                                MangaPdfCleaner.this,
                                "Готово:\n" + file.getName()
                        );
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(
                        MangaPdfCleaner.this,
                        "Ошибка: " + e.getMessage(),
                        "Ошибка",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private void cleanPdf(File input) throws Exception {
        File output = new File(
                input.getParent(),
                input.getName().replace(".pdf", "_clean.pdf")
        );

        try (PDDocument doc = PDDocument.load(input)) {

            PDFTextStripper stripper = new PDFTextStripper();

            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                PDPage page = doc.getPage(i);

                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);

                String text = stripper.getText(doc).toLowerCase();

                // 🎯 СТРОГО OceanofPDF.com
                if (!text.contains("OceanofPDF.com")) {
                    continue;
                }

                PDRectangle box = page.getMediaBox();

                try (PDPageContentStream cs =
                             new PDPageContentStream(
                                     doc,
                                     page,
                                     AppendMode.APPEND,
                                     true,
                                     true)) {

                    cs.setNonStrokingColor(Color.WHITE);

                    // 📍 Центр страницы (watermark OceanofPDF.com)
                    cs.addRect(
                            box.getWidth() / 2 - 160,
                            box.getHeight() / 2 - 25,
                            320,
                            50
                    );
                    cs.fill();
                }
            }

            doc.save(output);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() ->
                new MangaPdfCleaner().setVisible(true)
        );
    }
}
