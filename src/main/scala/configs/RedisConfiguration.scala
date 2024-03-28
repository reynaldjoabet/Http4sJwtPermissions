package configs

object RedisConfiguration {}
case class RedisConfiguration(
    host: String,
    port: Int,
    password: String,
    database: Int
)
