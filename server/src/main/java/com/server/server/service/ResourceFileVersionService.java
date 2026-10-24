package com.server.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.server.server.entity.ResourceFileVersion;

import java.util.List;

public interface ResourceFileVersionService extends IService<ResourceFileVersion> {

    List<ResourceFileVersion> listByResourceId(Long resourceId);

    Integer nextVersionNo(Long resourceId);
}
