package com.springboot.finalprojcet.domain.common.service.impl;

import com.springboot.finalprojcet.domain.common.service.ImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
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
    
    @Override
    public String createGridImage(List<String> imageUrls, String subDir) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return null;
        }
        
        try {
            // Create sub-directory if not exists
            Path targetDir = this.uploadDir.resolve(subDir);
            Files.createDirectories(targetDir);
            
            // 이미지 다운로드 (최대 4개)
            List<BufferedImage> images = new ArrayList<>();
            for (int i = 0; i < Math.min(4, imageUrls.size()); i++) {
                String url = imageUrls.get(i);
                if (url == null || url.isEmpty()) continue;
                
                try {
                    BufferedImage img = downloadBufferedImage(url);
                    if (img != null) {
                        images.add(img);
                    }
                } catch (Exception e) {
                    log.warn("Failed to download image {}: {}", url, e.getMessage());
                }
            }
            
            if (images.isEmpty()) {
                return null;
            }
            
            // 이미지가 1개면 그대로 사용
            if (images.size() == 1) {
                return saveBufferedImage(images.get(0), targetDir);
            }
            
            // 2x2 그리드 생성 (300x300 결과물)
            int gridSize = 300;
            int cellSize = gridSize / 2;
            BufferedImage combined = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = combined.createGraphics();
            
            // 안티앨리어싱
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            
            // 배경색 (이미지가 4개 미만일 때)
            g.setColor(new Color(30, 30, 30));
            g.fillRect(0, 0, gridSize, gridSize);
            
            // 2x2 배치
            int[][] positions = {{0, 0}, {cellSize, 0}, {0, cellSize}, {cellSize, cellSize}};
            for (int i = 0; i < Math.min(4, images.size()); i++) {
                BufferedImage img = images.get(i);
                // 정사각형으로 크롭 & 리사이즈
                BufferedImage cropped = cropToSquare(img);
                g.drawImage(cropped, positions[i][0], positions[i][1], cellSize, cellSize, null);
            }
            
            g.dispose();
            
            return saveBufferedImage(combined, targetDir);
            
        } catch (Exception e) {
            log.error("Failed to create grid image: {}", e.getMessage());
            return null;
        }
    }
    
    private BufferedImage downloadBufferedImage(String imageUrl) throws IOException {
        if (imageUrl.startsWith("/")) {
            // 로컬 파일
            Path localPath = Paths.get(System.getProperty("user.dir")).resolve(imageUrl.substring(1));
            if (Files.exists(localPath)) {
                return ImageIO.read(localPath.toFile());
            }
            return null;
        }
        
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new URL(imageUrl).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        
        try (InputStream in = connection.getInputStream()) {
            return ImageIO.read(in);
        }
    }
    
    private BufferedImage cropToSquare(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int size = Math.min(w, h);
        int x = (w - size) / 2;
        int y = (h - size) / 2;
        return img.getSubimage(x, y, size, size);
    }
    
    private String saveBufferedImage(BufferedImage img, Path targetDir) throws IOException {
        String filename = UUID.randomUUID().toString() + ".jpg";
        Path targetPath = targetDir.resolve(filename);
        ImageIO.write(img, "jpg", targetPath.toFile());
        return "/uploads/" + targetDir.getFileName().toString() + "/" + filename;
    }
}
