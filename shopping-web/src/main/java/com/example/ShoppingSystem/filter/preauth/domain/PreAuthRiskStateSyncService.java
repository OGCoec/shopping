package com.example.ShoppingSystem.filter.preauth.domain;
import com.example.ShoppingSystem.filter.preauth.model.PreAuthBinding;
import jakarta.servlet.http.HttpServletRequest;
public interface PreAuthRiskStateSyncService {
    public void syncAfterBindingSaved(PreAuthBinding previous,
                                      PreAuthBinding current,
                                      HttpServletRequest request);

    public void forceClearDerivedState(PreAuthBinding current, HttpServletRequest request);
}
