package com.serverless;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

public class PDF {
    public PDDocument document;

    public PDF(File file) throws IOException {
        this.document = PDDocument.load(file);
    }

    public File convertToImage(int pageNum, String extension) throws IOException {
        String random = UUID.randomUUID().toString();
        PDFRenderer renderer = new PDFRenderer(document);
        BufferedImage image = renderer.renderImage(pageNum, 3);
        File file = new File(String.format("/tmp/%s.%s", random, extension));
        ImageIO.write(image, extension, file);
        this.document.close();
        return file;
    }
}
