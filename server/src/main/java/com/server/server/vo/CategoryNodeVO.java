package com.server.server.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryNodeVO {

    private Long id;

    private Long parentId;

    private String name;

    private Integer sortNo;

    private List<CategoryNodeVO> children = new ArrayList<>();
}
