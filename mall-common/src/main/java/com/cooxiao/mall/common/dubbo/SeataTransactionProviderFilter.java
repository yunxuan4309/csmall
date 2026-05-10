package com.cooxiao.mall.common.dubbo;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.seata.core.context.RootContext;
import org.apache.seata.core.model.BranchType;

/**
 * Seata事务上下文传播过滤器 - 提供者端
 * 替代 dubbo-filter-seata:1.0.2 中有NPE bug的SeataTransactionPropagationProviderFilter
 * (Seata Issue #6815: branchType为null时导致NPE)
 */
@Activate(group = "provider")
public class SeataTransactionProviderFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String xid = invocation.getAttachment("TX_XID");
        if (xid != null) {
            RootContext.bind(xid);
            String branchTypeStr = invocation.getAttachment("TX_BRANCH_TYPE");
            if (branchTypeStr != null) {
                RootContext.bindBranchType(BranchType.valueOf(branchTypeStr));
            }
        }
        try {
            return invoker.invoke(invocation);
        } finally {
            if (xid != null) {
                RootContext.unbind();
                RootContext.unbindBranchType();
            }
        }
    }
}
