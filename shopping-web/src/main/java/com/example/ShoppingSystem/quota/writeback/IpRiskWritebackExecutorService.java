package com.example.ShoppingSystem.quota.writeback;
import com.example.ShoppingSystem.quota.IpRiskCachedPayload;
import java.util.Set;
public interface IpRiskWritebackExecutorService {
    public void executeActions(String ip, IpRiskCachedPayload payload, Set<IpRiskWritebackAction> actions);
}
