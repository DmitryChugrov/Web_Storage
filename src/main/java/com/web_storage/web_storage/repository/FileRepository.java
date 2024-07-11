package com.web_storage.web_storage.repository;

import com.web_storage.web_storage.model.FileEntity;
import com.web_storage.web_storage.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileRepository extends JpaRepository<FileEntity, Long> {
    List<FileEntity> findByUser(UserEntity user);
    List<FileEntity> findByUserAndFolder(UserEntity user, String folder);
}
