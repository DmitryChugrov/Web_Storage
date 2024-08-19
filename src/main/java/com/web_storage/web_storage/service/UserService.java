package com.web_storage.web_storage.service;
import com.web_storage.web_storage.model.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String USER_KEY_PREFIX = "user:";
    private static final String USER_HASH_KEY = "user:hash";
    private static final String USER_COUNTER_KEY = "user:counter";
    public UserEntity findByUsername(String username) {
        return (UserEntity) redisTemplate.opsForHash().get(USER_HASH_KEY, username);
    }

    public UserEntity findUserById(Long userId) {
        Map<Object, Object> allUsers = redisTemplate.opsForHash().entries(USER_HASH_KEY);
        return allUsers.values().stream()
                .filter(obj -> obj instanceof UserEntity)
                .map(obj -> (UserEntity) obj)
                .filter(user -> user.getId().equals(userId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found");
        }

        Set<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        return new User(user.getUsername(), user.getPassword(), authorities);
    }

    public void saveUser(UserEntity user) {
        Long userId = redisTemplate.opsForValue().increment(USER_COUNTER_KEY);
        user.setId(userId);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        redisTemplate.opsForHash().put(USER_HASH_KEY, user.getUsername(), user);
    }

    public List<UserEntity> getAllUsers() {
        Map<Object, Object> allUsers = redisTemplate.opsForHash().entries(USER_HASH_KEY);
        return allUsers.values().stream()
                .filter(obj -> obj instanceof UserEntity)
                .map(obj -> (UserEntity) obj)
                .collect(Collectors.toList());
    }

    public void deleteUser(Long userId) {
        Map<Object, Object> allUsers = redisTemplate.opsForHash().entries(USER_HASH_KEY);
        for (Map.Entry<Object, Object> entry : allUsers.entrySet()) {
            UserEntity user = (UserEntity) entry.getValue();
            if (user != null && user.getId().equals(userId)) {
                redisTemplate.opsForHash().delete(USER_HASH_KEY, entry.getKey());
                break;
            }
        }
    }
}
