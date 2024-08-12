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

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private UserService userService;

    @GetMapping("/addUser")
    public String showAddUserForm() {
        return "add_user";
    }

    @PostMapping("/addUser")
    public String addUser(@RequestParam String username, Model model) {
        String password = generateRandomPassword();
        UserEntity user = new UserEntity(username, password, Collections.singleton("ROLE_USER"));
        userService.saveUser(user);
        model.addAttribute("username", username);
        model.addAttribute("password", password);
        return "user_added";
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
        return "admin_users";
    }

    @PostMapping("/deleteUser")
    public String deleteUser(@RequestParam Long userId, Authentication authentication) {
        UserEntity userToDelete = userService.findUserById(userId);
        if (userToDelete != null && userToDelete.getRoles().contains("ROLE_USER")) {
            String currentUsername = ((UserDetails) authentication.getPrincipal()).getUsername();
            if (!userToDelete.getUsername().equals(currentUsername)) {
                userService.deleteUser(userId);
            }
        }
        return "redirect:/admin/users";
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
        // Implement password generation logic
        return "1111";
    }
}

