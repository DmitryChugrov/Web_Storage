package com.web_storage.web_storage.controller;

import com.web_storage.web_storage.model.FileEntity;
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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/owner")
public class OwnerController {

    @Autowired
    private UserService userService;
    @Autowired
    private FileService fileService;

    @GetMapping("/addAdmin")
    public String showAddAdminForm() {
        return "add_admin";
    }

    @PostMapping("/addAdmin")
    public String addAdmin(@RequestParam String username, Model model) {
        String password = generateRandomPassword();
        UserEntity admin = new UserEntity(username, password, Collections.singleton("ROLE_ADMIN"));
        userService.saveUser(admin);
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

    @GetMapping("/folders/{folder}")
    public String listFilesInFolder(@PathVariable String folder, Model model) {
        String user = getCurrentUsername();
        List<FileEntity> files = fileService.getFilesByUserAndFolder(user, folder);
        model.addAttribute("files", files);
        model.addAttribute("folder", folder);
        model.addAttribute("user", user);
        return "owner_files";
    }

    @GetMapping("/users")
    public String listAllUsers(Model model, Authentication authentication) {
        String currentUsername = ((UserDetails) authentication.getPrincipal()).getUsername();

        List<UserEntity> users = userService.getAllUsers().stream()
                .filter(user -> !user.getUsername().equals(currentUsername))
                .collect(Collectors.toList());

        List<UserDTO> userDTOs = users.stream()
                .map(user -> new UserDTO(user.getId(), user.getUsername(), convertRoles(user.getRoles())))
                .collect(Collectors.toList());
        model.addAttribute("users", userDTOs);
        return "owner_users";
    }
    @PostMapping("/deleteUser")
    public String deleteUser(@RequestParam Long userId) {
        userService.deleteUser(userId);
        return "redirect:/owner/users";
    }
    @PostMapping("/deleteFile")
    public String deleteFile(@RequestParam Long fileId, @RequestParam String folderName, Model model) {
            fileService.deleteFileForAdminOrOwner(fileId, folderName);
            return "redirect:/owner/folders/" + folderName;
    }

    @PostMapping("/deleteFolder")
    public String deleteFolder(@RequestParam String folderOwner,@RequestParam String folderName, Model model) {
        String currentUser = getCurrentUsername();
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

    public static class UserDTO {
        private Long id;
        private String username;
        private String roles;

        public UserDTO(Long id, String username, String roles) {
            this.id = id;
            this.username = username;
            this.roles = roles;
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
    }
    private String generateRandomPassword() {
        return "1111";
    }
}
