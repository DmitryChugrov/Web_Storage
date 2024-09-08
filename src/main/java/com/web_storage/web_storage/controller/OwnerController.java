package com.web_storage.web_storage.controller;

import com.web_storage.web_storage.model.FileEntity;
import com.web_storage.web_storage.model.FolderEntity;
import com.web_storage.web_storage.model.UserEntity;
import com.web_storage.web_storage.service.FileService;
import com.web_storage.web_storage.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.web_storage.web_storage.service.FileService.getAccessLevelString;

@Controller
@RequestMapping("/owner")
public class OwnerController {

    @Autowired
    private UserService userService;
    @Autowired
    private FileService fileService;
    private static final Logger logger = LoggerFactory.getLogger(OwnerController.class);

    @GetMapping("/addAdmin")
    public String showAddAdminForm() {
        return "add_admin";
    }

    @PostMapping("/addAdmin")
    public String addAdmin(@RequestParam String username, Model model) {
        String currentUser = getCurrentUsername();
        String password = generateRandomPassword();
        UserEntity admin = new UserEntity(username, password, Collections.singleton("ROLE_ADMIN"));
        admin.setAccessLevel(3);
        userService.saveUser(admin);
        logger.info("Владелец '{}' создал администратора '{}' ", currentUser, admin);
        model.addAttribute("username", username);
        model.addAttribute("password", password);
        return "admin_added";
    }
    @GetMapping("/folders")
    public String listAllFolders(Model model) {
        List<FileService.FolderInfo> folders = fileService.getAllFoldersWithOwners();
        model.addAttribute("folders", folders);
        return "owner_folders";
    }

    @GetMapping("/folders/{owner}/{folder}")
    public String listFilesInFolder(@PathVariable String owner, @PathVariable String folder, Model model) {
        String currentUser = getCurrentUsername();

        FolderEntity folderEntity = fileService.getFolderEntity(owner, folder);
        int accessLevel = folderEntity != null ? folderEntity.getAccessLevel() : 0;

        if (currentUser.equals(owner) || accessLevel > 0) {
            List<FileEntity> files = fileService.getFilesByUserAndFolder(owner, folder);
            List<FileController.FileDTO> fileDTOs = files.stream()
                    .map(file -> new FileController.FileDTO(file.getId(), file.getFileName(), file.getFileSize(),
                            getAccessLevelString(file.getAccessLevel()), file.getUser()))
                    .collect(Collectors.toList());
            model.addAttribute("files", fileDTOs);
            model.addAttribute("folder", folder);
            model.addAttribute("user", owner);
            return "owner_files";
        } else {
            return "access_denied";
        }
    }

    @GetMapping("/users")
    public String listAllUsers(Model model, Authentication authentication) {
        String currentUsername = ((UserDetails) authentication.getPrincipal()).getUsername();

        List<UserEntity> users = userService.getAllUsers().stream()
                .filter(user -> !user.getUsername().equals(currentUsername))
                .collect(Collectors.toList());

        List<UserDTO> userDTOs = users.stream()
                .map(user -> new UserDTO(user.getId(), user.getUsername(), convertRoles(user.getRoles()), convertAccessLevel(user.getAccessLevel())))
                .collect(Collectors.toList());
        model.addAttribute("users", userDTOs);
        return "owner_users";
    }
    @PostMapping("/assignAdmin")
    public String assignAdmin(@RequestParam Long userId, Authentication authentication) {
        String owner = getCurrentUsername();
        UserEntity user = userService.findUserById(userId);
        String username = user.getUsername();
        if (userService.hasTopSecretAccess(authentication)) {
            userService.assignAdminRole(userId);
            logger.info("Владелец '{}' назначил пользователя '{}' администратором", owner, username);
        } else {
            logger.error("Владелец '{}' не смог назначил пользователя '{}' администратором", owner, username);
            return "redirect:/error/unauthorized";
        }
        return "redirect:/owner/users";
    }

    @PostMapping("/revokeAdmin")
    public String revokeAdmin(@RequestParam Long userId, Authentication authentication) {
        String owner = getCurrentUsername();
        UserEntity user = userService.findUserById(userId);
        String username = user.getUsername();
        if (userService.hasTopSecretAccess(authentication)) {
            userService.revokeAdminRole(userId);
            logger.info("Владелец '{}' снял роль администратора с пользователя '{}'", owner, username);
        } else {
            logger.error("Владелец '{}' не смог снять роль администратора с '{}'", owner, username);
            return "redirect:/error/unauthorized";
        }
        return "redirect:/owner/users";
    }
    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file, @RequestParam("folder") String folder, @RequestParam("owner") String folderOwner, Model model) {
        String user = getCurrentUsername();
        try {
            fileService.saveFile(folderOwner, file, folder);
            logger.info("Владелец '{}' загрузил файл '{}' в папку '{}'", user, file.getOriginalFilename(), folder);
            model.addAttribute("message", "Файл успешно загружен в хранилище");
        } catch (IOException e) {
            logger.error("Ошибка при загрузке файла '{}' владельцем: {}", file.getOriginalFilename(), e.getMessage());
            model.addAttribute("error", "Произошла непредвиденная ошибка");
        }
        return "upload_result";
    }
    @PostMapping("/deleteUser")
    public String deleteUser(@RequestParam Long userId) {
        String owner = getCurrentUsername();
        UserEntity user = userService.findUserById(userId);
        String username = user.getUsername();
        logger.info("Владелец '{}' удалил пользователя '{}'", owner, username);
        userService.deleteUser(userId);
        return "redirect:/owner/users";
    }
    @PostMapping("/deleteFile")
    public String deleteFile(@RequestParam Long fileId,@RequestParam("owner") String folderOwner, @RequestParam("folder") String folder, @RequestParam String folderName, Model model) {
            logger.info("Владелец удалил файл с id = '{}' в папке '{}'", fileId, folder);
            fileService.deleteFileForOwner(fileId, folderName);
            model.addAttribute("message", "Файл успешно удален из хранилище");
            return "delete_result";
    }

    @PostMapping("/deleteFolder")
    public String deleteFolder(@RequestParam String folderOwner,@RequestParam String folderName, Model model) {
        String currentUser = getCurrentUsername();
        logger.info("Владелец удалил папку '{}'", folderName);
            fileService.deleteFolderForOwner(folderOwner,folderName);
            return "redirect:/owner/folders";
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
    private String convertRoles(Set<String> roles) {
        return roles.stream()
                .map(role -> role.replace("ROLE_", "").replace("_", " "))
                .map(String::toLowerCase)
                .map(role -> Character.toUpperCase(role.charAt(0)) + role.substring(1))
                .collect(Collectors.joining(", "));
    }
    private String convertAccessLevel(int accessLevel) {
        switch (accessLevel) {
            case 1:
                return "Public";
            case 2:
                return "Secret";
            case 3:
                return "Top Secret";

        }
        return null;
    }


    public static class UserDTO {
        private Long id;
        private String username;
        private String roles;
        private String accessLevel;

        public UserDTO(Long id, String username, String roles, String accessLevel) {
            this.id = id;
            this.username = username;
            this.roles = roles;
            this.accessLevel = accessLevel;
        }

        public Long getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getRoles() {
            return roles;
        }

        public String getAccessLevel() {
            return accessLevel;
        }

        public void setAccessLevel(String accessLevel) {
            this.accessLevel = accessLevel;
        }
    }
    private String generateRandomPassword() {
        return "1111";
    }
}
