# Privacy

This launcher does not send usage telemetry, query a public-IP geolocation service, or automatically update itself. It contacts the services needed for actions you request, such as account sign-in, game installation, skin loading and addon browsing.

Microsoft account authentication sends credentials and tokens only to the corresponding official Microsoft, Xbox and Minecraft endpoints. Download mirrors are not authentication endpoints.

Account sessions are stored locally in HMCL's account files so you can sign in again. Treat these files as private. Deleting a Microsoft account removes its local account records through the normal HMCL controls.

Ownership verification is performed online for each launch. The resulting launch authorization is short-lived, held only in memory, and usable for one game process. It is not exported in launch scripts or written as a reusable ownership flag.

Local diagnostic logs can include request addresses, status codes, account or character identifiers and exception details. The authentication diagnostics omit request bodies, authorization headers, passwords, device codes and token values. Inspect logs before sharing them. Release packages exclude local logs, account files and game installations.

The static project website uses GitHub Pages and has no analytics scripts, login forms or browser storage. GitHub processes ordinary hosting request data under its own privacy policy.
