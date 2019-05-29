import org.apache.derby.drda.NetworkServerControl
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.Velocity
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants
import org.gradle.internal.impldep.com.google.common.io.Files
import java.io.*
import java.net.InetAddress
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Instant
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.io.PrintWriter
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }
    dependencies {
        classpath("org.apache.derby:derby:10.12.1.1")
        classpath("org.apache.derby:derbyclient:10.12.1.1")
        classpath("org.apache.derby:derbynet:10.12.1.1")
        classpath("org.apache.velocity:velocity:1.7")
    }
}

/**
 * Tool to run database scripts
 */
class ScriptRunner
/**
 * Default constructor
 */
(private val connection: Connection, private val autoCommit: Boolean,
 private val stopOnError: Boolean) {

    private var logWriter: PrintWriter? = null
    private var errorLogWriter: PrintWriter? = null

    private var delimiter = DEFAULT_DELIMITER
    private var fullLineDelimiter = false

    private var userDirectory = System.getProperty("user.dir")

    init {
        val logFile = File("create_db.log")
        val errorLogFile = File("create_db_error.log")
        try {
            if (logFile.exists()) {
                logWriter = PrintWriter(FileWriter(logFile, true))
            } else {
                logWriter = PrintWriter(FileWriter(logFile, false))
            }
        } catch (e: IOException) {
            System.err.println("Unable to access or create the db_create log")
        }

        try {
            if (errorLogFile.exists()) {
                errorLogWriter = PrintWriter(FileWriter(errorLogFile, true))
            } else {
                errorLogWriter = PrintWriter(FileWriter(errorLogFile, false))
            }
        } catch (e: IOException) {
            System.err.println("Unable to access or create the db_create error log")
        }

        val timeStamp = SimpleDateFormat("dd/mm/yyyy HH:mm:ss").format(Date())
        println("\n-------\n$timeStamp\n-------\n")
        printlnError("\n-------\n$timeStamp\n-------\n")
    }

    fun setDelimiter(delimiter: String, fullLineDelimiter: Boolean) {
        this.delimiter = delimiter
        this.fullLineDelimiter = fullLineDelimiter
    }

    /**
     * Setter for logWriter property
     *
     * @param logWriter - the new value of the logWriter property
     */
    fun setLogWriter(logWriter: PrintWriter) {
        this.logWriter = logWriter
    }

    /**
     * Setter for errorLogWriter property
     *
     * @param errorLogWriter - the new value of the errorLogWriter property
     */
    fun setErrorLogWriter(errorLogWriter: PrintWriter) {
        this.errorLogWriter = errorLogWriter
    }

    /**
     * Set the current working directory.  Source commands will be relative to this.
     */
    fun setUserDirectory(userDirectory: String) {
        this.userDirectory = userDirectory
    }

    /**
     * Runs an SQL script (read in using the Reader parameter)
     *
     * @param filepath - the filepath of the script to run. May be relative to the userDirectory.
     */
    @Throws(IOException::class, SQLException::class)
    fun runScript(filepath: String) {
        val file = File(userDirectory, filepath)
        this.runScript(BufferedReader(FileReader(file)))
    }

    /**
     * Runs an SQL script (read in using the Reader parameter)
     *
     * @param reader - the source of the script
     */
    @Throws(IOException::class, SQLException::class)
    fun runScript(reader: Reader) {
        try {
            val originalAutoCommit = connection.autoCommit
            try {
                if (originalAutoCommit != this.autoCommit) {
                    connection.autoCommit = this.autoCommit
                }
                runScript(connection, reader)
            } finally {
                connection.autoCommit = originalAutoCommit
            }
        } catch (e: IOException) {
            throw e
        } catch (e: SQLException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException("Error running script.  Cause: $e", e)
        }

    }

    /**
     * Runs an SQL script (read in using the Reader parameter) using the
     * connection passed in
     *
     * @param conn - the connection to use for the script
     * @param reader - the source of the script
     * @throws SQLException if any SQL errors occur
     * @throws IOException if there is an error reading from the Reader
     */
    @Throws(IOException::class, SQLException::class)
    private fun runScript(conn: Connection, reader: Reader) {
        var command: StringBuffer? = null
        try {
            val lineReader = LineNumberReader(reader)
            var line: String? = lineReader.readLine()
            do {
                if (command == null) {
                    command = StringBuffer()
                }
                val trimmedLine = line?.trim { it <= ' ' }
                val delimMatch = delimP.matcher(trimmedLine)
                if (trimmedLine?.length!! < 1 || trimmedLine.startsWith("//")) {
                    // Do nothing
                } else if (delimMatch.matches()) {
                    setDelimiter(delimMatch.group(2), false)
                } else if (trimmedLine.startsWith("--")) {
                    println(trimmedLine)
                } else if (trimmedLine.length < 1 || trimmedLine.startsWith("--")) {
                    // Do nothing
                } else if (!fullLineDelimiter && trimmedLine.endsWith(delimiter) || fullLineDelimiter && trimmedLine == delimiter) {
                    command.append(line?.substring(0, line
                            .lastIndexOf(delimiter)))
                    command.append(" ")
                    this.execCommand(conn, command, lineReader)
                    command = null
                } else {
                    command.append(line)
                    command.append("\n")
                }

                line = lineReader.readLine()
            } while (line != null)
            if (command != null) {
                this.execCommand(conn, command, lineReader)
            }
            if (!autoCommit) {
                conn.commit()
            }
        } catch (e: IOException) {
            throw IOException(String.format("Error executing '%s': %s", command, e.message), e)
        } finally {
            conn.rollback()
            flush()
        }
    }

    @Throws(IOException::class, SQLException::class)
    private fun execCommand(conn: Connection, command: StringBuffer,
                            lineReader: LineNumberReader) {

        if (command.length == 0) {
            return
        }

        val sourceCommandMatcher = SOURCE_COMMAND.matcher(command)
        if (sourceCommandMatcher.matches()) {
            this.runScriptFile(conn, sourceCommandMatcher.group(1))
            return
        }

        this.execSqlCommand(conn, command, lineReader)
    }

    @Throws(IOException::class, SQLException::class)
    private fun runScriptFile(conn: Connection, filepath: String) {
        val file = File(userDirectory, filepath)
        this.runScript(conn, BufferedReader(FileReader(file)))
    }

    @Throws(SQLException::class)
    private fun execSqlCommand(conn: Connection, command: StringBuffer,
                               lineReader: LineNumberReader) {

        val statement = conn.createStatement()

        println(command)

        var hasResults = false
        try {
            hasResults = statement.execute(command.toString())
        } catch (e: SQLException) {
            val errText = String.format("Error executing '%s' (line %d): %s",
                    command, lineReader.lineNumber, e.message)
            printlnError(errText)
            System.err.println(errText)
            if (stopOnError) {
                throw SQLException(errText, e)
            }
        }

        if (autoCommit && !conn.autoCommit) {
            conn.commit()
        }

        val rs = statement.resultSet
        if (hasResults && rs != null) {
            val md = rs.metaData
            val cols = md.columnCount
            for (i in 1..cols) {
                val name = md.getColumnLabel(i)
                print(name + "\t")
            }
            println("")
            while (rs.next()) {
                for (i in 1..cols) {
                    val value = rs.getString(i)
                    print(value + "\t")
                }
                println("")
            }
        }

        try {
            statement.close()
        } catch (e: Exception) {
            // Ignore to workaround a bug in Jakarta DBCP
        }

    }

    private fun print(o: Any) {
        if (logWriter != null) {
            logWriter!!.print(o)
        }
    }

    private fun println(o: Any) {
        if (logWriter != null) {
            logWriter!!.println(o)
        }
    }

    private fun printlnError(o: Any) {
        if (errorLogWriter != null) {
            errorLogWriter!!.println(o)
        }
    }

    private fun flush() {
        if (logWriter != null) {
            logWriter!!.flush()
        }
        if (errorLogWriter != null) {
            errorLogWriter!!.flush()
        }
    }

    companion object {

        private val DEFAULT_DELIMITER = ";"
        private val SOURCE_COMMAND = Pattern.compile("^\\s*SOURCE\\s+(.*?)\\s*$", Pattern.CASE_INSENSITIVE)

        /**
         * regex to detect delimiter.
         * ignores spaces, allows delimiter in comment, allows an equals-sign
         */
        val delimP = Pattern.compile("^\\s*(--)?\\s*delimiter\\s*=?\\s*([^\\s]+)+\\s*.*$", Pattern.CASE_INSENSITIVE)
    }
}

