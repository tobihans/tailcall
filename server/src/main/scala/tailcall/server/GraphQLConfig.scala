package tailcall.server

import tailcall.registry.SchemaRegistry
import zio.cli.{CliApp, Command, Options}
import zio.{Duration, ZIO, ZIOAppArgs}

case class GraphQLConfig(
  adminPort: Int = SchemaRegistry.PORT + 1,
  port: Int = SchemaRegistry.PORT,
  globalResponseTimeout: Int = 10000,
  httpCacheSize: Option[Int] = None,
  enableTracing: Boolean = false,
  slowQueryDuration: Option[Int] = None,
  database: Option[GraphQLConfig.DBConfig] = None,
  persistedQueries: Boolean = false,
  allowedHeaders: Set[String] = Set("cookie", "authorization"),
)

object GraphQLConfig {
  val default: GraphQLConfig = GraphQLConfig()

  def bootstrap[R, E, A](run: GraphQLConfig => ZIO[R, E, A]): ZIO[R with ZIOAppArgs, Any, Any] =
    ZIOAppArgs.getArgs
      .flatMap(args => CliApp.make("tailcall", "0.0.1", command.helpDoc.getSpan, command)(run(_)).run(args.toList))

  private def command: Command[GraphQLConfig] =
    Command("server", options).withHelp(s"starts the server on port: ${default.port}").map {
      case (
            adminPort,
            port,
            globalResponseTimeout,
            httpCacheSize,
            enableTracing,
            slowQueryDuration,
            database,
            persistedQueries,
            allowedHeaders,
          ) => GraphQLConfig(
          adminPort,
          port,
          globalResponseTimeout,
          httpCacheSize,
          enableTracing,
          slowQueryDuration,
          database,
          persistedQueries,
          allowedHeaders,
        )
    }

  private def options =
    CustomOptions.int("admin-port").withDefault(default.adminPort) ?? "port on which the admin APIs are exposed" ++
      CustomOptions.int("port").withDefault(default.port) ?? "port on which the public APIs are exposed" ++
      CustomOptions.int("timeout").withDefault(default.globalResponseTimeout) ?? "global timeout in millis" ++
      CustomOptions.int("http-cache").optional
        .withDefault(default.httpCacheSize) ?? "size of the in-memory http cache" ++
      Options.boolean("tracing")
        .withDefault(default.enableTracing) ?? "enables low-level tracing (affects performance)" ++
      CustomOptions.int("slow-query").optional
        .withDefault(default.slowQueryDuration) ?? "slow-query identifier in millis" ++
      DBConfig.options ++
      Options.boolean("persisted-queries").withDefault(default.persistedQueries) ?? "enable persisted-queries" ++
      Options.text("allowed-headers").map(_.split(",").map(_.trim().toLowerCase()).toSet)
        .withDefault(default.allowedHeaders) ?? "comma separated list of headers"

  final case class DBConfig(host: String, port: Int, username: Option[String], password: Option[String])
  object DBConfig {
    val options: Options[Option[DBConfig]] = {
      Options.boolean("db").withDefault(false) ?? "enable database for persistence" ++
        Options.text("db-host").withDefault("localhost") ?? "database hostname" ++
        CustomOptions.int("db-port").withDefault(3306) ?? "database port" ++
        Options.text("db-username").withDefault("tailcall_main_user").optional ?? "database username" ++
        Options.text("db-password").withDefault("tailcall").optional ?? "database password"
    }.map { case (enable, host, port, username, password) =>
      if (enable) Some(DBConfig(host, port, username, password)) else None
    }
  }

  private object CustomOptions {
    def duration(name: String): Options[Duration] = Options.integer(name).map(b => Duration.fromMillis(b.toLong))
    def int(name: String): Options[Int]           = Options.integer(name).map(_.toInt)
  }
}
