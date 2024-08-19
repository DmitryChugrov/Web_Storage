package com.web_storage.web_storage.controller;
import com.web_storage.web_storage.model.FileEntity;
import com.web_storage.web_storage.service.FileService;
import com.web_storage.web_storage.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @Autowired
    private UserService userService;

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return ((UserDetails) authentication.getPrincipal()).getUsername();
    }

    @GetMapping
    public String listFolders(Model model, @RequestParam(value = "folder", required = false) String folder) {
        String user = getCurrentUsername();
        if (folder == null) {
            List<String> folders = fileService.getFoldersByUser(user);
            model.addAttribute("folders", folders);
            model.addAttribute("user", user);
            return "folder_list";
        } else {
            return listFiles(model, user, folder);
        }
    }

    private String listFiles(Model model, String user, String folder) {
        List<FileEntity> files = fileService.getFilesByUserAndFolder(user, folder);

        if (files.isEmpty()) {
            model.addAttribute("message", "Папка пуста");
        }

        model.addAttribute("files", files);
        model.addAttribute("user", user);
        model.addAttribute("folder", folder);
        return "file_list";
    }
    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file, @RequestParam("folder") String folder, Model model) {
        String user = getCurrentUsername();
        try {
            fileService.saveFile(user, file, folder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "redirect:/files?folder=" + folder;
    }

    @PostMapping("/createFolder")
    public String createFolder(@RequestParam("folderName") String folderName) {
        String user = getCurrentUsername();
        fileService.createFolder(user, folderName);
        return "redirect:/files";
    }
    @PostMapping("/deleteFolder")
    public String deleteFolder(@RequestParam String folderName, Authentication authentication) {
        String currentUsername = ((UserDetails) authentication.getPrincipal()).getUsername();
        fileService.deleteFolder(currentUsername, folderName);
        return "redirect:/files";
    }
    @PostMapping("/deleteFile")
    public String deleteFile(@RequestParam Long fileId, @RequestParam String folderName, Authentication authentication) throws Throwable {
        fileService.deleteFile(fileId, folderName);
        return "redirect:/files?folder=" + folderName;
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long fileId, @RequestParam("folder") String folder) throws IOException {
        String user = getCurrentUsername();
        Path filePath = fileService.getFilePath(fileId, user, folder);
        Resource resource = new UrlResource(filePath.toUri());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + new String(resource.getFilename().getBytes("UTF-8"), "ISO-8859-1") + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream; charset=UTF-8")
                .body(resource);
    }

}
