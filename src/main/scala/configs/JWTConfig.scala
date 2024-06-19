package configs

final case class JwtConfig(
  secret: String,
  ttl: Long
)

object JwtConfig {}
