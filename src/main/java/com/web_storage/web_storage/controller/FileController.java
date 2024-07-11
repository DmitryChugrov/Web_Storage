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
    public String listFiles(@RequestParam("user") String user, Model model) {
        List<FileEntity> files = fileService.getFilesByUser(user);
        model.addAttribute("files", files);
        model.addAttribute("user", user);
        return "file_list";
    }

    @PostMapping("/upload")
    public String uploadFile(@RequestParam("user") String user, @RequestParam("file") MultipartFile file, Model model) {
        try {
            System.out.println("Received request to upload file for user: " + user);
            fileService.saveFile(user, file);
            System.out.println("File uploaded successfully.");
        } catch (IOException e) {
            e.printStackTrace();
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

