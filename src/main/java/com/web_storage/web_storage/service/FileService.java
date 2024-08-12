package com.web_storage.web_storage.service;

import com.web_storage.web_storage.model.FileEntity;
import com.web_storage.web_storage.repository.FileRepository;
import jakarta.persistence.NoResultException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
@Service
public class FileService {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private FileRepository fileRepository;

    private final Path rootLocation = Paths.get("uploads");

    @Transactional
    public void createFolder(String user, String folderName) {
        String tableName = sanitizeTableName(folderName);
        Query query = entityManager.createNativeQuery("CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "file_name VARCHAR(255), " +
                "file_path VARCHAR(255), " +
                "file_type VARCHAR(50), " +
                "file_size DOUBLE) ENGINE=InnoDB");
        query.executeUpdate();

        FileEntity fileEntity = new FileEntity(user, folderName);
        fileRepository.save(fileEntity);

        try {
            Path folderPath = rootLocation.resolve(Paths.get(folderName)).normalize().toAbsolutePath();
            if (!Files.exists(folderPath)) {
                Files.createDirectories(folderPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not create folder on disk", e);
        }
    }

    @Transactional
    public void saveFile(String user, MultipartFile file, String folder) throws IOException {
        String pathFolder = folder;
        Path folderPath = rootLocation.resolve(Paths.get(pathFolder)).normalize().toAbsolutePath();

        if (!Files.exists(folderPath)) {
            Files.createDirectories(folderPath);
        }

        Path destinationFile = folderPath.resolve(Paths.get(file.getOriginalFilename())).normalize().toAbsolutePath();
        if (!destinationFile.getParent().equals(folderPath)) {
            throw new RuntimeException("Cannot store file outside current directory");
        }

        Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

        double fileSizeInMB = file.getSize() / (1024.0 * 1024.0);

        String tableName = sanitizeTableName(pathFolder);
        String sql = "INSERT INTO " + tableName + " (file_name, file_path, file_type, file_size) VALUES (?, ?, ?, ?)";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, file.getOriginalFilename());
        query.setParameter(2, destinationFile.toString());
        query.setParameter(3, file.getContentType());
        query.setParameter(4, fileSizeInMB);
        query.executeUpdate();
    }

    public List<String> getFoldersByUser(String user) {
        List<FileEntity> fileEntities = fileRepository.findByUser(user);
        return fileEntities.stream()
                .map(FileEntity::getPathFolder)
                .collect(Collectors.toList());
    }

    public List<FileEntity> getFilesByUserAndFolder(String user, String folder) {
        String tableName = sanitizeTableName(folder);
        String sql = "SELECT id, file_name AS fileName, file_path AS filePath, file_type AS fileType, file_size AS fileSize FROM " + tableName;
        List<Object[]> results = entityManager.createNativeQuery(sql).getResultList();
        return results.stream().map(result -> {
            FileEntity fileEntity = new FileEntity();
            fileEntity.setId(((Number) result[0]).longValue());
            fileEntity.setUser(user);
            fileEntity.setPathFolder(folder);
            fileEntity.setFileName((String) result[1]);
            fileEntity.setFilePath((String) result[2]);
            fileEntity.setFileType((String) result[3]);
            fileEntity.setFileSize(((Number) result[4]).longValue());
            return fileEntity;
        }).collect(Collectors.toList());
    }

    public Path getFilePath(Long fileId, String user, String folder) {
        String tableName = sanitizeTableName(folder);
        String sql = "SELECT file_path FROM " + tableName + " WHERE id = ?";
        return Paths.get((String) entityManager.createNativeQuery(sql).setParameter(1, fileId).getSingleResult());
    }

    private String sanitizeTableName(String tableName) {
        return tableName.replaceAll("[^a-zA-Z0-9_]", "");
    }
    public List<FolderInfo> getAllFoldersWithOwners() {
        List<FileEntity> allFiles = fileRepository.findAll();
        return allFiles.stream()
                .collect(Collectors.groupingBy(FileEntity::getPathFolder))
                .entrySet().stream()
                .map(entry -> new FolderInfo(entry.getKey(), entry.getValue().get(0).getUser()))
                .collect(Collectors.toList());
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

    public List<FileEntity> getFilesByFolder(String folder) {
        return fileRepository.findByPathFolder(folder);
    }

    @Transactional
    public void deleteFolder(String user, String folderName) {
        fileRepository.deleteByUserAndPathFolder(user, folderName);
        String tableName = sanitizeTableName(folderName);
        Query query = entityManager.createNativeQuery("DROP TABLE IF EXISTS " + tableName);
        query.executeUpdate();
        Path folderPath = rootLocation.resolve(Paths.get(folderName)).normalize().toAbsolutePath();
        try {
            if (Files.exists(folderPath)) {
                Files.walkFileTree(folderPath, new FileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not delete folder on disk", e);
        }
    }
    @Transactional
    public void deleteFile(Long fileId, String folderName) {
        String tableName = sanitizeTableName(folderName);

        String sqlGetFilePath = "SELECT file_path FROM " + tableName + " WHERE id = ?";
        String filePath;
        try {
            filePath = (String) entityManager.createNativeQuery(sqlGetFilePath)
                    .setParameter(1, fileId)
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new RuntimeException("File not found for ID: " + fileId, e);
        }

        Path file = Paths.get(filePath);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException("Could not delete file on disk", e);
        }

        String sqlDeleteFile = "DELETE FROM " + tableName + " WHERE id = ?";
        Query queryDeleteFile = entityManager.createNativeQuery(sqlDeleteFile);
        queryDeleteFile.setParameter(1, fileId);
        queryDeleteFile.executeUpdate();
    }

    private boolean hasAnyRole(Set<String> roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return userDetails.getAuthorities().stream()
                    .anyMatch(grantedAuthority -> roles.contains(grantedAuthority.getAuthority()));
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
    public void deleteFolderForOwner(String user,String folderName) {
        deleteFolder(user, folderName);
    }

    @Transactional
    public void deleteFileForAdminOrOwner(Long fileId, String folderName) {
        if (!isOwner() && !isAdmin()) {
            throw new SecurityException("You do not have permission to delete this file.");
        }
        deleteFile(fileId, folderName);
    }

}
