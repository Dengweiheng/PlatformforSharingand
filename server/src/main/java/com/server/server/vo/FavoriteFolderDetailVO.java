package com.server.server.vo;

import com.server.server.entity.FavoriteFolder;
import com.server.server.entity.ResourceInfo;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FavoriteFolderDetailVO {

    private FavoriteFolder folder;

    private List<ResourceInfo> resources = new ArrayList<>();
}
