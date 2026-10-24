package com.server.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.server.server.entity.SystemMessage;

import java.util.List;

public interface SystemMessageService extends IService<SystemMessage> {

    List<SystemMessage> listByUserId(Long userId);

    boolean markRead(Long messageId);

    boolean createMessage(SystemMessage message);
}
