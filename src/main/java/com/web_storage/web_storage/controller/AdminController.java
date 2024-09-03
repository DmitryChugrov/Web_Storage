package com.web_storage.web_storage.controller;

import com.web_storage.web_storage.model.UserEntity;
import com.web_storage.web_storage.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UserService userService;
    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @GetMapping("/addUser")
    public String showAddUserForm() {
        return "add_user";
    }

    @PostMapping("/addUser")
    public String addUser(@RequestParam String username, @RequestParam int accessLevel, Model model) {
        String admin = getCurrentUsername();
        String password = generateRandomPassword();
        UserEntity user = new UserEntity(username, password, Collections.singleton("ROLE_USER"));
        user.setAccessLevel(accessLevel);
        userService.saveUser(user);
        logger.info("Администратор '{}' создал пользователя '{}' ",admin, user);
        String accessLevelString = userService.getAccessLevelString1(accessLevel);
        model.addAttribute("username", username);
        model.addAttribute("password", password);
        model.addAttribute("accessLevelString", accessLevelString);
        return "user_added";
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
        return "admin_users";
    }

    @PostMapping("/deleteUser")
    public String deleteUser(@RequestParam Long userId, Authentication authentication) {
        String admin = getCurrentUsername();
        UserEntity userToDelete = userService.findUserById(userId);
        String user = userToDelete.getUsername();
        if (userToDelete != null && userToDelete.getRoles().contains("ROLE_USER")) {
            String currentUsername = ((UserDetails) authentication.getPrincipal()).getUsername();
            if (!userToDelete.getUsername().equals(currentUsername)) {
                userService.deleteUser(userId);
                logger.info("Администратор '{}' удалил пользователя '{}' ", admin, user);
            }
        }
        return "redirect:/admin/users";
    }
    @GetMapping("/changeAccessLevel")
    public String showChangeAccessLevelForm(@RequestParam Long userId, Model model) {
        UserEntity user = userService.findUserById(userId);
        if (user != null) {
            UserDTO userDTO = new UserDTO(user.getId(), user.getUsername(), convertRoles(user.getRoles()), convertAccessLevel(user.getAccessLevel()));
            model.addAttribute("user", userDTO);
            return "change_access_level";
        } else {
            return "redirect:/admin/users";
        }
    }

    @PostMapping("/changeAccessLevel")
    public String changeAccessLevel(@RequestParam Long userId, @RequestParam int newAccessLevel, Model model) {
        String admin = getCurrentUsername();
        UserEntity user = userService.findUserById(userId);
        String username = user.getUsername();
        if (user != null) {
            userService.updateAccessLevel(userId, newAccessLevel);
            logger.info("Администратор '{}' изменил уровень доступа пользователя '{}' на '{}'", admin, username, newAccessLevel);
            model.addAttribute("message", "Уровень доступа успешно изменён");
        } else {
            logger.error("Администратор '{}' не смог изменить уровень доступа пользователя '{}' на '{}'", admin, username, newAccessLevel);
            model.addAttribute("error", "Пользователь не найден");
        }
        return "change_access_level_result";
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
        // Implement password generation logic
        return "1111";
    }
}

