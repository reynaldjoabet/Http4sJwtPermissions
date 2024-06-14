package configs

final case class JWTConfig(
  secret: String,
  ttl: Long
)

object JWTConfig {}
