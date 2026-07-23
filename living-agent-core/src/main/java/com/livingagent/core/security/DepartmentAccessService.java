package com.livingagent.core.security;

import org.springframework.stereotype.Service;

/**
 * 统一的部门访问权限判断服务。
 * 合并了原先分散在 WebSocketHandler、ChatService、ApiController、Interceptor 中的 hasDepartmentAccess 逻辑。
 *
 * <p>P14 改进：在原 accessLevel/founder 判定基础上，补充按 {@link UserIdentity}
 * 8 种身份做精细化白名单。判定优先级：
 * <ol>
 *   <li>ctx 为 null → 无权限</li>
 *   <li>public 部门 → 任何人可访问</li>
 *   <li>enterprise 部门 → identity.canAccessEnterprise() 且非 INTERNAL_DEPARTED</li>
 *   <li>INTERNAL_DEPARTED / EXTERNAL_VISITOR → 拒绝所有部门大脑（CHAT_ONLY）</li>
 *   <li>INTERNAL_ENTERPRISE（董事长）→ 全部 9 个部门大脑</li>
 *   <li>EXTERNAL_CUSTOMER（客户）→ 仅 cs（客服部）</li>
 *   <li>INTERNAL_ACTIVE / INTERNAL_PROBATION / EXTERNAL_PARTNER / EXTERNAL_CONTRACTOR → 仅 token.department</li>
 *   <li>accessLevel == FULL 或 founder → 兜底放行（保留管理员特权）</li>
 * </ol>
 *
 * @see UserIdentity
 * @see com.livingagent.gateway.websocket.DepartmentWebSocketHandler DepartmentWebSocketHandler 握手时调用本服务，失败返回 CloseStatus(4030)
 */
@Service
public class DepartmentAccessService {

    /**
     * 判断用户是否有权访问指定部门。
     */
    public boolean hasDepartmentAccess(AuthContext ctx, String department) {
        if (ctx == null) return false;
        if ("public".equals(department)) return true;

        // enterprise 部门：按 identity.canAccessEnterprise() 判定，离职员工即使持有 token 也拒绝
        if ("enterprise".equals(department)) {
            UserIdentity identity = ctx.getIdentity();
            if (identity == null) {
                // 兜底：未设置 identity 时按旧规则（FULL 或 founder）
                return ctx.getAccessLevel() == AccessLevel.FULL || ctx.isFounder();
            }
            return identity.canAccessEnterprise() && identity != UserIdentity.INTERNAL_DEPARTED
                && (ctx.getAccessLevel() == AccessLevel.FULL || ctx.isFounder() || identity.isActiveEmployee());
        }

        // 8 种 identity 精细化白名单
        UserIdentity identity = ctx.getIdentity();
        if (identity != null) {
            switch (identity) {
                case INTERNAL_DEPARTED:
                case EXTERNAL_VISITOR:
                    // CHAT_ONLY 用户不应进入任何部门大脑
                    return false;
                case INTERNAL_ENTERPRISE:
                    // 董事长：全部 9 个部门大脑
                    return true;
                case EXTERNAL_CUSTOMER:
                    // 客户：仅客服部
                    return "cs".equalsIgnoreCase(department);
                case INTERNAL_ACTIVE:
                case INTERNAL_PROBATION:
                case EXTERNAL_PARTNER:
                case EXTERNAL_CONTRACTOR:
                    // 在职/试用期/合作伙伴/外包：仅 token.department
                    String userDept = ctx.getDepartment();
                    return userDept != null && userDept.equalsIgnoreCase(department);
                default:
                    // 未知 identity 走旧规则兜底
                    break;
            }
        }

        // 兜底：保留旧规则（accessLevel==FULL 或 founder 可访问任何部门；CHAT_ONLY 拒绝；其他按部门匹配）
        if (ctx.getAccessLevel() == AccessLevel.FULL || ctx.isFounder()) return true;
        if (ctx.getAccessLevel() == AccessLevel.CHAT_ONLY) return false;
        String fallbackUserDept = ctx.getDepartment();
        return fallbackUserDept != null && fallbackUserDept.equalsIgnoreCase(department);
    }
}
