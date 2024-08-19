package com.web_storage.web_storage;

import com.web_storage.web_storage.model.UserEntity;
import com.web_storage.web_storage.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class DataLoader implements CommandLineRunner {

    @Autowired
    private UserService userService;

    @Override
    public void run(String... args) throws Exception {
        UserEntity adminUser = new UserEntity();
//        adminUser.setUsername("admin");
//        adminUser.setPassword("1111");
//        adminUser.setRoles(Collections.singleton("ROLE_OWNER"));
//        userService.saveUser(adminUser);
    }
}