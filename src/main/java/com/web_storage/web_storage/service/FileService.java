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

    public void saveFile(String user, String folder, MultipartFile file) throws IOException {
        if (Files.notExists(rootLocation)) {
            Files.createDirectories(rootLocation);
        }

        Path userFolder = rootLocation.resolve(user);
        if (Files.notExists(userFolder)) {
            Files.createDirectories(userFolder);
        }

        Path folderPath = userFolder.resolve(folder);
        if (!folder.isEmpty() && Files.notExists(folderPath)) {
            throw new IOException("Folder does not exist.");
        }

        Path destinationFile = folderPath.resolve(
                        Paths.get(file.getOriginalFilename()))
                .normalize().toAbsolutePath();

        if (!destinationFile.getParent().equals(folderPath.toAbsolutePath())) {
            throw new IOException("Cannot store file outside of current directory.");
        }

        try (var inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile);
        }

        FileEntity fileEntity = new FileEntity();
        fileEntity.setFileName(file.getOriginalFilename());
        fileEntity.setFilePath(destinationFile.toString());
        fileEntity.setFileSize(file.getSize());
        fileEntity.setFileType(Files.probeContentType(destinationFile));
        fileEntity.setUser(user);

        fileRepository.save(fileEntity);
    }

    public boolean createFolder(String user, String folderName) {
        Path userFolder = rootLocation.resolve(user);
        if (Files.notExists(userFolder)) {
            try {
                Files.createDirectories(userFolder);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        Path newFolder = userFolder.resolve(folderName);
        if (Files.exists(newFolder)) {
            return false;
        }

        try {
            Files.createDirectory(newFolder);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public Path getFilePath(Long fileId) throws IOException {
        Optional<FileEntity> fileEntityOptional = fileRepository.findById(fileId);
        if (fileEntityOptional.isPresent()) {
            return Paths.get(fileEntityOptional.get().getFilePath());
        } else {
            throw new IOException("File with ID " + fileId + " not found.");
        }
    }
}
