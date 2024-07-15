package com.web_storage.web_storage.controller;

import com.web_storage.web_storage.model.FileEntity;
import com.web_storage.web_storage.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Controller
@RequestMapping("/files")
public class FileController {

    @Autowired
    private FileService fileService;

    @GetMapping
    public String listFiles(Model model, @RequestParam("user") String user, @RequestParam(value = "folder", required = false) String folder) {
        List<FileEntity> files = (folder == null || folder.isEmpty()) ? fileService.getFilesByUser(user) : fileService.getFilesByUserAndFolder(user, folder);
        model.addAttribute("files", files);
        model.addAttribute("user", user);
        model.addAttribute("folder", folder);
        return "file_list";
    }

    @PostMapping("/upload")
    public String uploadFile(@RequestParam("user") String user, @RequestParam("file") MultipartFile file, @RequestParam(value = "folder", required = false) String folder) {
        try {
            System.out.println("Received request to upload file for user: " + user + " in folder: " + folder);
            fileService.saveFile(user, file, folder);
            System.out.println("File uploaded successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "redirect:/files?user=" + user + (folder != null ? "&folder=" + folder : "");
    }

    @PostMapping("/createFolder")
    public String createFolder(@RequestParam("user") String user,
                               @RequestParam("folderName") String folderName, Model model) {
        try {
            if (fileService.folderExists(user, folderName)) {
                model.addAttribute("error", "Folder already exists");
                return "error/folder_exists";
            }
            fileService.createFolder(user, folderName);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", "Folder already exists");
            return "error/folder_exists";
        }
        return "redirect:/files?user=" + user;
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long fileId) throws IOException {
        Path filePath = fileService.getFilePath(fileId);
        Resource resource = new UrlResource(filePath.toUri());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + new String(resource.getFilename().getBytes("UTF-8"), "ISO-8859-1") + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream; charset=UTF-8")
                .body(resource);
    }
}
