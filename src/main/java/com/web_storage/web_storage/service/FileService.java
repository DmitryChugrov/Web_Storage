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

    public void saveFile(String user, MultipartFile file) throws IOException {
        if (Files.notExists(rootLocation)) {
            Files.createDirectories(rootLocation);
        }

        Path destinationFile = rootLocation.resolve(
                        Paths.get(file.getOriginalFilename()))
                .normalize().toAbsolutePath();

        // Проверка на безопасность пути
        if (!destinationFile.getParent().equals(rootLocation.toAbsolutePath())) {
            throw new IOException("Не удается сохранить файл за пределами текущего каталога.");
        }

        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile);
        }

        FileEntity fileEntity = new FileEntity();
        fileEntity.setFileName(file.getOriginalFilename());
        fileEntity.setFilePath(destinationFile.toString());
        fileEntity.setUser(user);

        fileRepository.save(fileEntity);
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
