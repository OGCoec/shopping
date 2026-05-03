package com.example.ShoppingSystem.controller.login.user.dto;

import lombok.Data;

@Data
public class RegisterFlowStartRequest {

    private String email;
    private String deviceFingerprint;
}
