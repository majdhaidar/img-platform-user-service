package com.users.service.services.objectstore.impl;

import com.users.service.exceptions.InvalidFileTypeException;
import com.users.service.services.objectstore.ObjectStorageService;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ObjectStorageServiceImpl implements ObjectStorageService {


    @Value("${minio.bucket}")
    private String bucketName;

    private final MinioClient minioClient;

    //Allowed image types
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp");

    //Maximum file bytes (5MB)
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;


    //maximum image dimensions
    private static final int MAX_WIDTH = 2048;
    private static final int MAX_HEIGHT = 2048;


    //Compression quality (0.0-1.0)
    private static final float COMPRESSION_QUALITY = 0.75f;


    @Override
    public void init() {
        try {
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created bucket: {}", bucketName);
            }
        }
        catch (Exception e) {
            log.error("Error initializing MinIO bucket: {}", e.getMessage(), e);
            throw new RuntimeException("Could not initialize storage", e);
        }
    }


    @Override
    public String uploadProfilePicture(String userId, MultipartFile file) {

        validateImageFile(file);

        try {
            // Process and compress the image
            byte[] processedImageBytes = processAndCompressImage(file);

            // Create unique object name
            String objectName = "profile-pictures/" + userId + "/" + UUID.randomUUID() + getExtension(file.getOriginalFilename());

            // Upload processed image to MinIO
            InputStream inputStream = new ByteArrayInputStream(processedImageBytes);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, processedImageBytes.length, -1)
                            .contentType(file.getContentType())
                            .build()
            );

            log.info("Uploaded profile picture for user: {}, object: {}", userId, objectName);
            String objectUrl = "/" + bucketName + "/" + objectName;

            return objectUrl;
        }
        catch (Exception e) {
            log.error("Could not upload profile picture: {}", e.getMessage(), e);
            throw new RuntimeException("Could not upload profile picture", e);
        }

    }

    private String getExtension(String filename) {
        if (filename == null) {
            return ".jpg";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex < 0) {
            return ".jpg";
        }
        return filename.substring(lastDotIndex);
    }

    private byte[] processAndCompressImage(MultipartFile file) throws IOException {
        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        log.info("Resize image if needed");
        BufferedImage resizedImage = resizeImage(originalImage);
        return compressImage(resizedImage, getFormatName(file.getContentType()));
    }

    private String getFormatName(String contentType) {
        if (contentType == null) {
            return "jpeg";
        }

        switch (contentType.toLowerCase()) {
            case "image/jpeg":
                return "jpeg";
            case "image/png":
                return "png";
            case "image/gif":
                return "gif";
            case "image/bmp":
                return "bmp";
            case "image/webp":
                return "webp";
            default:
                return "jpeg";
        }
    }

    private byte[] compressImage(BufferedImage image, String formatName) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Special handling for JPEG compression
        if ("jpeg".equalsIgnoreCase(formatName) || "jpg".equalsIgnoreCase(formatName)) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(formatName);
            if (!writers.hasNext()) {
                throw new IOException("No image writer found for format: " + formatName);
            }

            ImageWriter writer = writers.next();
            ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream);
            writer.setOutput(imageOutputStream);

            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(COMPRESSION_QUALITY);

            writer.write(null, new IIOImage(image, null, null), params);

            writer.dispose();
            imageOutputStream.close();
        }
        else {
            // For other formats like PNG that don't support quality compression in the same way
            ImageIO.write(image, formatName, outputStream);
        }

        return outputStream.toByteArray();
    }

    private BufferedImage resizeImage(BufferedImage originalImage) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        log.info("validate images allowed max width and height");
        if (originalWidth <= MAX_WIDTH && originalHeight <= MAX_HEIGHT) {
            return originalImage;
        }

        log.info("Calculate new dimensions while maintaining aspect ratio");
        double aspectRatio = (double) originalWidth / originalHeight;
        int newWidth, newHeight;

        if (originalWidth > originalHeight) {
            newWidth = MAX_WIDTH;
            newHeight = (int) (MAX_WIDTH / aspectRatio);
        }
        else {
            newHeight = MAX_HEIGHT;
            newWidth = (int) (MAX_HEIGHT / aspectRatio);
        }

        log.info("Create new resized image");
        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, originalImage.getType());
        Graphics2D g = resizedImage.createGraphics();

        // Use better quality rendering hints
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g.dispose();

        log.info("Resized image from {}x{} to {}x{}", originalWidth, originalHeight, newWidth, newHeight);
        return resizedImage;
    }

    private void validateImageFile(MultipartFile file) {
        //check if file is empty
        if (file.isEmpty()) {
            throw new InvalidFileTypeException("File is empty");
        }

        //check file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidFileTypeException("File is too large");
        }

        //check content type
        String contentType = file.getContentType();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidFileTypeException("Only image files are supported (JPEG, PNG, GIF, BMP, WebP)");
        }

        // additional validation try to read as image
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new InvalidFileTypeException("Invalid image file");
            }
        }
        catch (IOException e) {
            log.error("Error reading image file: {}", e.getMessage(), e);
            throw new InvalidFileTypeException("Error reading image file");
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex == -1) {
            return "";
        }
        return fileName.substring(lastDotIndex);
    }

    @Override
    public void deleteProfilePicture(String objectUrl) {
        try {
            String objectName = extractObjectNameFromUrl(objectUrl);
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to delete profile picture", e);
        }
    }

    private String extractObjectNameFromUrl(String url) {
        return url.substring(url.indexOf(bucketName) + bucketName.length() + 1);
    }
}
