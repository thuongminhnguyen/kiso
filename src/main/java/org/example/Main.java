package org.example;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

public class Main {
    public static void main(String[] args) {
        String pdfPath = "E:/Project/kiso/src/main/resources/LTI25261001889.ST12_0902eab991fa05ae.pdf";
        // Định nghĩa vùng cần tìm, giữ nguyên xuống dòng
        String[] targets = {
                "KHÁCH HÀNG",
                "Tôi/Chúng tôi đã đọc, hiểu rõ và đồng ý giao kết, thực",
                "hiện đúng toàn bộ nội dung của Hợp đồng (bao gồm",
                "cả Điều kiện giao dịch chung của Hợp đồng này)."
        };


        File input = new File(pdfPath);
        if (!input.exists()) {
            System.err.println("Không tìm thấy file PDF: " + input.getAbsolutePath());
            return;
        }
        try (PDDocument doc = PDDocument.load(input)) {
            MarkingTextStripper stripper = new MarkingTextStripper(targets);
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(doc.getNumberOfPages());
            stripper.getText(doc);
            List<FoundBox> boxes = stripper.getFoundBoxes();

            if (boxes.isEmpty()) {
                System.out.println("Không tìm thấy vùng văn bản cần detect trên file PDF.");
            }

            // Vẽ bounding box màu xanh lá cây lên file PDF mới và xuất ảnh PNG
            try (PDDocument outDoc = PDDocument.load(input)) {
                if (!boxes.isEmpty()) {
                    float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
                    float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
                    int pageIndex = boxes.get(0).pageIndex;
                    for (FoundBox fb : boxes) {
                        minX = Math.min(minX, fb.x);
                        minY = Math.min(minY, fb.y);
                        maxX = Math.max(maxX, fb.x + fb.width);
                        maxY = Math.max(maxY, fb.y + fb.height);
                    }
                    float bigWidth = maxX - minX;
                    float bigHeight = maxY - minY;
                    PDPage page = outDoc.getPage(pageIndex);
                    try (PDPageContentStream cs = new PDPageContentStream(outDoc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                        cs.setStrokingColor(0, 200, 0); // màu xanh lá cây
                        cs.setLineWidth(2.0f);
                        cs.addRect(minX, minY, bigWidth, bigHeight);
                        cs.stroke();
                    }
                    // In ra tọa độ 4 góc chi tiết của box tổng hợp
                    System.out.println("==============================");
                    System.out.printf("Trang %d: Detect vùng tổng hợp\n", pageIndex + 1);
                    System.out.printf("Bounding box tổng hợp: x=%.2f, y=%.2f, w=%.2f, h=%.2f\n", minX, minY, bigWidth, bigHeight);
                    System.out.println("Tọa độ 4 góc tổng hợp:");
                    System.out.printf("  Góc trái dưới: (%.2f, %.2f)\n", minX, minY);
                    System.out.printf("  Góc phải dưới: (%.2f, %.2f)\n", minX + bigWidth, minY);
                    System.out.printf("  Góc phải trên: (%.2f, %.2f)\n", minX + bigWidth, minY + bigHeight);
                    System.out.printf("  Góc trái trên: (%.2f, %.2f)\n", minX, minY + bigHeight);
                    System.out.println("==============================");
                }


                File out = new File(input.getParentFile(), "output-marked.pdf");
                outDoc.save(out);
                System.out.println("Đã vẽ bounding box màu xanh lá cây và lưu ra: " + out.getAbsolutePath());
                // Render trang PDF có bounding box ra PNG
//                try {
//                    org.apache.pdfbox.rendering.PDFRenderer renderer = new org.apache.pdfbox.rendering.PDFRenderer(outDoc);
//                    java.awt.image.BufferedImage image = renderer.renderImageWithDPI(0, 150);
//                    javax.imageio.ImageIO.write(image, "png", new File(input.getParentFile(), "output-marked.png"));
//                    System.out.println("Đã xuất ảnh bounding box ra: " + new File(input.getParentFile(), "output-marked.png").getAbsolutePath());
//                } catch (Exception ex) {
//                    System.err.println("Lỗi khi xuất ảnh PNG: " + ex.getMessage());
//                }
            }
            // In ra tọa độ 4 góc chi tiết
//            for (FoundBox fb : boxes) {
//                System.out.println("------------------------------");
//                System.out.printf("Trang %d: Detect vùng: %s\n", fb.pageIndex + 1, fb.text.replace("\n", " | "));
//                System.out.printf("Bounding box: x=%.2f, y=%.2f, w=%.2f, h=%.2f\n", fb.x, fb.y, fb.width, fb.height);
//                System.out.println("Tọa độ 4 góc:");
//                System.out.printf("  Góc trái dưới: (%.2f, %.2f)\n", fb.x, fb.y);
//                System.out.printf("  Góc phải dưới: (%.2f, %.2f)\n", fb.x + fb.width, fb.y);
//                System.out.printf("  Góc phải trên: (%.2f, %.2f)\n", fb.x + fb.width, fb.y + fb.height);
//                System.out.printf("  Góc trái trên: (%.2f, %.2f)\n", fb.x, fb.y + fb.height);
//            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class FoundBox {
        int pageIndex;
        float x, y, width, height;
        String text;
        FoundBox(int pageIndex, float x, float y, float width, float height, String text) {
            this.pageIndex = pageIndex;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.text = text;
        }
    }

    static class MarkingTextStripper extends PDFTextStripper {
        private final String[] targets;
        private final List<FoundBox> found = new ArrayList<>();
        private final StringBuilder pageChars = new StringBuilder();
        private final List<TextPosition> charPositions = new ArrayList<>();
        private PDPage currentPage;
        private int currentPageIndex = -1;

        MarkingTextStripper(String[] targets) throws IOException {
            super();
            this.targets = targets;
        }
        List<FoundBox> getFoundBoxes() { return found; }
        @Override
        protected void startPage(PDPage page) throws IOException {
            super.startPage(page);
            pageChars.setLength(0);
            charPositions.clear();
            currentPage = page;
            currentPageIndex = getCurrentPageNo() - 1;
        }
        @Override
        protected void endPage(PDPage page) throws IOException {
            super.endPage(page);
            if (pageChars.length() == 0) return;
            String full = pageChars.toString(); // giữ nguyên xuống dòng
            float pageHeight = page.getMediaBox().getHeight();
            for (String t : targets) {
                int fromIndex = 0;
                while (true) {
                    int idx = full.indexOf(t, fromIndex);
                    if (idx < 0) break;
                    int start = idx;
                    int end = idx + t.length();
                    float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
                    float minY = Float.MAX_VALUE, maxY = Float.MIN_VALUE;
                    for (int i = start; i < end; i++) {
                        TextPosition tp = charPositions.get(i);
                        float x = tp.getXDirAdj();
                        float w = tp.getWidthDirAdj();
                        float yTop = tp.getYDirAdj();
                        float h = tp.getHeightDir();
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x + w);
                        minY = Math.min(minY, yTop);
                        maxY = Math.max(maxY, yTop + h);
                    }
                    if (minX < Float.MAX_VALUE) {
                        float yBottom = pageHeight - maxY;
                        float width = maxX - minX;
                        float height = maxY - minY;
                        FoundBox fb = new FoundBox(currentPageIndex, minX, yBottom, width, height, t);
                        found.add(fb);
                    }
                    fromIndex = end;
                }
            }
        }
        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            for (int i = 0; i < text.length(); i++) {
                pageChars.append(text.charAt(i));
                if (i < textPositions.size()) {
                    charPositions.add(textPositions.get(i));
                } else if (!textPositions.isEmpty()) {
                    charPositions.add(textPositions.get(textPositions.size() - 1));
                }
            }
            super.writeString(text, textPositions);
        }
    }
}
