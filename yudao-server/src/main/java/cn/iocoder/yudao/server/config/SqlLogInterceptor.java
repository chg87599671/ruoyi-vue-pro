package cn.iocoder.yudao.server.config;

import cn.hutool.core.date.LocalDateTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.springframework.stereotype.Component;

import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;

/**
 * mybatis sql打印插件
 * <p>
 * 拦截 StatementHandler的query查询方法，update新增，修改，删除方法
 *
 * @author chg
 */
@Intercepts({@Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}), @Signature(type = StatementHandler.class, method = "update", args = {Statement.class})})
@Slf4j
@Component
public class SqlLogInterceptor implements Interceptor {
    //版本不同，getValue方式有区别
    public static final String STR_STATEMENT_1 = "h.target.delegate.mappedStatement.id";
    public static final String STR_STATEMENT_2 = "delegate.mappedStatement.id";

    public static final String STR_REGISTRY_1 = "h.target.delegate.typeHandlerRegistry";
    public static final String STR_REGISTRY_2 = "delegate.typeHandlerRegistry";
    /**
     * 输出SQL调用方法全限定名称中需要忽略的公共部分
     */
    public static final String STR_PRINT_IGNORE = "cn.iocoder.yudao.module.";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {

        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        BoundSql boundSql = statementHandler.getBoundSql();

        String sql = boundSql.getSql();
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        Object parameterObject = boundSql.getParameterObject();

        MetaObject metaObject = MetaObject.forObject(statementHandler, SystemMetaObject.DEFAULT_OBJECT_FACTORY, SystemMetaObject.DEFAULT_OBJECT_WRAPPER_FACTORY, new DefaultReflectorFactory());
        String methodPath = "";
        try {
            methodPath = String.valueOf(metaObject.getValue(STR_STATEMENT_2));
        } catch (Exception e) {
            methodPath = String.valueOf(metaObject.getValue(STR_STATEMENT_1));
        }
        if (parameterMappings != null && parameterMappings.size() > 0) {
            TypeHandlerRegistry typeHandlerRegistry = new TypeHandlerRegistry();
            try {
                typeHandlerRegistry = (TypeHandlerRegistry) metaObject.getValue(STR_REGISTRY_2);
            } catch (Exception e) {
                typeHandlerRegistry = (TypeHandlerRegistry) metaObject.getValue(STR_REGISTRY_1);
            }
            for (ParameterMapping parameterMapping : parameterMappings) {
                Object value;
                String propertyName = parameterMapping.getProperty();
                if (boundSql.hasAdditionalParameter(propertyName)) {
                    value = boundSql.getAdditionalParameter(propertyName);
                } else if (parameterObject == null) {
                    value = null;
                } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) { // 基本类型，包装类
                    value = parameterObject;
                } else {
                    // 对象类型
                    MetaObject forObject = SystemMetaObject.forObject(parameterObject);
                    value = forObject.getValue(propertyName);
                }
                if (value instanceof Integer || value instanceof Long) {
                    value = String.valueOf(value);
                } else if (value instanceof Date) {
                    value = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value);
                } else if (value instanceof LocalDateTime) {
                    value = String.format("'%s'", LocalDateTimeUtil.formatNormal((LocalDateTime) value));
                } else if (value instanceof LocalDate) {
                    value = String.format("'%s'", LocalDateTimeUtil.formatNormal((LocalDate) value));
                } else if (value == null) {
                    value = null;
                } else {
                    value = String.format("'%s'", value);
                }
                sql = sql.replaceFirst("\\?", Matcher.quoteReplacement(String.valueOf(value)));
            }
        }
        String printStr = sql.replace("\n", " ") //减少换行
                .replaceAll(" +", " ")//压缩空格
                .replaceAll(" FROM", "\n FROM")
                .replaceAll(" WHERE", "\n WHERE")
                .replaceAll(" VALUES", "\n VALUES")
                .replaceAll(" LIMIT", "\n LIMIT")
                ;
        // 高亮打印sql
        System.err.println(String.format("==>> 方法\t%s\t执行sql: \n %s ;\n", methodPath.replace(STR_PRINT_IGNORE, ""), printStr));

        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof StatementHandler) {
            return Plugin.wrap(target, this);
        }
        return target;
    }

    @Override
    public void setProperties(Properties properties) {

    }
}