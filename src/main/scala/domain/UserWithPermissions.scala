package domain

case class UserWithPermissions(
    id: Long,
    username: String,
    permissions: Set[String]
)
object UserWithPermissions {}
