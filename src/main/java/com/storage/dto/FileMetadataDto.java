package com.storage.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class FileMetadataDto {
    private String fileName;
    private String s3Key;
    private long sizeBytes;
    private Instant lastModified;
    private String downloadUrl;
}
