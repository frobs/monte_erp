package com.entidaddesconocida.monte_erp.infrastructure.support;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;

/**
 * In-memory JDBC double built with dynamic proxies, so the infrastructure
 * adapters ({@code MySqlUserRepository}, {@code DatabaseConnection}) can be unit
 * tested without MySQL and without adding a database dependency.
 *
 * <p>It is a real {@link Driver} registered through {@link DriverManager}: any
 * URL starting with {@code jdbc:fake:} is served by the double. The double
 * records the SQL it receives and the parameters bound to each
 * {@link PreparedStatement}, and returns the rows configured with
 * {@link #withRows(Row...)} for the next query.</p>
 *
 * <p>Always pair {@link #install()} with {@link #uninstall()} (in
 * {@code @AfterEach}) so the global {@link DriverManager} is left clean.</p>
 */
public final class FakeJdbc implements Driver {

    /** Prefix of the URLs served by this double. */
    public static final String URL = "jdbc:fake:monte_erp";

    private static final String PREFIX = "jdbc:fake:";

    private final List<String> preparedSql = new ArrayList<>();
    private final List<Map<Integer, Object>> boundParameters = new ArrayList<>();
    private final List<String> executedStatements = new ArrayList<>();

    private List<Row> rows = List.of();
    private int generatedKey;
    private int updateCount = 1;
    private final Set<Object> closedConnections = Collections.newSetFromMap(new IdentityHashMap<>());
    private Object lastConnection;

    /** Registers the double in the global {@link DriverManager}. */
    public FakeJdbc install() {
        try {
            DriverManager.registerDriver(this);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not register the fake driver", e);
        }
        return this;
    }

    /** Removes the double from the global {@link DriverManager}. */
    public void uninstall() {
        try {
            DriverManager.deregisterDriver(this);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not deregister the fake driver", e);
        }
    }

    /**
     * Makes the most recently opened connection report itself as closed, which
     * is how a dropped JDBC connection looks to {@code DatabaseConnection}.
     */
    public void markConnectionClosed() {
        if (lastConnection != null) {
            closedConnections.add(lastConnection);
        }
    }

    /** Rows returned (in order) by every query executed from now on. */
    public FakeJdbc withRows(Row... newRows) {
        this.rows = List.of(newRows);
        return this;
    }

    /** Value of the single generated key returned after an {@code INSERT}. */
    public FakeJdbc withGeneratedKey(int key) {
        this.generatedKey = key;
        return this;
    }

    /** Rows affected reported by {@code executeUpdate()}. */
    public FakeJdbc withUpdateCount(int count) {
        this.updateCount = count;
        return this;
    }

    /** SQL passed to {@code prepareStatement(...)}, in call order. */
    public List<String> preparedSql() {
        return preparedSql;
    }

    /** Bound parameters of every prepared statement, in call order. */
    public List<Map<Integer, Object>> boundParameters() {
        return boundParameters;
    }

    /** SQL passed to {@code createStatement().execute*(...)}. */
    public List<String> executedStatements() {
        return executedStatements;
    }

    /** Builds a result row from alternating column name / value pairs. */
    public static Row row(Object... columnsAndValues) {
        return Row.of(columnsAndValues);
    }

    // --- java.sql.Driver -------------------------------------------------

    @Override
    public Connection connect(String url, Properties info) {
        if (!acceptsURL(url)) {
            return null;
        }
        Connection connection = connectionProxy();
        lastConnection = connection;
        return connection;
    }

