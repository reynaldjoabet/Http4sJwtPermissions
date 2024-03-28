# Http4sJwtPermissions


This project is an http4s middleware much `express-jwt-authz` and `` used to authorize access to an endpoint
`express-jwt` is used together with `express-jwt-authz` to both validate a jwt and make sure it has the correct permissions to call the endpoint. After successfully validating a jwt, `express-jwt` adds a user object to the request passes it to the next action. 

using ``http4sJwtPermissions`, we still need to have validated the jwt before this middleware. Here we also validate the jwt twice as there is no easy way to pass more info into the request


## Usage
```scala
 class TestRoutes(jwtService: JWTService[IO]) extends Http4sDsl[IO] {
    val routes = AuthedRoutes.of[User, IO] {
      case req -> Root / "hello" as user => Ok()
    }

     val allRoutes: HttpRoutes[IO] = CheckPermissionsMiddleware(
      jwtService,
      Set("read:user", "write:user", "delete:user")
    ).apply(routes)
  }

  val jwtService = JWTServiceLive.make[IO](jwtConfig, clock)

  val routes: HttpRoutes[IO] = new TestRoutes(
    jwtService
  ).allRoutes

```



## Test
run `sbt test` or `sbt --client test` on the terminal to run the tests

### Http 
some sites return a 404 (not found) error instead of a 403 error when a user is not authorized to access (or perform an operation on) a resource. 
The primary purpose of doing so is to hide the existence of the resource from the user instead of letting the user know the resource exists but they are unable to perform the requested operation. 
This practice is called error encapsulation, and we can see it implemented with 5xx errors when a 500 error is replaced with a 503 error to avoid informing potential attackers that an internal server error occurred

use 404 instead of 403

Letting the client (and especially the user behind it) know that resource exists could possibly lead to Insecure Direct Object References (IDOR), an access control vulnerability based on the knowledge of resources you shouldn't access. Therefore, in these cases, your API should respond with a 404 Not Found status code

An origin server that wishes to "hide" the current existence of a forbidden target resource MAY instead respond with a status code of 404 (Not Found).

Response(status=401, httpVersion=HTTP/1.1, headers=Headers(Content-Length: 0, WWW-Authenticate: Bearer scopes= realm="read:user write:user delete:user"))



Privileges define actions which can be performed against specific functionality. Privileges can be only be assigned to roles.
i.e. `nexus:blobstores:create,read` means allow creating and reading blobstores

Actions are functions allowing an explicit behavior the privilege can perform with the associated function.
You must assign one or more actions when creating new privileges

```bash
#Consider how each action behaves when applied to a privilege type:

add

#This action allows privileges to add repository content or scripts.

browse

#This action allows privileges to view the contents of associated repositories. Unlikeread, privilege types withbrowse can only view and administrate repository contents from UI.

create

#This action allows privileges to create applicable configurations within the repository manager. Since a read permission is required to view a configuration, this action is associated with most existing create privileges.

delete

#This action allows privileges to delete repository manager configurations, repository contents, and scripts. A read action is generally associated with delete actions, so the actor can view these configurations to remove them.

edit

#This action sllows privileges to modify scripts, repository content and settings.

read

#This action allows privileges to view various configuration lists and scripts. Withoutread, any associated action will permit a privilege to see these lists but not its contents. The read action also allows privileges to utilize tools that can look at content from the command line.

update

#This action allows privileges to update repository manager configurations. Most existing privileges with update include read actions. Therefore, if creating custom privileges with update, the actor should consider adding read to the privilege in order to view repository manager configuration updates.

*

#This action is a wildcard giving you the ability to group all actions together. Using a wildcard applies all other applicable actions to the privilege.




```

others(GitHub) see permissions as actions


- Roles represent a collection of permissions or privileges that define what actions a user can perform.
- Users are assigned one or more roles, and their access rights are determined based on the permissions associated with those roles


A permission is the declaration of an action that can be executed on a resource.
Permissions are bound to a resource
 
Privileges are assigned permissions
When you assign a permission to a user, you are granting them a privilege. If you assign a user the permission to read a document, you are granting them the privilege to read that document
Resources expose permissions, users have privileges( privileges can also be assigned to applications)

The entity that protects the resource is responsible for restricting access to it, i.e., it is doing access control

Scopes enable a mechanism to define what an application can do on behalf of the user
(delegated access)
Typically, scopes are permissions of a resource that the application wants to exercise on behalf of the user.

Usually, the scopes granted to a third-party application are a subset of the permissions granted to the user
when the application excercises its scopes, the user must have the corresponding priviledge at that time. The application can't do more than the user can do

Scopes only come into play in delegation scenarios, and always limit what an app can do on behalf of a user: a scope cannot allow an application to do more than what the user can do. They are not meant to grant the application permissions outside of the privileges the delegated user already possesses

In auth0, a role is a collection of permissions

Scopes are not required when requesting an access token for an API configured with RBAC.Only the audience must be passed to Auth0


`create:items`: Create menu items

`update:items`: Update menu items

`delete:items`: Delete menu items

`action:resource` is the format for permissions
You need to associate the permissions you've created with this role, mapping it to your API's resources
Through its permissions claim, the access token tells the server which actions the client can perform on which resources.

A scope is a term used by the OAuth 2.0 protocol to define limitations on the amount of access that you can grant to an access token. In essence, permissions define the scope of an access token.

[role-based-authentication-](https://thecibrax.com/role-based-authentication-for-net-core-apis-with-auth0)
[Permissions, Privileges and Scopes](https://www.youtube.com/watch?v=vULfBEn8N7E)
[Permissions, Privileges and Scopes](https://auth0.com/blog/permissions-privileges-and-scopes/)
### In database
A privilege is a permission to perform an action or a task
Privileges may be granted to individual users, to groups, or to PUBLIC. PUBLIC is a special group that consists of all users, including future users. Users that are members of a group will indirectly take advantage of the privileges granted to the group, where groups are supported.

sessioncookie

![alt text](image.png)


the order in which the middleware components are invoked on requests and the reverse order for the response

CORS before authentication, before authorization


RBAC is a model of access control in which access is granted or denied based upon the roles assigned to a user. Permissions are not directly assigned to an entity; rather, permissions are associated with a role and the entity inherits the permissions of any roles assigned to it. Generally, the relationship between roles and users can be many-to-many, and roles may be hierarchical in nature.


A role is simply a way to group permissions so that they can be assigned to users
When a user is assigned to a role, the user will be granted all the permissions that the role has.

A permission specifies an action that a user can take on a resource. For example, we might say that a user in an organization has permission to read repositories.

![alt text](image-1.png)


Access Control (or Authorization) is the process of granting or denying specific requests from a user, program, or process. Access control also involves the act of granting and revoking those privileges.

Role-Based Access Control (RBAC) is the primary authorization mechanism in Kubernetes and is responsible for permissions over resources. These permissions combine verbs (get, create, delete, etc.) with resources (pods, services, nodes, etc.) and can be namespace or cluster scoped

```bash
addCommandAlias("cleanCompile", "clean; compile;")
addCommandAlias("cleanTest", "clean; test;")
addCommandAlias("testWithCoverage", "clean; coverageOn; test; coverageAggregate; coverageOff; viewCoverageResults;")
```