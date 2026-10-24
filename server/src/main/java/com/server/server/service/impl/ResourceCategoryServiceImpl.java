package com.server.server.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.server.server.entity.ResourceCategory;
import com.server.server.mapper.ResourceCategoryMapper;
import com.server.server.service.ResourceCategoryService;
import org.springframework.stereotype.Service;

@Service
public class ResourceCategoryServiceImpl extends ServiceImpl<ResourceCategoryMapper, ResourceCategory>
        implements ResourceCategoryService {
}
