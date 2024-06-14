package domain

// Define some roles
sealed trait Role

object Role {

  case object Admin     extends Role
  case object Moderator extends Role
  case object User      extends Role

}
