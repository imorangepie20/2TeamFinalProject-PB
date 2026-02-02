package com.springboot.finalprojcet.domain.common.service.impl;

import com.springboot.finalprojcet.domain.common.service.ImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class ImageServiceImpl implements ImageService {

    private final Path uploadDir;

    public ImageServiceImpl() {
        // Base upload directory in the project root
        this.uploadDir = Paths.get(System.getProperty("user.dir"), "uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    @Override
    public String downloadImage(String imageUrl, String subDir) {
        if (!StringUtils.hasText(imageUrl) || imageUrl.startsWith("/")) {
            // Already local or empty
            return imageUrl;
        }

        try {
            // Create sub-directory if not exists
            Path targetDir = this.uploadDir.resolve(subDir);
            Files.createDirectories(targetDir);

            // Download and save with User-Agent to avoid 403 Forbidden (Apple Music)
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new URL(imageUrl).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            // Determine extension from Content-Type
            String contentType = connection.getContentType();
            String extension = "jpg"; // Default
            if (contentType != null) {
                if (contentType.contains("png"))
                    extension = "png";
                else if (contentType.contains("gif"))
                    extension = "gif";
                else if (contentType.contains("jpeg") || contentType.contains("jpg"))
                    extension = "jpg";
            } else {
                // Fallback to URL parsing if Content-Type is missing
                if (imageUrl.contains(".png"))
                    extension = "png";
                else if (imageUrl.contains(".gif"))
                    extension = "gif";
            }

            String filename = UUID.randomUUID().toString() + "." + extension;
            Path targetPath = targetDir.resolve(filename);

            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // Return relative path for DB
            // e.g., /uploads/playlists/filename.jpg
            return "/uploads/" + subDir + "/" + filename;

        } catch (IOException e) {
            log.error("Failed to download image from {}: {}", imageUrl, e.getMessage());
            // Fallback: return original URL if download fails so we don't break the UI
            // completely
            return imageUrl;
        }
    }
}
