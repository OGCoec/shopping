package com.example.ShoppingSystem.service.user.auth.login;

import com.example.ShoppingSystem.service.user.auth.login.model.LoginFlowStartResult;
import com.example.ShoppingSystem.service.user.auth.login.model.LoginVerificationResult;

public interface UserPasswordLoginService {

    LoginFlowStartResult startFlow(String email,
                                   String deviceFingerprint,
                                   String preAuthToken,
                                   String riskLevel,
                                   String publicIp,
                                   String captchaUuid,
                                   String captchaCode,
                                   boolean wafResumeRequest);

    LoginVerificationResult currentFlow(String flowId, String preAuthToken);

    LoginVerificationResult verifyPassword(String flowId, String preAuthToken, String password);

    LoginVerificationResult sendEmailCode(String flowId, String preAuthToken);

    LoginVerificationResult verifyEmailCode(String flowId, String preAuthToken, String emailCode);

    LoginVerificationResult verifyTotp(String flowId, String preAuthToken, String code);

    LoginVerificationResult checkPhoneLoginCandidate(String dialCode, String phoneNumber);

    LoginVerificationResult sendPhoneBindCode(String flowId,
                                              String preAuthToken,
                                              String dialCode,
                                              String phoneNumber,
                                              String clientIp,
                                              String riskLevel,
                                              String deviceFingerprint,
                                              String captchaUuid,
                                              String captchaCode);

    LoginVerificationResult bindVerifiedPhone(String flowId,
                                              String preAuthToken,
                                              String dialCode,
                                              String phoneNumber,
                                              String smsCode);

    LoginVerificationResult sendPhoneLoginCode(String preAuthToken,
                                               String dialCode,
                                               String phoneNumber,
                                               String clientIp,
                                               String riskLevel,
                                               String deviceFingerprint,
                                               String captchaUuid,
                                               String captchaCode);

    boolean refreshPendingChallengeSelection(String email,
                                             String deviceFingerprint,
                                             String expectedChallengeType,
                                             String expectedChallengeSubType);
}
