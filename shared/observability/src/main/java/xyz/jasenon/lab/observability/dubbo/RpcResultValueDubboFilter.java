package xyz.jasenon.lab.observability.dubbo;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;

/**
 * Normalize service failures before Dubbo's ExceptionFilter listener can replace
 * an undeclared exception with an untyped RuntimeException. The outer result
 * filter also covers authorization/context filters and retains the trace scope.
 */
@Activate(group = CommonConstants.PROVIDER, order = 10000)
public class RpcResultValueDubboFilter extends RpcResultDubboFilter {
}