open class Script {
    var source: File? = null
}

open class DerbyTestPluginExtension @javax.inject.Inject constructor(objectFactory: ObjectFactory) {
    var message = "Hello from GreetingPlugin"
    var databaseName = "rollerdb"
    var username = "APP"
    var password = "APP"
    var port = 4224

    val script: Script = objectFactory.newInstance()

    fun script(action: Action<in Script>) {
        action.execute(script)
    }
}

fun getGitHash(): String {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine = listOf("git", "rev-parse", "HEAD")
        standardOutput = stdout
    }
    return "r" + stdout.toString().trim()
}

project.ext.set("buildNumber", getGitHash())
project.ext.set("timestamp", Instant.now().toEpochMilli())
project.ext.set("username", System.getProperty("user.name"))

class DerbyTestPlugin : Plugin<Project> {
    override fun apply(project: Project) {

        val derby = project.extensions.create<DerbyTestPluginExtension>("derby")
        var server: NetworkServerControl? = null

        val errorStartingDatabase: () -> String = { "Error starting the server for database ${derby.databaseName}." }
        val errorStoppingDatabase: () -> String = { "Error starting the server for database ${derby.databaseName}." }

        val startDerby = project.task("startDerby") {

            doLast {
                logger.info("Starting embedded Derby database")
                try {
                    server = NetworkServerControl(InetAddress.getByName("localhost"), derby.port)
                    server!!.start(null)
                    Thread.sleep(5000)
                } catch (exception: Exception) {
                    logger.error(errorStartingDatabase(), exception)
                    throw GradleException(errorStartingDatabase(), exception)
                }


                try {
                    Class.forName("org.apache.derby.jdbc.ClientDriver").getDeclaredConstructor().newInstance()
                } catch (exception: Exception) {
                    logger.error(errorStartingDatabase(), exception)
                    throw GradleException(errorStartingDatabase(), exception)
                }

                try {
                    DriverManager
                            .getConnection("jdbc:derby://127.0.0.1:${derby.port}/memory:${derby.databaseName};create=true", derby.username, if (derby.password.isEmpty()) null else derby.password)
                            .use { connection ->
                                    connection.autoCommit = true
                                    val reader = FileReader(derby.script.source)
                                    reader.use {
                                        val runner = ScriptRunner(connection, autoCommit = true, stopOnError = true)
                                        runner.runScript(it)
                                    }
                            }

                } catch (exception: SQLException) {
                    logger.error(errorStartingDatabase(), exception)
                    throw GradleException(errorStartingDatabase(), exception)
                }
                logger.info("Started embedded Derby database")
            }
        }

        val stopDerby = project.task("stopDerby") {
            doLast {
                if (server != null) {
                    try {
                        DriverManager.getConnection("jdbc:derby://127.0.0.1:${derby.port}/memory:${derby.databaseName};drop=true", derby.username, if (derby.password.isEmpty()) null else derby.password)
                    } catch (exception: SQLException) {
                        if (exception.errorCode != 45000 || "08006" != exception.sqlState) {
                            throw GradleException(errorStartingDatabase(), exception)
                        }
                    } finally {
                        try {
                            server!!.shutdown()
                        } catch (exception: Exception) {
                            throw GradleException(errorStoppingDatabase(), exception)
                        }
                    }

                    logger.info("Stopped embedded Derby database")
                }
            }
        }

        startDerby.finalizedBy(stopDerby)
        stopDerby.mustRunAfter(startDerby)
    }
}

