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

        Path destinationFolder = (folder != null && !folder.isEmpty()) ? rootLocation.resolve(folder) : rootLocation;
        if (Files.notExists(destinationFolder)) {
            Files.createDirectories(destinationFolder);
        }

        Path destinationFile = destinationFolder.resolve(
                Paths.get(file.getOriginalFilename())).normalize().toAbsolutePath();

        if (!destinationFile.getParent().equals(destinationFolder.toAbsolutePath())) {
            throw new IOException("Не удается сохранить файл за пределами текущего каталога.");
        }

        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile);
        }

        FileEntity fileEntity = new FileEntity();
        fileEntity.setFileName(file.getOriginalFilename());
        fileEntity.setFilePath(destinationFile.toString());
        fileEntity.setFileSize(file.getSize());
        fileEntity.setFileType(file.getContentType());
        fileEntity.setUser(user);
        fileEntity.setFolder(folder);

        fileRepository.save(fileEntity);
    }
    public void createFolder(String user, String folderName) {
        Path userFolder = rootLocation.resolve(user).resolve(folderName);
        try {
            if (Files.notExists(userFolder)) {
                Files.createDirectories(userFolder);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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
