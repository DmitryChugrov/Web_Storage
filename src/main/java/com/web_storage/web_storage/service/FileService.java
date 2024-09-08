package com.web_storage.web_storage.service;

import com.web_storage.web_storage.model.FileEntity;
import com.web_storage.web_storage.model.FolderEntity;
import com.web_storage.web_storage.model.UserEntity;
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
    @Autowired
    private UserService userService;

    private static final String FILE_ID_COUNTER_KEY = "file:id:counter";
    private static final String FOLDER_ID_COUNTER_KEY = "folder:id:counter";

    @PostConstruct
    public void initializeFileCounter() {
        if (redisTemplate.opsForValue().get(FILE_ID_COUNTER_KEY) == null) {
            redisTemplate.opsForValue().set(FILE_ID_COUNTER_KEY, 0);
        }
    }
    @PostConstruct
    public void initializeFolderCounter() {
        if (redisTemplate.opsForValue().get(FOLDER_ID_COUNTER_KEY) == null) {
            redisTemplate.opsForValue().set(FOLDER_ID_COUNTER_KEY, 0);
        }
    }

    private Long generateFileId() {
        return redisTemplate.opsForValue().increment(FILE_ID_COUNTER_KEY);
    }
    private Long generateFolderId() {
        return redisTemplate.opsForValue().increment(FOLDER_ID_COUNTER_KEY);
    }


    public void createFolder(String user, String folderName) {
        UserEntity currentUser = userService.findByUsername(user);
        String folderPath = rootLocation.resolve(Paths.get(folderName)).normalize().toAbsolutePath().toString();
        FolderEntity folderEntity = new FolderEntity();
        folderEntity.setId(generateFolderId());
        folderEntity.setUser(user);
        folderEntity.setFolderName(folderName);
        folderEntity.setFolderPath(folderPath);
        folderEntity.setAccessLevel(currentUser.getAccessLevel());

        String key = "folders:" + user;
        redisTemplate.opsForSet().add(key, folderName);

        String folderInfoKey = "folder:" + user + ":" + folderName + ":info";
        redisTemplate.opsForValue().set(folderInfoKey, folderEntity);
    }

    public void saveFile(String folderOwner, MultipartFile file, String folder) throws IOException {
        UserEntity currentUser = userService.findByUsername(folderOwner);

        FolderEntity folderEntity = getFolderEntity(folderOwner, folder);
        if (folderEntity == null) {
            throw new RuntimeException("Folder not found for user: " + folderOwner + ", folder: " + folder);
        }

        Path folderPath = Paths.get(folderEntity.getFolderPath()).normalize().toAbsolutePath();
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
        fileEntity.setUser(getCurrentUsername());
        fileEntity.setPathFolder(folder);
        fileEntity.setFileName(file.getOriginalFilename());
        fileEntity.setFilePath(destinationFile.toString());
        fileEntity.setFileType(file.getContentType());

        double fileSizeInMB = file.getSize() / (1024.0 * 1024.0);
        double roundedFileSize = Math.round(fileSizeInMB * 100.0) / 100.0;

        fileEntity.setFileSize(roundedFileSize);

        fileEntity.setAccessLevel(folderEntity.getAccessLevel());

        String fileKey = "folder:" + folderOwner + ":" + folder;
        redisTemplate.opsForSet().add(fileKey, fileEntity);
    }


    public FolderEntity getFolderEntity(String user, String folderName) {
        String folderEntityKey = "folder:" + user + ":" + folderName + ":info";
        FolderEntity folderEntity = (FolderEntity) redisTemplate.opsForValue().get(folderEntityKey);

        if (folderEntity == null) {
            System.out.println("Folder " + folderName + " not found for user: " + user);
            return null;
        }

        return folderEntity;
    }

    public List<String> getFoldersByUser(String user) {
        Set<Object> folders = redisTemplate.opsForSet().members("folders:" + user);
        return folders.stream().map(Object::toString).collect(Collectors.toList());
    }

    public List<FileEntity> getFilesByUserAndFolder(String user, String folder) {
        String folderKey = "folder:" + user + ":" + folder;

        Set<Object> files = redisTemplate.opsForSet().members(folderKey);

        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        return files.stream()
                .filter(file -> file instanceof FileEntity)
                .map(file -> (FileEntity) file)
                .collect(Collectors.toList());
    }
    public Path getFilePath(Long fileId, String owner, String folder) {
        UserEntity currentUser = userService.findByUsername(getCurrentUsername());
        System.out.println(currentUser.toString());
        String key = "folder:" + owner + ":" + folder;
        Set<Object> files = redisTemplate.opsForSet().members(key);

        if (files == null || files.isEmpty()) {
            throw new RuntimeException("No files found in the specified folder or folder doesn't exist.");
        }

        for (Object fileObj : files) {
            if (!(fileObj instanceof FileEntity)) {
                throw new RuntimeException("Invalid object type found in Redis. Expected FileEntity.");
            }

            FileEntity fileEntity = (FileEntity) fileObj;
            if (fileEntity.getId().equals(fileId)) {
                if (fileEntity.getAccessLevel() > currentUser.getAccessLevel()) {

                    throw new SecurityException("Недостаточно прав для доступа к этому файлу");
                }
                return Paths.get(fileEntity.getFilePath());
            }
        }

        throw new RuntimeException("File not found or access denied.");
    }


    public void deleteFolder(String user, String folderName) {
        String folderKey = "folder:" + user + ":" + folderName;
        String folderInfoKey = folderKey + ":info";

        Set<Object> files = redisTemplate.opsForSet().members(folderKey);
        if (files != null) {
            for (Object file : files) {
                if (file instanceof FileEntity) {
                    FileEntity fileEntity = (FileEntity) file;
                    Path path = Paths.get(fileEntity.getFilePath());

                    try {
                        Files.deleteIfExists(path);

                        redisTemplate.opsForSet().remove(folderKey, fileEntity);
                    } catch (IOException e) {
                        throw new RuntimeException("Could not delete file: " + path, e);
                    }
                }
            }
        }

        redisTemplate.delete(folderKey);
        redisTemplate.opsForSet().remove("folders:" + user, folderName);

        redisTemplate.delete(folderInfoKey);

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
        String key = "folder:" + getCurrentUsername() + ":" + folderName;
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
//                System.out.println("File deleted from disk: " + filePath);
            } else {
//                System.out.println("File does not exist on disk: " + filePath);
            }
            redisTemplate.opsForSet().remove(key, fileEntityToDelete);
