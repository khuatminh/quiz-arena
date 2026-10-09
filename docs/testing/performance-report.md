# Local performance report

Measured on2026-10-09, Asia/Ho_Chi_Minh, Apple M4/arm64, macOS27.0, JDK21.0.9, Maven3.9.11 and isolated MySQL8.4.7. The shaded production server listened on loopback port15555. Four independent TCP clients used two concurrent matches. No network or DB service was mocked for this probe.

Command (public demo password supplied via QUIZ_DEMO_PASSWORD):

```sh
java -cp 'server/target/test-classes:server/target/quiz-arena-server.jar' \
 vn.edu.nhom7.quiz.server.system.LoadProbe \
 --host=127.0.0.1 --port=15555 --connections=4 --matches=2 \
 --samples=100 --csv=docs/testing/evidence/latency.csv
```

Server environment `QUIZ_TELEMETRY_CSV=docs/testing/evidence/timer-dispatch.csv` exports bounded monotonic scheduler samples on graceful shutdown. The measured jar SHA256 was `8981bebae7abf772eac79b3f66239ef9885a08b62c1588fbcc72503f5d29333b` (subsequent source-only cleanup/rebuild may produce another jar hash).

p95 uses sorted index `ceil(0.95*n)-1` without interpolation. Client latency starts immediately before writing a request and ends at the matching response, so it includes real socket writes and server service time. Chat measures sender echo, not a separate remote display/render. Login warmup uses repeated complete login/logout batches until100 samples; it is not100 simultaneous users. Client CSV connection/match columns are configured targets; scheduler CSV records observed counts at dispatch.

| Metric | Samples | p95 ms | Plan target | Local result |
| --- | ---: | ---: | --- | --- |
| LOGIN_RESULT | 100 | 58.965 | login <3000ms | Within local target |
| ONLINE_LIST | 100 | 2.297 | online <500ms | Within local target |
| CHAT_MESSAGE | 100 | 3.524 | chat <300ms | Within local target |
| ANSWER_ACK | 100 | 3.341 | ACK <300ms | Within local target |
| TIMER_DISPATCH | 164 | 10.113 | timer <100ms | Within local target |

Raw evidence: [client latency CSV](evidence/latency.csv), [server timer CSV](evidence/timer-dispatch.csv), [probe output](evidence/load-probe.log). Timer delay measures expected scheduler deadline→callback dispatch, not client receive time. The server finished shutdown and wrote telemetry after SIGTERM; it reported no unsaved pending summaries.

Physical two-machine LAN RTT<50ms,32-client/16-match stress, CPU profiling, high-DPI UI interaction and100 save-duration samples were **NOT_RUN**. Save correctness/recovery was tested via real-DB integration; a100-sample save-latency claim is not made. The measurements here cannot establish performance on another host or LAN.
