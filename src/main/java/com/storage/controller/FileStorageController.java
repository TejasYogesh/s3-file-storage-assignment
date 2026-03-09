package com.storage.controller;

import com.storage.dto.FileMetadataDto;
import com.storage.dto.SearchResponseDto;
import com.storage.service.S3FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Tag(name = "File Storage", description = "Search, upload and download user files from S3")
public class FileStorageController {

    private final S3FileService s3FileService;

    /**
     * GET /api/files/{userName}/search?term=logistics
     *
     * Search files inside a user's S3 folder by filename substring.
     */
    @GetMapping("/{userName}/search")
    @Operation(
        summary = "Search files by filename",
        description = "Returns all files in the user's S3 folder whose names contain the search term (case-insensitive)."
    )
    public ResponseEntity<SearchResponseDto> searchFiles(
            @Parameter(description = "Username (maps to S3 folder)", example = "sandy")
            @PathVariable @NotBlank String userName,

            @Parameter(description = "Filename search term", example = "logistics")
            @RequestParam @NotBlank String term) {

        SearchResponseDto result = s3FileService.searchFiles(userName, term);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/files/{userName}/download/{fileName}
     *
     * Download a specific file for a user.
     */
    @GetMapping("/{userName}/download/{fileName}")
    @Operation(
        summary = "Download a file",
        description = "Streams a file from the user's S3 folder as a download."
    )
    public ResponseEntity<InputStreamResource> downloadFile(
            @Parameter(description = "Username", example = "sandy")
            @PathVariable @NotBlank String userName,

            @Parameter(description = "Exact file name", example = "logistics-report.pdf")
            @PathVariable @NotBlank String fileName) {

        ResponseInputStream<GetObjectResponse> s3Stream = s3FileService.downloadFile(userName, fileName);
        GetObjectResponse metadata = s3Stream.response();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType(
                        metadata.contentType() != null
                                ? metadata.contentType()
                                : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .contentLength(metadata.contentLength())
                .body(new InputStreamResource(s3Stream));
    }

    /**
     * POST /api/files/{userName}/upload
     *
     * Upload a file into the user's S3 folder.
     */
    @PostMapping(value = "/{userName}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload a file",
        description = "Uploads a file to the user's S3 folder. The file will be stored as {userName}/{originalFileName}."
    )
    public ResponseEntity<FileMetadataDto> uploadFile(
            @Parameter(description = "Username", example = "sandy")
            @PathVariable @NotBlank String userName,

            @Parameter(description = "File to upload")
            @RequestParam("file") MultipartFile file) throws IOException {

        FileMetadataDto result = s3FileService.uploadFile(userName, file);
        return ResponseEntity.status(201).body(result);
    }
}
