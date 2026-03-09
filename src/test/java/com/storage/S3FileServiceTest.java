package com.storage;

import com.storage.dto.FileMetadataDto;
import com.storage.dto.SearchResponseDto;
import com.storage.exception.FileNotFoundException;
import com.storage.service.S3FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class S3FileServiceTest {

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private S3FileService s3FileService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(s3FileService, "bucketName", "test-bucket");
    }

    // ─── Search Tests ────────────────────────────────────────────────────────

    @Test
    void searchFiles_returnsMatchingFiles() {
        // Arrange
        S3Object matchingFile = S3Object.builder()
                .key("sandy/logistics-report.pdf")
                .size(1024L)
                .lastModified(Instant.now())
                .build();

        S3Object nonMatchingFile = S3Object.builder()
                .key("sandy/invoice.pdf")
                .size(512L)
                .lastModified(Instant.now())
                .build();

        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder()
                .contents(matchingFile, nonMatchingFile)
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResponse);

        // Act
        SearchResponseDto result = s3FileService.searchFiles("sandy", "logistics");

        // Assert
        assertThat(result.getTotalResults()).isEqualTo(1);
        assertThat(result.getFiles()).hasSize(1);
        assertThat(result.getFiles().get(0).getFileName()).isEqualTo("logistics-report.pdf");
        assertThat(result.getUserName()).isEqualTo("sandy");
        assertThat(result.getSearchTerm()).isEqualTo("logistics");
    }

    @Test
    void searchFiles_returnsEmpty_whenNoMatch() {
        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder()
                .contents(
                    S3Object.builder().key("sandy/invoice.pdf").size(100L).lastModified(Instant.now()).build()
                )
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResponse);

        SearchResponseDto result = s3FileService.searchFiles("sandy", "logistics");

        assertThat(result.getTotalResults()).isEqualTo(0);
        assertThat(result.getFiles()).isEmpty();
    }

    @Test
    void searchFiles_isCaseInsensitive() {
        S3Object file = S3Object.builder()
                .key("sandy/LOGISTICS-Q3.xlsx")
                .size(200L)
                .lastModified(Instant.now())
                .build();

        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder()
                .contents(file)
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResponse);

        SearchResponseDto result = s3FileService.searchFiles("sandy", "logistics");

        assertThat(result.getTotalResults()).isEqualTo(1);
    }

    @Test
    void searchFiles_paginatesCorrectly() {
        // Page 1 (truncated)
        ListObjectsV2Response page1 = ListObjectsV2Response.builder()
                .contents(
                    S3Object.builder().key("sandy/logistics-1.pdf").size(100L).lastModified(Instant.now()).build()
                )
                .isTruncated(true)
                .nextContinuationToken("token-xyz")
                .build();

        // Page 2 (last page)
        ListObjectsV2Response page2 = ListObjectsV2Response.builder()
                .contents(
                    S3Object.builder().key("sandy/logistics-2.pdf").size(200L).lastModified(Instant.now()).build()
                )
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(page1)
                .thenReturn(page2);

        SearchResponseDto result = s3FileService.searchFiles("sandy", "logistics");

        assertThat(result.getTotalResults()).isEqualTo(2);
        verify(s3Client, times(2)).listObjectsV2(any(ListObjectsV2Request.class));
    }

    // ─── Download Tests ──────────────────────────────────────────────────────

    @Test
    void downloadFile_throwsFileNotFoundException_whenKeyMissing() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Not found").build());

        assertThatThrownBy(() -> s3FileService.downloadFile("sandy", "missing.pdf"))
                .isInstanceOf(FileNotFoundException.class)
                .hasMessageContaining("missing.pdf")
                .hasMessageContaining("sandy");
    }

    // ─── DTO Tests ───────────────────────────────────────────────────────────

    @Test
    void searchFiles_downloadUrlIsCorrectFormat() {
        S3Object file = S3Object.builder()
                .key("sandy/logistics-report.pdf")
                .size(1024L)
                .lastModified(Instant.now())
                .build();

        ListObjectsV2Response mockResponse = ListObjectsV2Response.builder()
                .contents(file)
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResponse);

        SearchResponseDto result = s3FileService.searchFiles("sandy", "logistics");
        FileMetadataDto dto = result.getFiles().get(0);

        assertThat(dto.getDownloadUrl()).isEqualTo("/api/files/sandy/download/logistics-report.pdf");
    }
}
