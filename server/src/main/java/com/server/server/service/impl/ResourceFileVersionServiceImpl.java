package com.server.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.server.server.entity.ResourceFileVersion;
import com.server.server.mapper.ResourceFileVersionMapper;
import com.server.server.service.ResourceFileVersionService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ResourceFileVersionServiceImpl extends ServiceImpl<ResourceFileVersionMapper, ResourceFileVersion>
        implements ResourceFileVersionService {

    @Override
    public List<ResourceFileVersion> listByResourceId(Long resourceId) {
        return this.list(new LambdaQueryWrapper<ResourceFileVersion>()
                .eq(ResourceFileVersion::getResourceId, resourceId)
                .orderByDesc(ResourceFileVersion::getVersionNo));
    }

    @Override
    public Integer nextVersionNo(Long resourceId) {
        ResourceFileVersion latest = this.getOne(new LambdaQueryWrapper<ResourceFileVersion>()
                .eq(ResourceFileVersion::getResourceId, resourceId)
                .orderByDesc(ResourceFileVersion::getVersionNo)
                .last("limit 1"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }
}
