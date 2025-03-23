package com.users.service.services.objectstore.impl;

import com.users.service.services.objectstore.ObjectStorageService;
import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@Slf4j
public class ObjectStorageServiceImpl implements ObjectStorageService {

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket}")
    private String bucketName;

    private MinioClient minioClient;

    @PostConstruct
    @Override
    public void init() {
        minioClient = MinioClient.builder().endpoint(minioEndpoint).credentials(accessKey, secretKey).build();
        createBucketIfNotExists();
    }

    private void createBucketIfNotExists() {
        try {
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucketName)
                        .build());
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to create bucket", e);
        }
    }

    @Override
    public String uploadProfilePicture(String userId, MultipartFile file) {
        try {
            log.info("generate a unique filename with userId: " + userId);
            String extension = getFileExtension(file.getOriginalFilename());
            String fileName = "profile-pictures/" + userId + "-" + UUID.randomUUID().toString() + extension;
            log.info("uploading profile picture: " + fileName);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            return minioEndpoint + "/" + bucketName + "/" + fileName;
        }
        catch (Exception e) {
            throw new RuntimeException("Unable to upload profile picture", e);
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
