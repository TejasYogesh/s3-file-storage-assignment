package com.storage.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SearchResponseDto {
    private String userName;
    private String searchTerm;
    private int totalResults;
    private List<FileMetadataDto> files;
}
