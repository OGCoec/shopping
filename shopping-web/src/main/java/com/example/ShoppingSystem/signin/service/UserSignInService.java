package com.example.ShoppingSystem.signin.service;
import com.example.ShoppingSystem.signin.dto.UserSignInResponse;
import com.example.ShoppingSystem.signin.dto.UserSignInStatusResponse;
public interface UserSignInService {
    public UserSignInStatusResponse status(Long userId);

    public UserSignInResponse signIn(Long userId);
}