//            System.out.println("File removed from Redis: " + fileEntityToDelete);

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
    public void deleteFileForOwner(Long fileId, String folderName) {
        if (!isOwner()) {
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

                        String folderEntityKey = "folder:" + user + ":" + folderName + ":info";
                        FolderEntity folderEntity = (FolderEntity) redisTemplate.opsForValue().get(folderEntityKey);

                        int accessLevel = folderEntity != null ? folderEntity.getAccessLevel() : 0;

                        folderInfos.add(new FolderInfo(folderName, user, accessLevel));
                    }
                }
            }
        }

        return folderInfos;
    }
    public static String getAccessLevelString(int accessLevel) {
        switch (accessLevel) {
            case 1:
                return "Public";
            case 2:
                return "Secret";
            case 3:
                return "Top Secret";
        }
        return null;
    }


    public static class FolderInfo {
        private String folderName;
        private String owner;
        private int accessLevel;

        public FolderInfo(String folderName, String owner, int accessLevel) {
            this.folderName = folderName;
            this.owner = owner;
            this.accessLevel = accessLevel;
        }

        public String getFolderName() {
            return folderName;
        }

        public String getOwner() {
            return owner;
        }

        public int getAccessLevel() {
            return accessLevel;
        }

        public void setAccessLevel(int accessLevel) {
            this.accessLevel = accessLevel;
        }
        public String getAccessLevelString() {
            return FileService.getAccessLevelString(accessLevel);
        }
    }
}