plugins {
    war
}

apply<DerbyTestPlugin>()

configure<DerbyTestPluginExtension> {
    script.source = file("build/dbscripts/derby/createdb.sql")
}

tasks.register("readProperty") {
    inputs.file("")
}

tasks.named<Test>("test") {
    this.dependsOn(tasks.getByName("startDerby"))
    tasks.getByName("stopDerby").mustRunAfter(this)

    this.systemProperty("buildDir", buildDir.path)
}

tasks.named<ProcessResources>("processResources") {
    filesNotMatching(
            listOf(
                    "**/roller.properties",
                    "**/smileys.properties",
                    "**/*.vm",
                    "**/*.ftl",
                    "**/ApplicationResources*.properties",
                    "blacklist.txt",
                    "struts.xml"
            )
    ) {
        expand(project.properties)
    }

    exclude("sql/**")
}

val copySQLTemplateResources by tasks.registering(Copy::class) {
    from("$projectDir/src/main/resources/sql")
    into("$buildDir/dbscripts")
}

val generateSQLResources by tasks.registering {
    dependsOn(copySQLTemplateResources)
    doFirst {
        val rootPath = "$buildDir/dbscripts"
        val templateProperties = Properties()
        templateProperties.load(file("$rootPath/dbscripts.properties").reader())
        val databases = templateProperties.getProperty("databases").split(" ")
        val templates = templateProperties.getProperty("templates").split(" ")


        databases.filter { it.isNotBlank() }.forEach { database ->
            templates.filter { it.isNotBlank() }.forEach { template ->
                Velocity.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, rootPath)
//                Velocity.setProperty(VelocityEngine.RUNTIME_LOG_NAME, logger.name)
                Velocity.setProperty(RuntimeConstants.VM_PERM_ALLOW_INLINE_REPLACE_GLOBAL, true)

                Velocity.init()

                val dbProperties = Properties()
                dbProperties.load(file("$rootPath/$database.properties").inputStream())

                val context = VelocityContext()
                context.put("db", dbProperties)

                val t = Velocity.getTemplate("$template.vm")

                val writer = file("$rootPath/$database/$template.sql").writer(StandardCharsets.UTF_8)

                t.merge(context, writer, listOf("macros.vm"))

                writer.close()
            }
        }
    }
}

