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
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@RequestMapping("/profile")
public class UserController {

    @Autowired
    private UserService userService;
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return ((UserDetails) authentication.getPrincipal()).getUsername();
    }

    @GetMapping("/info")
    public String viewProfile(Model model) {
        String username = getCurrentUsername();
        UserEntity currentUser = userService.findByUsername(username);
        UserDTO userDTO = convertToDTO(currentUser);
        model.addAttribute("user", userDTO);
        return "profile";
    }

    @PostMapping("/changePassword")
    public String changePassword(@RequestParam String oldPassword, @RequestParam String newPassword, Model model) {
        String username = getCurrentUsername();
        boolean success = userService.changePassword(username, oldPassword, newPassword);
        if (success) {
            logger.info("Пользователь '{}' успешно сменил пароль", username);
            model.addAttribute("message", "Пароль успешно изменён");
        } else {
            logger.error("Пользователь '{}' не смог сменить пароль. Неверныйй старый пароль", username);
            model.addAttribute("error", "Неверный старый пароль");
        }
        return "change_password_result";
    }

    @GetMapping("/changePassword")
    public String showChangePasswordForm() {
        return "change_password";
    }
    public UserDTO convertToDTO(UserEntity user) {
        String accessLevelString = getAccessLevelString(user.getAccessLevel());
        String roles = String.join(", ", user.getRoles());
        return new UserDTO(user.getId(), user.getUsername(), convertRoles(Collections.singleton(roles)), accessLevelString);
    }

    private String convertRoles(Set<String> roles) {
        return roles.stream()
                .map(role -> role.replace("ROLE_", "").replace("_", " "))
                .map(String::toLowerCase)
                .map(role -> Character.toUpperCase(role.charAt(0)) + role.substring(1))
                .collect(Collectors.joining(", "));
    }
    private String getAccessLevelString(int accessLevel) {
        switch (accessLevel) {
            case 1:
                return "Public";
            case 2:
                return "Secret";
            case 3:
                return "Top Secret";
            default:
                return "Unknown";
        }
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
}
