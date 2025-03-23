package com.users.service.services.objectstore;

import org.springframework.web.multipart.MultipartFile;

public interface ObjectStorageService {
    void init();
    String uploadProfilePicture(String userId, MultipartFile file);
    void deleteProfilePicture(String objectUrl);
}
