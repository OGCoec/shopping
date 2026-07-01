package com.example.ShoppingSystem.service.user.auth.register;
import com.example.ShoppingSystem.service.user.auth.register.model.ChallengeSelection;
import com.example.ShoppingSystem.service.user.auth.register.model.RiskSnapshot;
public interface EmailCodeDispatchService {
    public void dispatchRegisterEmailCode(String email,
                                          String username,
                                          String rawPassword,
                                          String deviceFingerprint,
                                          String publicIp,
                                          RiskSnapshot riskSnapshot,
                                          ChallengeSelection challengeSelection);
}
