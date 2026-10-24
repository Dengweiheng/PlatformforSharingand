package com.server.server.vo;

import com.server.server.entity.UserAction;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class InteractionRecordVO {

    private List<UserAction> downloadHistory = new ArrayList<>();

    private List<FavoriteFolderDetailVO> favoriteFolders = new ArrayList<>();
}
