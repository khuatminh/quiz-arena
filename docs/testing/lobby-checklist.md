# Lobby verification

Auth fields create only the specified typed payload. Confirm-password remains local and both password fields clear after submission. HELLO_ACK enables authentication; pending auth disables both submit controls until response/error/timeout.

Quiz/topic lists and online users come from server events. Challenge requires an AVAILABLE quiz, a different FREE opponent and no pending submission. Incoming invitation offers accept/reject; outgoing offers cancel. TTL display does not close the invitation locally. Only server MATCH_START opens the game.

The actual server test registered two generated accounts, logged each in, fetched a nonempty quiz list and logged out (`evidence/client-live.log`). Native fixture lobby captures show the quiz card and opponent. Interactive challenge accept/reject/expiry is covered by server integration tests and remains a manual client click-through check.
