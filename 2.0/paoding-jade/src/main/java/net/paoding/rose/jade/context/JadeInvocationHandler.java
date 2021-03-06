package net.paoding.rose.jade.context;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jdbc.core.RowMapper;

import net.paoding.rose.jade.annotation.DAO;
import net.paoding.rose.jade.annotation.SQLParam;
import net.paoding.rose.jade.annotation.SQLType;
import net.paoding.rose.jade.dataaccess.DataAccessFactory;
import net.paoding.rose.jade.rowmapper.RowMapperFactory;
import net.paoding.rose.jade.statement.DAOConfig;
import net.paoding.rose.jade.statement.DAOMetaData;
import net.paoding.rose.jade.statement.Interpreter;
import net.paoding.rose.jade.statement.InterpreterFactory;
import net.paoding.rose.jade.statement.JdbcStatement;
import net.paoding.rose.jade.statement.Querier;
import net.paoding.rose.jade.statement.SelectQuerier;
import net.paoding.rose.jade.statement.Statement;
import net.paoding.rose.jade.statement.StatementMetaData;
import net.paoding.rose.jade.statement.StatementWrapperProvider;
import net.paoding.rose.jade.statement.UpdateQuerier;

/**
 * DAO代理处理器（一个DAO类对应一个处理器实例）
 * 
 * @author 王志亮 [qieqie.wang@gmail.com]
 * 
 */
public class JadeInvocationHandler implements InvocationHandler {

    private static final Log logger = LogFactory.getLog(JadeInvocationHandler.class);
    private static final Log sqlLogger = LogFactory.getLog("jade_sql.log");

    private final ConcurrentHashMap<Method, Statement> statements = new ConcurrentHashMap<Method, Statement>();

    private final DAOMetaData daoMetaData;

    /**
     * 
     * @param daoMetaData
     */
    public JadeInvocationHandler(DAOMetaData daoMetaData) {
        this.daoMetaData = daoMetaData;
    }

    /**
     * 
     * @return
     */
    public DAOMetaData getDAOMetaData() {
        return daoMetaData;
    }

    private static final String[] INDEX_NAMES = new String[] { ":1", ":2", ":3", ":4", ":5", ":6",
            ":7", ":8", ":9", ":10", ":11", ":12", ":13", ":14", ":15", ":16", ":17", ":18", ":19",
            ":20", ":21", ":22", ":23", ":24", ":25", ":26", ":27", ":28", ":29", ":30", };

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        final boolean debugEnabled = logger.isDebugEnabled();
        if (debugEnabled) {
            logger.debug("invoking " + daoMetaData.getDAOClass().getName() + "#" + method.getName());
        }

        // 调用object的方法
        if (method.getDeclaringClass() == Object.class) {
            return invokeObjectMethod(proxy, method, args);
        }
        // 获取当前DAO方法对应的Statement对象
        Statement statement = getStatement(method);
        //
        // 将参数放入  Map
        Map<String, Object> parameters;
        StatementMetaData statemenetMetaData = statement.getMetaData();
        if (args == null || args.length == 0) {
            parameters = new HashMap<String, Object>(4);
        } else {
            parameters = new HashMap<String, Object>(args.length * 2 + 4);
            for (int i = 0; i < args.length; i++) {
                parameters.put(INDEX_NAMES[i], args[i]);
                SQLParam sqlParam = statemenetMetaData.getSQLParamAt(i);
                if (sqlParam != null) {
                    parameters.put(sqlParam.value(), args[i]);
                }
            }
        }
        // logging
        if (debugEnabled) {
            logger.info("invoking " + statemenetMetaData);
        }

        // executing
        long begin = System.currentTimeMillis();
        final Object result = statement.execute(parameters);
        long cost = System.currentTimeMillis() - begin;

        // logging
        if (sqlLogger.isInfoEnabled()) {
            sqlLogger.info(statemenetMetaData + "\tcost " + cost + "ms." );
        }
        return result;
    }

    private Statement getStatement(Method method) {
        Statement statement = statements.get(method);
        if (statement == null) {
            synchronized (method) {
                statement = statements.get(method);
                if (statement == null) {
                    // config
                    DAOConfig config = daoMetaData.getConfig();
                    DataAccessFactory dataAccessFactory = config.getDataAccessFactory();
                    RowMapperFactory rowMapperFactory = config.getRowMapperFactory();
                    InterpreterFactory interpreterFactory = config.getInterpreterFactory();
                    StatementWrapperProvider wrapperProvider = config.getStatementWrapperProvider();

                    // create
                    StatementMetaData smd = new StatementMetaData(daoMetaData, method);
                    SQLType sqlType = smd.getSQLType();
                    Querier querier;
                    if (sqlType == SQLType.READ) {
                        RowMapper<?> rowMapper = rowMapperFactory.getRowMapper(smd);
                        querier = new SelectQuerier(dataAccessFactory, smd, rowMapper);
                    } else {
                        querier = new UpdateQuerier(dataAccessFactory, smd);
                    }
                    Interpreter[] interpreters = interpreterFactory.getInterpreters(smd);
                    statement = new JdbcStatement(smd, sqlType, interpreters, querier);
                    if (wrapperProvider != null) {
                        statement = wrapperProvider.wrap(statement);
                    }
                    statements.put(method, statement);
                }
            }
        }
        return statement;
    }

    private Object invokeObjectMethod(Object proxy, Method method, Object[] args) 
            throws CloneNotSupportedException {
        String methodName = method.getName();
        if (methodName.equals("toString")) {
            return JadeInvocationHandler.this.toString();
        }
        if (methodName.equals("hashCode")) {
            return daoMetaData.getDAOClass().hashCode() * 13 + this.hashCode();
        }
        if (methodName.equals("equals")) {
            return args[0] == proxy;
        }
        if (methodName.equals("clone")) {
            throw new CloneNotSupportedException("clone is not supported for jade dao.");
        }
        throw new UnsupportedOperationException(daoMetaData.getDAOClass().getName() + "#"
                + method.getName());
    }

    @Override
    public String toString() {
        DAO dao = daoMetaData.getDAOClass().getAnnotation(DAO.class);
        String toString = daoMetaData.getDAOClass().getName()//
                + "[catalog=" + dao.catalog() + "]";
        return toString;
    }

}
