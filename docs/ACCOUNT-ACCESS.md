# Account access

## Microsoft identity

Add a Microsoft account through HMCL's existing Microsoft login page. Passwords, MFA and consent are completed directly in Microsoft's browser pages. The desktop application uses its public client ID, PKCE and state validation for the authorization-code flow; device authorization is also supported. No client secret is distributed.

Before each game launch, HMCL refreshes expired credentials as needed, requests the current Java Edition license from the official Minecraft Services entitlement endpoint, and fetches the profile using the same Minecraft bearer token. The profile UUID must match the selected Microsoft account. HTTP 200 without a `game_minecraft` entitlement does not qualify.

Launch stops if authentication, entitlement verification or profile verification fails. An earlier success, offline setting or legacy local account does not bypass these checks. Script export does not create reusable game credentials. The process boundary checks a short-lived, single-use in-memory authorization.

## Supported accounts

This distribution supports Microsoft accounts only. Its normal account page and login dialog use the existing HMCL interface. Local profile metadata does not grant access to Minecraft.

## Availability

The application's ability to use Minecraft Services is controlled by Microsoft and Mojang. An issued device code, a completed Microsoft consent screen, or successful test fixtures do not establish that the registered application has Minecraft API access. When access is refused, the launcher blocks game launch. It does not borrow another application's ID or use an authentication proxy.
