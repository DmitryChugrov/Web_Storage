package com.web_storage.web_storage.service;

import com.web_storage.web_storage.model.FileEntity;
import com.web_storage.web_storage.repository.FileRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class FileService {

    private final Path rootLocation = Paths.get("uploads");

    @Autowired
    private FileRepository fileRepository;

    public List<FileEntity> getFilesByUser(String user) {
        return fileRepository.findByUser(user);
    }

    public List<FileEntity> getFilesByUserAndFolder(String user, String folder) {
        return fileRepository.findByUserAndFolder(user, folder);
    }

    public void saveFile(String user, MultipartFile file, String folder) throws IOException {
        if (Files.notExists(rootLocation)) {
            Files.createDirectories(rootLocation);
        }

        Path userFolder = rootLocation.resolve(user);
        if (folder != null && !folder.isEmpty()) {
            userFolder = userFolder.resolve(folder);
        }

        if (Files.notExists(userFolder)) {
            Files.createDirectories(userFolder);
        }

        Path destinationFile = userFolder.resolve(
                        Paths.get(file.getOriginalFilename()))
                .normalize().toAbsolutePath();

        // Проверка на безопасность пути
        if (!destinationFile.getParent().equals(userFolder.toAbsolutePath())) {
            throw new IOException("Не удается сохранить файл за пределами текущего каталога.");
        }

        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile);
        }

        FileEntity fileEntity = new FileEntity();
        fileEntity.setFileName(file.getOriginalFilename());
        fileEntity.setFilePath(destinationFile.toString());
        fileEntity.setUser(user);
        fileEntity.setFileSize(file.getSize());
        fileEntity.setFileType(Files.probeContentType(destinationFile));
        fileEntity.setFolder(folder);

        fileRepository.save(fileEntity);
    }

    public void createFolder(String user, String folderName) {
        Path userFolder = rootLocation.resolve(user).resolve(folderName);
        if (Files.exists(userFolder)) {
            throw new IllegalArgumentException("Folder already exists");
        }
        try {
            Files.createDirectories(userFolder);
        } catch (IOException e) {
            throw new RuntimeException("Could not create folder", e);
        }
    }

    public boolean folderExists(String user, String folderName) {
        Path userFolder = rootLocation.resolve(user).resolve(folderName);
        return Files.exists(userFolder);
    }

    public Path getFilePath(Long fileId) throws IOException {
        Optional<FileEntity> fileEntityOptional = fileRepository.findById(fileId);
        if (fileEntityOptional.isPresent()) {
            return Paths.get(fileEntityOptional.get().getFilePath());
        } else {
            throw new IOException("Файл с идентификатором " + fileId + " не найден.");
        }
    }
}
