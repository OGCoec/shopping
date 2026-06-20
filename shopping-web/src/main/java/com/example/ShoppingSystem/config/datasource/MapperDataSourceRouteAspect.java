package com.example.ShoppingSystem.config.datasource;

import com.example.ShoppingSystem.common.datasource.DataSourceRoute;
import com.example.ShoppingSystem.common.datasource.DataSourceRouteGroups;
import com.example.ShoppingSystem.common.datasource.RoutingDataSourceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class MapperDataSourceRouteAspect {

    @Around("""
            execution(* com.example.ShoppingSystem.mapper.user.UserLoginIdentityMapper.*(..))
                    || execution(* com.example.ShoppingSystem.mapper.user.UserProfileMapper.*(..))
                    || execution(* com.example.ShoppingSystem.mapper.user.UserAccountSelfDeletionMapper.*(..))
                    || execution(* com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.listSelfTerminations(..))
                    || execution(* com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.findSelfTerminationById(..))
                    || execution(* com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.restoreDisabledIdentity(..))
                    || execution(* com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.markSelfTerminationRestored(..))
            """)
    public Object routeCoreMapper(ProceedingJoinPoint joinPoint) throws Throwable {
        return routeIfDefault(DataSourceRoute.CORE, joinPoint);
    }

    @Around("""
            execution(* com.example.ShoppingSystem.mapper.product.ProductCategoryMapper.*(..))
                    || execution(* com.example.ShoppingSystem.mapper.product.ProductSpuMapper.*(..))
                    || execution(* com.example.ShoppingSystem.mapper.product.ProductHotSkuMapper.*(..))
                    || execution(* com.example.ShoppingSystem.mapper.product.OrderProductSkuMapper.*(..))
            """)
    public Object routeProductMapper(ProceedingJoinPoint joinPoint) throws Throwable {
        return routeIfDefault(DataSourceRoute.PRODUCT, joinPoint);
    }

    @Around("""
            execution(* com.example.ShoppingSystem.mapper.coupon.CouponTemplateMapper.*(..))
                    || execution(* com.example.ShoppingSystem.mapper.coupon.CouponScopeMapper.*(..))
            """)
    public Object routeCouponMapper(ProceedingJoinPoint joinPoint) throws Throwable {
        return routeIfDefault(DataSourceRoute.COUPON, joinPoint);
    }

    @Around("""
            execution(* com.example.ShoppingSystem.mapper.coupon.UserCouponMapper.*(..))
                    || execution(* com.example.ShoppingSystem.mapper.coupon.CouponUsageRecordMapper.*(..))
                    || execution(* com.example.ShoppingSystem.mapper.order.OrderMapper.*(..))
                    || execution(* com.example.ShoppingSystem.mapper.signin.UserSignInMapper.*(..))
                    || execution(* com.example.ShoppingSystem.mapper.product.CardSecretInventoryMapper.*(..))
                    || execution(* com.example.ShoppingSystem.mapper.order.OrderCardSecretDeliveryMapper.*(..))
                    || execution(* com.example.ShoppingSystem.mapper.order.PaymentCallbackInboxMapper.*(..))
                    || execution(* com.example.ShoppingSystem.mapper.order.PaymentRefundMapper.*(..))
            """)
    public Object routeTradeMapper(ProceedingJoinPoint joinPoint) throws Throwable {
        return routeIfDefault(DataSourceRoute.TRADE, joinPoint);
    }

    @Around("""
            execution(* com.example.ShoppingSystem.mapper.risk.IpReputationProfileMapper.*(..))
                    || execution(* com.example.ShoppingSystem.mapper.risk.RegisterRiskProfileMapper.*(..))
                    || execution(* com.example.ShoppingSystem.mapper.risk.AdminDeviceRiskProfileMapper.*(..))
                    || execution(* com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.listAccountCreditProfiles(..))
                    || execution(* com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.findAccountCreditDetail(..))
                    || execution(* com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.findFirstLoginRecord(..))
                    || execution(* com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.listRiskScoreEvents(..))
                    || execution(* com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.listRecentRiskScoreEvents(..))
                    || execution(* com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.lockRiskProfileForAdjust(..))
                    || execution(* com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.updateRiskProfileScore(..))
                    || execution(* com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.insertRiskScoreEvent(..))
                    || execution(* com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.listRiskTerminations(..))
                    || execution(* com.example.ShoppingSystem.mapper.admin.AdminAccountManagementMapper.findRiskTerminationById(..))
                    || execution(* com.example.ShoppingSystem.mapper.risk.UserRiskAccountTerminationMapper.upsertRiskTermination(..))
                    || execution(* com.example.ShoppingSystem.mapper.risk.UserRiskAccountTerminationMapper.countTerminatedEmailHashes(..))
                    || execution(* com.example.ShoppingSystem.mapper.risk.UserRiskAccountTerminationMapper.listTerminatedEmailHashes(..))
                    || execution(* com.example.ShoppingSystem.mapper.risk.UserRiskAccountTerminationMapper.existsByEmailHash(..))
                    || execution(* com.example.ShoppingSystem.mapper.risk.UserRiskAccountTerminationMapper.listExpiredRiskTerminatedUserIds(..))
                    || execution(* com.example.ShoppingSystem.mapper.risk.UserRiskProfileMapper.findUserRiskStateByUserId(..))
                    || execution(* com.example.ShoppingSystem.mapper.risk.UserRiskProfileMapper.upsertUserAuthLockState(..))
                    || execution(* com.example.ShoppingSystem.mapper.risk.UserRiskProfileMapper.insertUserRiskScoreEvent(..))
                    || execution(* com.example.ShoppingSystem.mapper.risk.UserRiskProfileMapper.touchUserNetworkState(..))
                    || execution(* com.example.ShoppingSystem.mapper.risk.UserRiskProfileMapper.markRiskRecoveryStarted(..))
                    || execution(* com.example.ShoppingSystem.mapper.risk.UserRiskProfileMapper.listStableUnlockedUserRecoveryCandidates(..))
                    || execution(* com.example.ShoppingSystem.mapper.risk.UserRiskProfileMapper.listStableUnlockedUserRecoveryCandidatesByReason(..))
                    || execution(* com.example.ShoppingSystem.mapper.risk.UserRiskProfileMapper.recoverStableUnlockedUsersByUserIds(..))
                    || execution(* com.example.ShoppingSystem.mapper.risk.UserRiskProfileMapper.recoverStableUnlockedUsersByReasonAndUserIds(..))
            """)
    public Object routeRiskMapper(ProceedingJoinPoint joinPoint) throws Throwable {
        return routeIfDefault(DataSourceRoute.RISK, joinPoint);
    }

    private Object routeIfDefault(DataSourceRoute route, ProceedingJoinPoint joinPoint) throws Throwable {
        DataSourceRoute previousRoute = RoutingDataSourceContext.snapshot();
        if (previousRoute != null) {
            if (!DataSourceRouteGroups.sameDomain(previousRoute, route)) {
                throw new IllegalStateException(
                        "Mapper data source route conflict, current=" + previousRoute
                                + ", mapperRoute=" + route
                                + ", mapper=" + joinPoint.getSignature().toShortString()
                );
            }
            return joinPoint.proceed();
        }
        try {
            RoutingDataSourceContext.use(route);
            return joinPoint.proceed();
        } finally {
            RoutingDataSourceContext.restore(previousRoute);
        }
    }
}
