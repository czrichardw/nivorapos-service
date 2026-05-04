package id.nivorapos.pos_service.config

import org.slf4j.LoggerFactory
import java.io.PrintWriter
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.CallableStatement
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLFeatureNotSupportedException
import java.sql.Statement
import java.util.logging.Logger
import javax.sql.DataSource

class PsgsJdbcLoggingDataSource(
    private val delegate: DataSource,
    private val slowQueryThresholdMs: Long
) : DataSource, AutoCloseable {

    override fun getConnection(): Connection = proxyConnection(delegate.connection)

    override fun getConnection(username: String?, password: String?): Connection =
        proxyConnection(delegate.getConnection(username, password))

    override fun getLogWriter(): PrintWriter? = delegate.logWriter

    override fun setLogWriter(out: PrintWriter?) {
        delegate.logWriter = out
    }

    override fun setLoginTimeout(seconds: Int) {
        delegate.loginTimeout = seconds
    }

    override fun getLoginTimeout(): Int = delegate.loginTimeout

    override fun getParentLogger(): Logger = try {
        delegate.parentLogger
    } catch (e: SQLFeatureNotSupportedException) {
        throw e
    }

    override fun <T : Any?> unwrap(iface: Class<T>): T = delegate.unwrap(iface)

    override fun isWrapperFor(iface: Class<*>): Boolean = delegate.isWrapperFor(iface)

    override fun close() {
        (delegate as? AutoCloseable)?.close()
    }

    private fun proxyConnection(connection: Connection): Connection {
        return Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
            ConnectionHandler(connection, slowQueryThresholdMs)
        ) as Connection
    }

    private class ConnectionHandler(
        private val delegate: Connection,
        private val slowQueryThresholdMs: Long
    ) : InvocationHandler {

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            return when (method.name) {
                "prepareStatement" -> {
                    val sql = args?.firstOrNull() as? String
                    val statement = invokeDelegate(method, args) as PreparedStatement
                    proxyStatement(statement, sql, PreparedStatement::class.java)
                }
                "prepareCall" -> {
                    val sql = args?.firstOrNull() as? String
                    val statement = invokeDelegate(method, args) as CallableStatement
                    proxyStatement(statement, sql, CallableStatement::class.java)
                }
                "createStatement" -> {
                    val statement = invokeDelegate(method, args) as Statement
                    proxyStatement(statement, null, Statement::class.java)
                }
                "unwrap" -> unwrap(args)
                "isWrapperFor" -> isWrapperFor(args)
                else -> invokeDelegate(method, args)
            }
        }

        private fun unwrap(args: Array<out Any?>?): Any {
            val iface = args?.firstOrNull() as? Class<*>
            if (iface != null && iface.isInstance(delegate)) return delegate
            return delegate.unwrap(iface ?: Connection::class.java)
        }

        private fun isWrapperFor(args: Array<out Any?>?): Boolean {
            val iface = args?.firstOrNull() as? Class<*> ?: return false
            return iface.isInstance(delegate) || delegate.isWrapperFor(iface)
        }

        private fun invokeDelegate(method: Method, args: Array<out Any?>?): Any? {
            return try {
                method.invoke(delegate, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }

        private fun <T : Statement> proxyStatement(statement: T, sql: String?, iface: Class<*>): T {
            @Suppress("UNCHECKED_CAST")
            return Proxy.newProxyInstance(
                iface.classLoader,
                arrayOf(iface),
                StatementHandler(statement, sql, slowQueryThresholdMs)
            ) as T
        }
    }

    private class StatementHandler(
        private val delegate: Statement,
        private val fixedSql: String?,
        private val slowQueryThresholdMs: Long
    ) : InvocationHandler {

        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            if (!isExecuteMethod(method.name)) {
                return invokeDelegate(method, args)
            }

            val sql = querySql(method.name, args)
            val start = System.nanoTime()
            var thrown: Throwable? = null

            return try {
                invokeDelegate(method, args)
            } catch (e: Throwable) {
                thrown = e
                throw e
            } finally {
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                PsgsJdbcQueryMetrics.record(sql, elapsedMs)
                logSlowQuery(sql, elapsedMs, thrown)
            }
        }

        private fun isExecuteMethod(methodName: String): Boolean {
            return methodName == "execute" ||
                methodName == "executeQuery" ||
                methodName == "executeUpdate" ||
                methodName == "executeLargeUpdate" ||
                methodName == "executeBatch" ||
                methodName == "executeLargeBatch"
        }

        private fun querySql(methodName: String, args: Array<out Any?>?): String? {
            if (fixedSql != null) return fixedSql
            return args?.firstOrNull() as? String ?: methodName
        }

        private fun invokeDelegate(method: Method, args: Array<out Any?>?): Any? {
            return try {
                method.invoke(delegate, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }

        private fun logSlowQuery(sql: String?, elapsedMs: Long, thrown: Throwable?) {
            if (slowQueryThresholdMs <= 0 || elapsedMs < slowQueryThresholdMs) return

            val status = if (thrown == null) "" else " failed=${thrown.javaClass.simpleName}"
            log.info("[PSGS-SLOW] ${elapsedMs}ms$status ${oneLine(sql)}")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PsgsJdbcLoggingDataSource::class.java)

        fun oneLine(sql: String?): String {
            return sql
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?.take(500)
                ?: "(unknown sql)"
        }
    }
}
