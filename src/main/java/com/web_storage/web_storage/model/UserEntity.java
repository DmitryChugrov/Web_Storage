package com.web_storage.web_storage.model;
import com.web_storage.web_storage.service.FileService;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@RedisHash("UserEntity")
public class UserEntity {
    @Id
    private Long id;
    private String username;
    private String password;
    private int accessLevel;
    private Set<String> roles = new HashSet<>();

    public UserEntity() {
    }

    public UserEntity(String username, String password, Set<String> roles) {
        this.username = username;
        this.password = password;
        this.roles = roles;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public int getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(int accessLevel) {
        this.accessLevel = accessLevel;
    }
//    public String getAccessLevelString() {
//        return FileService.getAccessLevelString(accessLevel);
//    }
}
