package com.server.server.controller;

import com.server.server.common.ApiResponse;
import com.server.server.entity.UserAction;
import com.server.server.service.UserActionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/actions")
public class UserActionController {

    private final UserActionService userActionService;

    public UserActionController(UserActionService userActionService) {
        this.userActionService = userActionService;
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<UserAction>> listByUser(@PathVariable Long userId) {
        return ApiResponse.success(userActionService.listByUserId(userId));
    }
}
