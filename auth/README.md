# Auth Module

`auth` is currently kept as an authorization modeling module and future Permify integration base.

Kept intentionally:

- `auth/src/main/java/xyz/jasenon/lab/auth/permission/`: Java-side permission/action/relation name design.
- `auth/schema/`: Permify DSL models used as authorization schema guidance.
- `co.permify:permify-java`: official Permify Java SDK dependency for future client abstraction.

Not implemented here now:

- controller annotations
- AOP aspects
- concrete client wrappers
- relation grant/revoke services
- check enforcement
- Dubbo RPC service contracts

The next implementation can define explicit Permify operations around:

- `grant`
- `revoke`
- `check`

and expose them through Dubbo services according to the final auth-service boundary.
