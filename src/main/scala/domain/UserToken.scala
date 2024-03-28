package domain

case class UserToken(
    email: String,
    token: String,
    expires: Long
)
