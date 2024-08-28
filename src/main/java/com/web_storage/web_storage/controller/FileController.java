package com.web_storage.web_storage.controller;
import com.web_storage.web_storage.model.FileEntity;
import com.web_storage.web_storage.model.FolderEntity;
import com.web_storage.web_storage.model.UserEntity;
import com.web_storage.web_storage.service.FileService;
import com.web_storage.web_storage.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.web_storage.web_storage.service.FileService.getAccessLevelString;

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
    public String uploadFile(@RequestParam("file") MultipartFile file, @RequestParam("folder") String folder, @RequestParam("owner") String folderOwner, Model model) {
        String user = getCurrentUsername();
        try {
            fileService.saveFile(folderOwner, file, folder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "redirect:/files/folders/" + folderOwner + "/" + folder;
    }

    @PostMapping("/folders/deleteOtherFolder")
    public String deleteOtherFolder(@RequestParam String folderOwner,@RequestParam String folderName, Model model) {
        String currentUser = getCurrentUsername();
        FolderEntity folderEntity = fileService.getFolderEntity(folderOwner, folderName);
        int folderAccessLevel = folderEntity != null ? folderEntity.getAccessLevel() : 0;
        UserEntity currentUserEntity = userService.findByUsername(currentUser);
        int userAccessLevel = currentUserEntity != null ? currentUserEntity.getAccessLevel() : 0;
        if (folderAccessLevel == userAccessLevel){
            fileService.deleteFolder(folderOwner,folderName);
            return "redirect:/files/folders";
        }else return "access_denied";

    }
    @GetMapping("/folders")
    public String listAllFolders(Model model, Authentication authentication) {
        String currentUsername = ((UserDetails) authentication.getPrincipal()).getUsername();
        UserEntity currentUser = userService.findByUsername(currentUsername);
        int currentUserAccessLevel = currentUser.getAccessLevel();
        List<FileService.FolderInfo> allFolders = fileService.getAllFoldersWithOwners();
        List<FileService.FolderInfo> accessibleFolders = allFolders.stream()
                .filter(folder -> isFolderAccessible(currentUserAccessLevel, folder.getAccessLevel()))
                .collect(Collectors.toList());

        model.addAttribute("userAccessLevel", currentUserAccessLevel);
        model.addAttribute("folders", accessibleFolders);
        return "other_folders";
    }

    private boolean isFolderAccessible(int userAccessLevel, int folderAccessLevel) {
        switch (userAccessLevel) {
            case 1:
                return folderAccessLevel == 1 || folderAccessLevel == 2;
            case 2:
                return folderAccessLevel <= 3;
            case 3:
                return true;
            default:
                return false;
        }
    }

    @GetMapping("/folders/{owner}/{folder}")
    public String listFilesInFolder(@PathVariable String owner, @PathVariable String folder, Model model) {
        String currentUser = getCurrentUsername();

        FolderEntity folderEntity = fileService.getFolderEntity(owner, folder);
        int folderAccessLevel = folderEntity != null ? folderEntity.getAccessLevel() : 0;

        UserEntity currentUserEntity = userService.findByUsername(currentUser);
        int userAccessLevel = currentUserEntity != null ? currentUserEntity.getAccessLevel() : 0;
        boolean canUpload = (userAccessLevel == folderAccessLevel) || (userAccessLevel == 2 && folderAccessLevel == 3);

        List<FileEntity> files = fileService.getFilesByUserAndFolder(owner, folder);
        List<FileDTO> fileDTOs = files.stream()
                .map(file -> new FileDTO(file.getId(), file.getFileName(), file.getFileSize(),
                        getAccessLevelString(file.getAccessLevel()), file.getUser()))
                .collect(Collectors.toList());

        model.addAttribute("files", fileDTOs);
        model.addAttribute("folder", folder);
        model.addAttribute("user", owner);
        model.addAttribute("canUpload", canUpload);
        model.addAttribute("userAccessLevel", userAccessLevel);
        model.addAttribute("folderAccessLevel", folderAccessLevel);


        return "other_files";
    }

    @PostMapping("/folders/deleteFile")
    public String deleteFileInOtherFolder(@RequestParam Long fileId, @RequestParam String folder, @RequestParam("owner") String folderOwner, Authentication authentication) throws Throwable {
        String currentUser = getCurrentUsername();
        FolderEntity folderEntity = fileService.getFolderEntity(folderOwner, folder);
        int folderAccessLevel = folderEntity != null ? folderEntity.getAccessLevel() : 0;
        UserEntity currentUserEntity = userService.findByUsername(currentUser);
        int userAccessLevel = currentUserEntity != null ? currentUserEntity.getAccessLevel() : 0;
        if (folderAccessLevel == userAccessLevel){
            fileService.deleteFile(fileId, folder);
            return "redirect:/files/folders/" + folderOwner + "/" + folder;
        }else return "access_denied";
    }


    public static class FileDTO {
        private Long id;
        private String fileName;
        private double fileSize;
        private String accessLevel;
        private String user;

        public FileDTO(Long id, String fileName, double fileSize, String accessLevel, String user) {
            this.id = id;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.accessLevel = accessLevel;
            this.user = user;
        }

        public Long getId() {
            return id;
        }

        public String getFileName() {
            return fileName;
        }

        public double getFileSize() {
            return fileSize;
        }

        public String getAccessLevel() {
            return accessLevel;
        }

        public String getUser() {
            return user;
        }
    }



    @PostMapping("/createFolder")
    public String createFolder(@RequestParam("folderName") String folderName) {
        String user = getCurrentUsername();
        fileService.createFolder(user, folderName);
        return "redirect:/files";
    }

    @PostMapping("/deleteFolder")
    public String deleteFolder(@RequestParam String folderName, Authentication authentication, Model model) {
        String currentUsername = ((UserDetails) authentication.getPrincipal()).getUsername();
        FolderEntity folderEntity = fileService.getFolderEntity(currentUsername, folderName);

        if (folderEntity == null) {
            model.addAttribute("error", "Папка не найдена.");
            return "redirect:/files";
        }
        UserEntity currentUser = userService.findByUsername(currentUsername);
        int userAccessLevel = currentUser.getAccessLevel();

        if (userAccessLevel == folderEntity.getAccessLevel()) {
            fileService.deleteFolder(currentUsername, folderName);
            return "redirect:/files";
        } else {
            model.addAttribute("error", "У вас недостаточно прав для удаления этой папки.");
            return "redirect:/files";
        }
    }

    @PostMapping("/deleteFile")
    public String deleteFile(@RequestParam Long fileId, @RequestParam String folderName, Authentication authentication) throws Throwable {
        fileService.deleteFile(fileId, folderName);
        return "redirect:/files?folder=" + folderName;
    }
    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long fileId,
                                                 @RequestParam("owner") String owner,
                                                 @RequestParam("folder") String folder) {
        try {
            Path filePath = fileService.getFilePath(fileId, owner, folder);
            Resource resource = new UrlResource(filePath.toUri());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + new String(resource.getFilename().getBytes("UTF-8"), "ISO-8859-1") + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream; charset=UTF-8")
                    .body(resource);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

}
