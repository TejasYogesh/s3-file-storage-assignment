package com.storage.service;

import com.storage.dto.FileMetadataDto;
import com.storage.dto.SearchResponseDto;
import com.storage.exception.FileNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3FileService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    /**
     * Search files for a specific user by filename term.
     * Each user's files live under the prefix: {userName}/
     * Search is case-insensitive and matches substrings.
     *
     * S3 list calls are paginated - we handle all pages automatically.
     */
    public SearchResponseDto searchFiles(String userName, String searchTerm) {
        String userPrefix = buildUserPrefix(userName);
        log.debug("Searching in bucket={}, prefix={}, term={}", bucketName, userPrefix, searchTerm);

        List<FileMetadataDto> matchedFiles = new ArrayList<>();
        String continuationToken = null;

        // Paginate through all objects in the user's folder
        do {
            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(userPrefix);

            if (continuationToken != null) {
                requestBuilder.continuationToken(continuationToken);
            }

            ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());

            response.contents().stream()
                    .filter(obj -> matchesSearchTerm(obj.key(), userPrefix, searchTerm))
                    .map(obj -> toFileMetadataDto(obj, userName))
                    .forEach(matchedFiles::add);

            continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;

        } while (continuationToken != null);

        log.info("Search complete for user={}, term={}, results={}", userName, searchTerm, matchedFiles.size());

        return SearchResponseDto.builder()
                .userName(userName)
                .searchTerm(searchTerm)
                .totalResults(matchedFiles.size())
                .files(matchedFiles)
                .build();
    }

    /**
     * Upload a file to the user's folder in S3.
     * Key format: {userName}/{originalFileName}
     */
    public FileMetadataDto uploadFile(String userName, MultipartFile file) throws IOException {
        String key = buildUserPrefix(userName) + file.getOriginalFilename();
        log.debug("Uploading file: bucket={}, key={}", bucketName, key);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        log.info("Uploaded file: user={}, key={}", userName, key);

        return FileMetadataDto.builder()
                .fileName(file.getOriginalFilename())
                .s3Key(key)
                .sizeBytes(file.getSize())
                .downloadUrl(buildDownloadUrl(userName, file.getOriginalFilename()))
                .build();
    }

    /**
     * Download a file as a raw byte stream.
     * Throws FileNotFoundException if the key does not exist.
     */
    public ResponseInputStream<GetObjectResponse> downloadFile(String userName, String fileName) {
        String key = buildUserPrefix(userName) + fileName;
        log.debug("Downloading: bucket={}, key={}", bucketName, key);

        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3Client.getObject(getRequest);
        } catch (NoSuchKeyException e) {
            throw new FileNotFoundException(
                    "File '%s' not found for user '%s'".formatted(fileName, userName));
        }
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    /** Builds the S3 prefix for a user's folder: "sandy/" */
    private String buildUserPrefix(String userName) {
        return userName.toLowerCase() + "/";
    }

    /** Checks if a file's name (excluding the prefix) contains the search term (case-insensitive) */
    private boolean matchesSearchTerm(String s3Key, String userPrefix, String searchTerm) {
        String fileName = s3Key.substring(userPrefix.length()); // strip "sandy/"
        return !fileName.isBlank()
                && fileName.toLowerCase().contains(searchTerm.toLowerCase());
    }

    /** Converts an S3Object summary to our DTO */
    private FileMetadataDto toFileMetadataDto(S3Object s3Object, String userName) {
        String userPrefix = buildUserPrefix(userName);
        String fileName = s3Object.key().substring(userPrefix.length());

        return FileMetadataDto.builder()
                .fileName(fileName)
                .s3Key(s3Object.key())
                .sizeBytes(s3Object.size())
                .lastModified(s3Object.lastModified())
                .downloadUrl(buildDownloadUrl(userName, fileName))
                .build();
    }

    /** Returns a relative download URL for the file */
    private String buildDownloadUrl(String userName, String fileName) {
        return "/api/files/%s/download/%s".formatted(userName, fileName);
    }
}
