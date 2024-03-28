package domain

final case class User(id: Long, email: String, permissions: Set[String])
