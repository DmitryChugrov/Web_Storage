package com.web_storage.web_storage.service;

import com.web_storage.web_storage.model.FileEntity;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FileService {

    private final Path rootLocation = Paths.get("uploads");

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String FILE_ID_COUNTER_KEY = "file:id:counter";

    @PostConstruct
    public void initializeCounter() {
        if (redisTemplate.opsForValue().get(FILE_ID_COUNTER_KEY) == null) {
            redisTemplate.opsForValue().set(FILE_ID_COUNTER_KEY, 0);
        }
    }

    private Long generateFileId() {
        return redisTemplate.opsForValue().increment(FILE_ID_COUNTER_KEY);
    }

    public void createFolder(String user, String folderName) {
        redisTemplate.opsForSet().add("folders:" + user, folderName);

        try {
            Path folderPath = rootLocation.resolve(Paths.get(folderName)).normalize().toAbsolutePath();
            if (!Files.exists(folderPath)) {
                Files.createDirectories(folderPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not create folder on disk", e);
        }
    }


    public void saveFile(String user, MultipartFile file, String folder) throws IOException {
        String key = "folder:" + user + ":" + folder;
        Path folderPath = rootLocation.resolve(Paths.get(folder)).normalize().toAbsolutePath();

        if (!Files.exists(folderPath)) {
            Files.createDirectories(folderPath);
        }

        Path destinationFile = folderPath.resolve(Paths.get(file.getOriginalFilename())).normalize().toAbsolutePath();
        if (!destinationFile.getParent().equals(folderPath)) {
            throw new RuntimeException("Cannot store file outside current directory");
        }

        Files.copy(file.getInputStream(), destinationFile);

        FileEntity fileEntity = new FileEntity();
        fileEntity.setId(generateFileId());
        fileEntity.setUser(user);
        fileEntity.setPathFolder(folder);
        fileEntity.setFileName(file.getOriginalFilename());
        fileEntity.setFilePath(destinationFile.toString());
        fileEntity.setFileType(file.getContentType());
        fileEntity.setFileSize(file.getSize() / (1024 * 1024));

        redisTemplate.opsForSet().add(key, fileEntity);
    }

    public List<String> getFoldersByUser(String user) {
        Set<Object> folders = redisTemplate.opsForSet().members("folders:" + user);
        return folders.stream().map(Object::toString).collect(Collectors.toList());
    }

    public List<FileEntity> getFilesByUserAndFolder(String user, String folder) {
        String key = "folder:" + user + ":" + folder;
        Set<Object> files = redisTemplate.opsForSet().members(key);
        return files.stream().map(file -> (FileEntity) file).collect(Collectors.toList());
    }


    public Path getFilePath(Long fileId, String user, String folder) {
        String key = "folder:" + user + ":" + folder;
        Set<Object> files = redisTemplate.opsForSet().members(key);

        for (Object fileObj : files) {
            FileEntity fileEntity = (FileEntity) fileObj;
            if (fileEntity.getId().equals(fileId)) {
                return Paths.get(fileEntity.getFilePath());
            }
        }

        throw new RuntimeException("File not found or access denied.");
    }

    public void deleteFolder(String user, String folderName) {
        String key = "folder:" + user + ":" + folderName;

        Set<Object> files = redisTemplate.opsForSet().members(key);
        if (files != null) {
            for (Object file : files) {
                if (file instanceof FileEntity) {
                    Path path = Paths.get(((FileEntity) file).getFilePath());
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new RuntimeException("Could not delete file: " + path, e);
                    }
                }
            }
        }

        redisTemplate.delete(key);
        redisTemplate.opsForSet().remove("folders:" + user, folderName);

        Path folderPath = rootLocation.resolve(Paths.get(folderName)).normalize().toAbsolutePath();
        try {
            if (Files.exists(folderPath)) {
                Files.walk(folderPath)
                        .sorted((p1, p2) -> p2.compareTo(p1))
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not delete folder on disk", e);
        }
    }

    public void deleteFile(Long fileId, String folderName) {
        String key = "folder:" + getCurrentUsername() + ":" + folderName; // Убедитесь, что ключ включает имя пользователя
        Set<Object> files = redisTemplate.opsForSet().members(key);

        if (files == null || files.isEmpty()) {
            throw new RuntimeException("No files found in folder.");
        }

        FileEntity fileEntityToDelete = null;

        for (Object fileObj : files) {
            if (fileObj instanceof FileEntity) {
                FileEntity fileEntity = (FileEntity) fileObj;
                if (fileEntity.getId().equals(fileId)) {
                    fileEntityToDelete = fileEntity;
                    break;
                }
            }
        }

        if (fileEntityToDelete == null) {
            throw new RuntimeException("File with ID " + fileId + " not found.");
        }

        Path filePath = Paths.get(fileEntityToDelete.getFilePath());
        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                System.out.println("File deleted from disk: " + filePath);
            } else {
                System.out.println("File does not exist on disk: " + filePath);
            }
            redisTemplate.opsForSet().remove(key, fileEntityToDelete);
            System.out.println("File removed from Redis: " + fileEntityToDelete);

        } catch (IOException e) {
            throw new RuntimeException("Could not delete file from disk", e);
        } catch (Exception e) {
            throw new RuntimeException("Could not delete file from Redis", e);
        }
    }

    private boolean hasAnyRole(Set<String> roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            Set<String> userRoles = userDetails.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .collect(Collectors.toSet());
            return !Collections.disjoint(userRoles, roles);
        }
        return false;
    }

    public boolean isOwner() {
        return hasAnyRole(Set.of("ROLE_OWNER"));
    }

    public boolean isAdmin() {
        return hasAnyRole(Set.of("ROLE_ADMIN"));
    }

    @Transactional
    public void deleteFolderForOwner(String user, String folderName) {
        if (!isOwner()) {
            throw new SecurityException("You do not have permission to delete this folder.");
        }
        deleteFolder(user, folderName);
    }

    @Transactional
    public void deleteFileForAdminOrOwner(Long fileId, String folderName) {
        if (!isOwner() && !isAdmin()) {
            throw new SecurityException("You do not have permission to delete this file.");
        }
        deleteFile(fileId, folderName);
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }

    public List<FolderInfo> getAllFoldersWithOwners() {
        Set<String> folderKeys = redisTemplate.keys("folders:*");

        List<FolderInfo> folderInfos = new ArrayList<>();

        if (folderKeys != null) {
            for (String folderKey : folderKeys) {
                String user = folderKey.split(":")[1];
                Set<Object> folderNames = redisTemplate.opsForSet().members(folderKey);

                if (folderNames != null) {
                    for (Object folderNameObj : folderNames) {
                        String folderName = folderNameObj.toString();
                        String fileKey = "folder:" + user + ":" + folderName;
                        Set<Object> files = redisTemplate.opsForSet().members(fileKey);

                        // Добавляем информацию о папке, даже если файлов нет
                        folderInfos.add(new FolderInfo(folderName, user));
                    }
                }
            }
        }

        return folderInfos;
    }


    public static class FolderInfo {
        private String folderName;
        private String owner;

        public FolderInfo(String folderName, String owner) {
            this.folderName = folderName;
            this.owner = owner;
        }

        public String getFolderName() {
            return folderName;
        }

        public String getOwner() {
            return owner;
        }
    }
}
