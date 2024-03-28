package domain
import java.time.Instant
import io.circe.generic.semiauto.deriveCodec
import io.circe.Codec
final case class UserJWT(
    id: Long, // PK
    email: String,
    hashedPassword: String,
    ctime: Instant = Instant.now(),
    mtime: Instant = Instant.now()
)

object UserJWT {
  implicit val userjwtCodec: Codec.AsObject[UserJWT] = deriveCodec[UserJWT]
}