val copyGeneratedSQLResources by tasks.registering(Copy::class) {
    dependsOn(generateSQLResources)
    from("$buildDir/dbscripts").include("**/*.sql")
    into("$buildDir/resources/main/dbscripts")
}

tasks.named("classes") {
    dependsOn(copyGeneratedSQLResources)
}

tasks.named<ProcessResources>("processTestResources") {
    filesNotMatching(listOf("**/*.jpg", "**/*.txt", "**/webdefault.xml", "**/*.png")) {
        expand(project.properties)
    }
}

dependencies {
    implementation("org.apache.commons:commons-text:1.6")
    implementation("jakarta.json:jakarta.json-api:1.1.5")
    compile("javax.servlet:jstl:1.2")
    compile("com.sun.activation:javax.activation:1.2.0")
    compile("javax.xml.bind:jaxb-api:2.3.1")
    compile("org.eclipse.persistence:org.eclipse.persistence.jpa:2.7.4")
    compile("org.apache.velocity:velocity:1.7")
    implementation("org.apache.velocity:velocity-tools:2.0")
    compile("commons-collections:commons-collections:3.2.2")
    compile("org.ow2.asm:asm:7.1")
    compile("org.ow2.asm:asm-commons:7.1")
    compile("org.ow2.asm:asm-tree:7.1")
    compile("org.apache.struts:struts2-core:2.5.20")
    compile("org.apache.struts:struts2-spring-plugin:2.5.20")
    compile("org.apache.struts:struts2-convention-plugin:2.5.20")
    compile("org.apache.struts:struts2-tiles-plugin:2.5.20")
    compile("org.apache.lucene:lucene-analyzers-common:4.10.4")
    compile("org.apache.lucene:lucene-queryparser:4.10.4")
    compile("log4j:log4j:1.2.17")
    compile("org.apache.logging.log4j:log4j-core:2.10.0")
    compile("commons-validator:commons-validator:1.6")
    compile("commons-beanutils:commons-beanutils:1.9.3")
    compile("commons-httpclient:commons-httpclient:3.1")
    compile("commons-codec:commons-codec:1.12")
    compile("xml-security:xmlsec:1.3.0")
    compile("org.apache.xmlrpc:xmlrpc-common:3.1.3")
    compile("org.apache.xmlrpc:xmlrpc-client:3.1.3")
    compile("org.apache.xmlrpc:xmlrpc-server:3.1.3")
    compile("org.apache.ws.commons.util:ws-commons-util:1.0.2")
    compile("org.springframework:spring-web:4.1.4.RELEASE")
    compile("org.springframework:spring-context:4.1.4.RELEASE")
    compile("org.springframework.security:spring-security-config:3.2.5.RELEASE")
    compile("org.springframework.security:spring-security-ldap:3.2.5.RELEASE")
    compile("org.springframework.security:spring-security-openid:3.2.5.RELEASE")
    compile("org.springframework.security:spring-security-taglibs:3.2.5.RELEASE")
    compile("org.springframework.security:spring-security-acl:3.2.5.RELEASE")
    compile("com.google.inject:guice:4.2.2")
    compile("com.rometools:rome-fetcher:1.12.0")
    compile("com.rometools:rome-propono:1.12.0")
    compile("org.webjars:webjars-servlet-2.x:1.5")
    compile("org.webjars.npm:angular:1.7.8")
    compile("net.oauth.core:oauth-provider:20100527")

    compileOnly("javax.servlet:javax.servlet-api:4.0.1")
    compileOnly("javax.servlet.jsp:jsp-api:2.2")
    compileOnly("javax.mail:mail:1.4.7")

    runtime("org.slf4j:slf4j-log4j12:1.7.26")

    testCompile("junit:junit:4.12")
    testCompile("org.apache.ant:ant:1.10.5")
    testCompile("org.apache.derby:derbynet:10.11.1.1")
    testCompile("org.apache.derby:derbyclient:10.11.1.1")

    testImplementation("javax.servlet:javax.servlet-api:4.0.1")
    testImplementation("javax.servlet.jsp:jsp-api:2.2")
    testImplementation("javax.mail:mail:1.4.7")
}

description = "Roller webapp"
