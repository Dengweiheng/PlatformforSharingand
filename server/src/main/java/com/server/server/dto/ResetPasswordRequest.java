package com.server.server.dto;

import lombok.Data;

@Data
public class ResetPasswordRequest {

    private String account;
    
    private String newPassword;

    private String captcha;
}
