package domain

case class UserWithScopes(
    id: Long,
    username: String,
    roles: Set[String],
    scopes: Set[String]
)
