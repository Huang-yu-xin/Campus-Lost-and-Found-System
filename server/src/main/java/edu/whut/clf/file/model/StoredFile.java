package edu.whut.clf.file.model;

import lombok.Data;

import java.time.LocalDateTime;

/** files 表实体。 */
@Data
public class StoredFile {
    private Long id;
    private Long ownerId;
    private String purpose;      // FilePurpose
    private String storageKey;   // 随机、不可枚举、不可由用户构造
    private String mimeType;
    private Long size;
    private String visibility;   // PUBLIC / PRIVATE
    private Boolean bound;
    private LocalDateTime createdAt;
}
