package com.server.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("resource_tag_rel")
public class ResourceTagRel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resourceId;

    private Long tagId;
}