    @Override
    public boolean acceptsURL(String url) {
        return url != null && url.startsWith(PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 1;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    // --- Proxies --------------------------------------------------------

    private Connection connectionProxy() {
        return proxy(Connection.class, (proxy, method, args) -> switch (method.getName()) {
            case "prepareStatement" -> prepareStatementProxy((String) args[0]);
            case "createStatement" -> statementProxy();
            case "isClosed" -> closedConnections.contains(proxy);
            case "close" -> {
                closedConnections.add(proxy);
                yield null;
            }
            case "getAutoCommit" -> true;
            case "toString" -> "FakeConnection";
            default -> defaultValue(method);
        });
    }

    private PreparedStatement prepareStatementProxy(String sql) {
        preparedSql.add(sql);
        Map<Integer, Object> parameters = new LinkedHashMap<>();
        boundParameters.add(parameters);
        return proxy(PreparedStatement.class, (proxy, method, args) -> {
            String name = method.getName();
            if (name.startsWith("set") && args != null && args.length == 2 && args[0] instanceof Integer index) {
                // setNull(index, sqlType) binds SQL NULL: the second argument is
                // the column type, not the value, so it is recorded as null.
                parameters.put(index, "setNull".equals(name) ? null : args[1]);
                return null;
            }
            return switch (name) {
                case "executeQuery" -> resultSetProxy(rows);
                case "executeUpdate" -> updateCount;
                case "execute" -> true;
                case "getGeneratedKeys" -> resultSetProxy(generatedKeyRows());
                case "toString" -> "FakePreparedStatement[" + sql + "]";
                default -> defaultValue(method);
            };
        });
    }

    private Statement statementProxy() {
        return proxy(Statement.class, (proxy, method, args) -> switch (method.getName()) {
            case "executeQuery" -> {
                executedStatements.add((String) args[0]);
                yield resultSetProxy(rows);
            }
            case "execute" -> {
                executedStatements.add((String) args[0]);
                yield true;
            }
            case "executeUpdate" -> {
                executedStatements.add((String) args[0]);
                yield updateCount;
            }
            case "toString" -> "FakeStatement";
            default -> defaultValue(method);
        });
    }

    private ResultSet resultSetProxy(List<Row> resultRows) {
        int[] index = {-1};
        // Real JDBC semantics: wasNull() reports whether the column read last
        // was SQL NULL, which is how a nullable category_id is detected.
        boolean[] lastReadWasNull = {false};
        return proxy(ResultSet.class, (proxy, method, args) -> switch (method.getName()) {
            case "next" -> ++index[0] < resultRows.size();
            case "close" -> null;
            case "wasNull" -> lastReadWasNull[0];
            case "toString" -> "FakeResultSet" + resultRows;
            default -> readColumn(method, args, resultRows, index[0], lastReadWasNull);
        });
    }

    private List<Row> generatedKeyRows() {
        return generatedKey > 0 ? List.of(row("id", generatedKey)) : List.of();
    }

    private static Object readColumn(Method method, Object[] args, List<Row> resultRows, int index,
                                     boolean[] lastReadWasNull) {
        if (index < 0 || index >= resultRows.size() || args == null || args.length == 0) {
            lastReadWasNull[0] = false;
            return defaultValue(method);
        }
        Map<String, Object> values = resultRows.get(index).values();
        Object raw;
        if (args[0] instanceof String column) {
            raw = values.get(column);
        } else {
            raw = values.values().stream().skip(((Integer) args[0]) - 1L).findFirst().orElse(null);
        }
        lastReadWasNull[0] = raw == null;
        return convert(raw, method.getReturnType());
    }

    private static Object convert(Object raw, Class<?> returnType) {
        if (raw == null) {
            return defaultValue(returnType);
        }
        if (returnType == int.class || returnType == Integer.class) {
            return ((Number) raw).intValue();
        }
        if (returnType == long.class || returnType == Long.class) {
            return ((Number) raw).longValue();
        }
        if (returnType == boolean.class || returnType == Boolean.class) {
            return raw instanceof Boolean value ? value : Boolean.parseBoolean(raw.toString());
        }
        if (returnType == String.class) {
            return raw.toString();
        }
        return raw;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(FakeJdbc.class.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Method method) {
        return defaultValue(method.getReturnType());
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return null;
    }

    /**
     * A result row: column name to value, keeping the insertion order so
     * index-based reads ({@code getInt(1)}) work too.
     */
    public record Row(Map<String, Object> values) {

        public static Row of(Object... columnsAndValues) {
            if (columnsAndValues.length % 2 != 0) {
                throw new IllegalArgumentException("Expected alternating column/value pairs");
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < columnsAndValues.length; i += 2) {
                values.put((String) columnsAndValues[i], columnsAndValues[i + 1]);
            }
            return new Row(Collections.unmodifiableMap(values));
        }
    }
}
