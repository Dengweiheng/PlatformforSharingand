package com.server.server.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HotTagVO {

    private Long tagId;

    private String tagName;

    private Long useCount;
}
