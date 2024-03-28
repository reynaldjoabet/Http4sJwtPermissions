package domain

final case class AuthzOptions(
    failWithError: Option[Boolean],
    customScopeKey: Option[Boolean],
    customUserKey: Option[Boolean],
    checkAllScopes: Option[Boolean]
)
