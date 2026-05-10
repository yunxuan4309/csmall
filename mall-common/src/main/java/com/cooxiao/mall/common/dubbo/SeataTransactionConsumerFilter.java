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
 * Seata事务上下文传播过滤器 - 消费者端
 * 替代 dubbo-filter-seata:1.0.2 中有NPE bug的SeataTransactionPropagationConsumerFilter
 * (Seata Issue #6815: branchType为null时导致NPE)
 */
@Activate(group = "consumer")
public class SeataTransactionConsumerFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String xid = RootContext.getXID();
        if (xid != null) {
            invocation.setAttachment("TX_XID", xid);
            BranchType branchType = RootContext.getBranchType();
            if (branchType != null) {
                invocation.setAttachment("TX_BRANCH_TYPE", branchType.name());
            }
        }
        return invoker.invoke(invocation);
    }
}
