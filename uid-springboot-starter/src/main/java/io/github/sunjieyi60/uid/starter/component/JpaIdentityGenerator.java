package io.github.sunjieyi60.uid.starter.component;

import io.github.sunjieyi60.uid.starter.UidGenerator;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentityGenerator;

import java.io.Serializable;

/**
 * 自定义JPA#Identity策略生成器
 * –IDENTITY：采用数据库ID自增长的方式来自增主键字段（Oracle 不支持）；
 * –AUTO： JPA自动选择合适的策略，是默认选项；
 * –SEQUENCE：通过序列产生主键，通过@SequenceGenerator 注解指定序列名（MySql不支持）
 * –TABLE：通过表产生主键，框架借由表模拟序列产生主键，使用该策略可以使应用更易于数据库移植。
 *
 * @author DengJun 2021/4/21
 */
public class JpaIdentityGenerator extends IdentityGenerator {
    @Override
    public Serializable generate(SharedSessionContractImplementor s, Object obj) {
        return getInstance().getUID();
    }

    public static UidGenerator getInstance() {
        return UidGeneratorHolder.HOLDER.uidGenerator;
    }

    public static void setInstance(final UidGenerator uidGenerator) {
        if (UidGeneratorHolder.HOLDER.uidGenerator == null) {
            UidGeneratorHolder.HOLDER.setHolder(uidGenerator);
        }
    }

    private enum UidGeneratorHolder {
        HOLDER;
        private UidGenerator uidGenerator;

        public void setHolder(UidGenerator uidGenerator) {
            HOLDER.uidGenerator = uidGenerator;
        }
    }
}
