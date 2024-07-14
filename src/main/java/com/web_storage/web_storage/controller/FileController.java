package com.web_storage.web_storage.controller;

import com.web_storage.web_storage.model.FileEntity;
import com.web_storage.web_storage.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
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
    public String listFiles(Model model, Authentication authentication) {
        String username = ((UserDetails) authentication.getPrincipal()).getUsername();
        List<FileEntity> files = fileService.getFilesByUser(username);
        model.addAttribute("files", files);
        model.addAttribute("user", username);
        return "file_list";
    }

    @PostMapping("/upload")
    public String uploadFile(@RequestParam("folder") String folder,
                             @RequestParam("file") MultipartFile file,
                             Authentication authentication) {
        String username = ((UserDetails) authentication.getPrincipal()).getUsername();
        try {
            fileService.saveFile(username, folder, file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "redirect:/files";
    }

    @PostMapping("/createFolder")
    public String createFolder(@RequestParam("folderName") String folderName,
                               Authentication authentication) {
        String username = ((UserDetails) authentication.getPrincipal()).getUsername();
        if (!fileService.createFolder(username, folderName)) {
            return "error";
        }
        return "redirect:/files";
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