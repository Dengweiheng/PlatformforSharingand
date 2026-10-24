package com.server.server.dto;

import lombok.Data;

import java.util.List;

@Data
public class BatchRemoveFavoriteRequest {

    private Long userId;

    private Long folderId;

    private List<Long> itemIds;
}
