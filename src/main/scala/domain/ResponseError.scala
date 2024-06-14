package domain

import scala.util.control.NoStackTrace

sealed abstract class ResponseError {
  override def toString(): String = "Insufficient Scope"
}

object ResponseError {

  case class UserNotFound(username: String)    extends NoStackTrace
  case class UserNameInUse(username: String)   extends NoStackTrace
  case class InvalidPassword(username: String) extends NoStackTrace
  case object UnsupportedOperation             extends NoStackTrace

  case object TokenNotFound extends NoStackTrace

//case object UnauthorizedResponse extends ResponseError
//401 unauthorized
// do not include any other info like scopes
  case object MissingAccessTokenResponse extends ResponseError

  // client calls protected api with a well formed request, but no valid credentials
  // - could be missing an access token, or the pressent access token is malformed, revoked, invalid etc
  // /** @throws AlgorithmMismatchException
  //   *   if the algorithm stated in the token's header is not equal to the one
  //   *   defined in the {@@@@@@@@@linkJWTVerifier} .
  //   * @throws SignatureVerificationException
  //   *   if the signature is invalid.
  //   * @throws TokenExpiredException
  //   *   if the token has expired.
  //   * @throws MissingClaimException
  //   *   if a claim to be verified is missing.
  //   * @throws IncorrectClaimException
  //   *   if a claim contained a different value than the expected one.
  //   */
  // 401 unauthorized
  // A server generating a 401 (Unauthorized) response MUST send a
  // WWW-Authenticate header field containing at least one challenge

  case class UnauthorizedResponse(scopes: Set[String]) extends ResponseError

  // request with valid access token but does  not include any permissions or scopes that allows the client to perform the desired action

  case object ForbiddenResponse extends ResponseError

  // case object EmptyStringResponse extends ResponseError
}

//404 good for example a user trying to access the details of another user... they should get 404, they do not need to know about the existing of the data

// some sites return a 404 (not found) error instead of a 403 error when a user is not authorized to access (or perform an operation on) a resource.
// The primary purpose of doing so is to hide the existence of the resource from the user instead of letting the user know the resource exists but they are unable to perform the requested operation.
// This practice is called error encapsulation, and we can see it implemented with 5xx errors when a 500 error is replaced with a 503 error to avoid informing potential attackers that an internal server error occurred
// UnauthorizedError,
//  InvalidRequestError,
//  InvalidTokenError,
//  InsufficientScopeError
