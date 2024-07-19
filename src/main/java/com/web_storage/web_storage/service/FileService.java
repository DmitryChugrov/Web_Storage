package com.web_storage.web_storage.service;

import com.web_storage.web_storage.model.FileEntity;
import com.web_storage.web_storage.repository.FileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
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
                "file_size BIGINT) ENGINE=InnoDB");
        query.executeUpdate();

        // Создаем новую запись в file_entity
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

        String tableName = sanitizeTableName(pathFolder);
        String sql = "INSERT INTO " + tableName + " (file_name, file_path, file_type, file_size) VALUES (?, ?, ?, ?)";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter(1, file.getOriginalFilename());
        query.setParameter(2, destinationFile.toString());
        query.setParameter(3, file.getContentType());
        query.setParameter(4, file.getSize());
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
        // Удаляем все символы, кроме букв, цифр и подчеркивания
        return tableName.replaceAll("[^a-zA-Z0-9_]", "");
    }
}
