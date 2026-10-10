# Security

## Reporting a problem
Please report security problems **privately**: on the repository page open the **Security** tab and choose
**Report a vulnerability** (GitHub's private reporting). If that button is not there, open an ordinary issue that says
only "I would like to report a security problem" and no details, and a private channel will be arranged.

Please do not post API keys, server addresses or logs from your own server in public issues.

## What the app handles
- It stores your Emby server address, an Emby **administrator** API key and the Watching Eye plugin key in the phone's
  encrypted preferences. Treat the Emby key like a password: anyone who has it controls your server.
- Keys are sent only in request headers (never in URLs) and only to the server address you entered.
- Release builds only talk to `https://` addresses.

## Supported versions
Only the latest release receives fixes.
