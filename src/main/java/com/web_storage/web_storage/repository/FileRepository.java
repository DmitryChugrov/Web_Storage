package com.web_storage.web_storage.repository;
import com.web_storage.web_storage.model.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FileRepository extends JpaRepository<FileEntity, Long> {
    List<FileEntity> findByUser(String user);
    List<FileEntity> findByUserAndPathFolder(String user, String pathFolder);
    List<FileEntity> findByPathFolder(String pathFolder);
    void deleteByUserAndPathFolder(String user, String pathFolder);


}