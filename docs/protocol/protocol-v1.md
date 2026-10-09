# Protocol v1

UTF-8 JSON with a four-byte signed big-endian byte length (1–65536). Partial frames expire after ten seconds; EOF inside a frame is invalid. Envelope protocolVersion is 1, IDs are canonical UUID strings. Client requests require requestId and cannot set eventSeq. Server match events use increasing eventSeq. Payload fields are strictly typed; unknown fields, duplicate keys, coercions, trailing tokens, malformed UTF-8 and nesting beyond 100 are rejected.

PublicQuestion never contains answer keys or explanation. Reveal and review DTOs own those fields. Authentication fields must never be logged. Optional nullable fields are represented explicitly as null on encoding.

| Message | Payload record | Direction | Handler owner | Fixture |
|---|---|---|---|---|
| HELLO | Payloads.Hello | C → S | Network | catalogue-v1.json:HELLO |
| HELLO_ACK | Payloads.HelloAck | S → C | Network | catalogue-v1.json:HELLO_ACK |
| PING | Payloads.Ping | C → S | Network | catalogue-v1.json:PING |
| PONG | Payloads.Pong | S → C | Network | catalogue-v1.json:PONG |
| REGISTER | Payloads.Register | C → S | Auth/query | catalogue-v1.json:REGISTER |
| REGISTER_RESULT | Payloads.RegisterResult | S → C | Auth/query | catalogue-v1.json:REGISTER_RESULT |
| LOGIN | Payloads.Login | C → S | Auth/query | catalogue-v1.json:LOGIN |
| LOGIN_RESULT | Payloads.LoginResult | S → C | Auth/query | catalogue-v1.json:LOGIN_RESULT |
| LOGOUT | Payloads.Logout | C → S | Network | catalogue-v1.json:LOGOUT |
| LOGOUT_ACK | Payloads.LogoutAck | S → C | Network | catalogue-v1.json:LOGOUT_ACK |
| ONLINE_LIST_REQUEST | Payloads.OnlineListRequest | C → S | Network | catalogue-v1.json:ONLINE_LIST_REQUEST |
| ONLINE_LIST | Payloads.OnlineList | S → C | Network | catalogue-v1.json:ONLINE_LIST |
| QUIZ_LIST_REQUEST | Payloads.QuizListRequest | C → S | Auth/query | catalogue-v1.json:QUIZ_LIST_REQUEST |
| QUIZ_LIST | Payloads.QuizList | S → C | Auth/query | catalogue-v1.json:QUIZ_LIST |
| QUIZ_DETAIL_REQUEST | Payloads.QuizDetailRequest | C → S | Auth/query | catalogue-v1.json:QUIZ_DETAIL_REQUEST |
| QUIZ_DETAIL | Payloads.QuizDetail | S → C | Auth/query | catalogue-v1.json:QUIZ_DETAIL |
| CHALLENGE | Payloads.Challenge | C → S | Network | catalogue-v1.json:CHALLENGE |
| CHALLENGE_ACK | Payloads.ChallengeAck | S → C | Network | catalogue-v1.json:CHALLENGE_ACK |
| CHALLENGE_RECEIVED | Payloads.ChallengeReceived | S → C | Network | catalogue-v1.json:CHALLENGE_RECEIVED |
| CHALLENGE_ACCEPT | Payloads.ChallengeAccept | C → S | Network | catalogue-v1.json:CHALLENGE_ACCEPT |
| CHALLENGE_REJECT | Payloads.ChallengeReject | C → S | Network | catalogue-v1.json:CHALLENGE_REJECT |
| CHALLENGE_CANCEL | Payloads.ChallengeCancel | C → S | Network | catalogue-v1.json:CHALLENGE_CANCEL |
| CHALLENGE_CLOSED | Payloads.ChallengeClosed | S → C | Network | catalogue-v1.json:CHALLENGE_CLOSED |
| MATCH_START | Payloads.MatchStart | S → C | Match | catalogue-v1.json:MATCH_START |
| MATCH_READY | Payloads.MatchReady | C → S | Match | catalogue-v1.json:MATCH_READY |
| READY_STATUS | Payloads.ReadyStatus | S → C | Match | catalogue-v1.json:READY_STATUS |
| MATCH_COUNTDOWN | Payloads.MatchCountdown | S → C | Match | catalogue-v1.json:MATCH_COUNTDOWN |
| QUESTION | Payloads.Question | S → C | Match | catalogue-v1.json:QUESTION |
| QUESTION_READY | Payloads.QuestionReady | C → S | Match | catalogue-v1.json:QUESTION_READY |
| ROUND_COUNTDOWN | Payloads.RoundCountdown | S → C | Match | catalogue-v1.json:ROUND_COUNTDOWN |
| QUESTION_OPEN | Payloads.QuestionOpen | S → C | Match | catalogue-v1.json:QUESTION_OPEN |
| ANSWER | Payloads.Answer | C → S | Match | catalogue-v1.json:ANSWER |
| ANSWER_ACK | Payloads.AnswerAck | S → C | Match | catalogue-v1.json:ANSWER_ACK |
| ANSWER_STATUS | Payloads.AnswerStatus | S → C | Match | catalogue-v1.json:ANSWER_STATUS |
| QUESTION_RESULT | Payloads.QuestionResult | S → C | Match | catalogue-v1.json:QUESTION_RESULT |
| ROUND_LEADERBOARD | Payloads.RoundLeaderboard | S → C | Match | catalogue-v1.json:ROUND_LEADERBOARD |
| CHAT | Payloads.Chat | C → S | Match | catalogue-v1.json:CHAT |
| CHAT_MESSAGE | Payloads.ChatMessage | S → C | Match | catalogue-v1.json:CHAT_MESSAGE |
| MATCH_RESULT | Payloads.MatchResult | S → C | Match | catalogue-v1.json:MATCH_RESULT |
| MATCH_SAVE_STATUS | Payloads.MatchSaveStatus | S → C | Match | catalogue-v1.json:MATCH_SAVE_STATUS |
| REMATCH_REQUEST | Payloads.RematchRequest | C → S | Match | catalogue-v1.json:REMATCH_REQUEST |
| REMATCH_RESPONSE | Payloads.RematchResponse | C → S | Match | catalogue-v1.json:REMATCH_RESPONSE |
| REMATCH_STATUS | Payloads.RematchStatus | S → C | Match | catalogue-v1.json:REMATCH_STATUS |
| EXIT_MATCH | Payloads.ExitMatch | C → S | Match | catalogue-v1.json:EXIT_MATCH |
| EXIT_ACK | Payloads.ExitAck | S → C | Match | catalogue-v1.json:EXIT_ACK |
| RESULT_SESSION_CLOSED | Payloads.ResultSessionClosed | S → C | Match | catalogue-v1.json:RESULT_SESSION_CLOSED |
| MATCH_SNAPSHOT_REQUEST | Payloads.MatchSnapshotRequest | C → S | Match | catalogue-v1.json:MATCH_SNAPSHOT_REQUEST |
| MATCH_SNAPSHOT | Payloads.MatchSnapshot | S → C | Match | catalogue-v1.json:MATCH_SNAPSHOT |
| RANKING_REQUEST | Payloads.RankingRequest | C → S | Auth/query | catalogue-v1.json:RANKING_REQUEST |
| RANKING | Payloads.Ranking | S → C | Auth/query | catalogue-v1.json:RANKING |
| RANKING_INVALIDATED | Payloads.RankingInvalidated | S → C | Auth/query | catalogue-v1.json:RANKING_INVALIDATED |
| PROFILE_REQUEST | Payloads.ProfileRequest | C → S | Auth/query | catalogue-v1.json:PROFILE_REQUEST |
| PROFILE | Payloads.Profile | S → C | Auth/query | catalogue-v1.json:PROFILE |
| HISTORY_REQUEST | Payloads.HistoryRequest | C → S | Auth/query | catalogue-v1.json:HISTORY_REQUEST |
| HISTORY | Payloads.History | S → C | Auth/query | catalogue-v1.json:HISTORY |
| MATCH_DETAIL_REQUEST | Payloads.MatchDetailRequest | C → S | Auth/query | catalogue-v1.json:MATCH_DETAIL_REQUEST |
| MATCH_DETAIL | Payloads.MatchDetail | S → C | Auth/query | catalogue-v1.json:MATCH_DETAIL |
| ERROR | Payloads.Error | S → C | Network | catalogue-v1.json:ERROR |
