package com.example.ShoppingSystem.quota.writeback;

/**
 * Dispatches IP risk writeback commands to asynchronous channel.
 */
public interface IpRiskWritebackDispatcher {

    void dispatch(IpRiskWritebackCommand command);

    void publishRetry(IpRiskWritebackCommand command, long delayMilli);

    void publishDeadLetter(IpRiskWritebackCommand command);
}
