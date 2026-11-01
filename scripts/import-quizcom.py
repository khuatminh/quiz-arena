#!/usr/bin/env python3
"""Archive Quiz.com public data and import reviewed questions via Quiz Arena's API.

No third-party dependencies. All questions default to drafts; a review file must
explicitly identify slides that are playable without the source media.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import socket
import ssl
import struct
import time
import urllib.request
import uuid


ROOT = Path(__file__).resolve().parents[1]


def save_json(path, value, private=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        json.dump(value, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    os.replace(temporary, path)
    if private:
        path.chmod(0o600)


class Client:
    def __init__(self, host, port):
        self.sock = socket.create_connection((host, port), timeout=15)
        self.sock.settimeout(15)
        self.last_request = 0
        self.request("HELLO", {"clientVersion": "3.0", "assetPackVersion": "1"}, "HELLO_ACK")

    def exact(self, count):
        data = b""
        while len(data) < count:
            chunk = self.sock.recv(count - len(data))
            if not chunk:
                raise RuntimeError("Server disconnected")
            data += chunk
        return data

    def request(self, kind, payload, expected):
        # The server allows ten queries per second per connection.
        time.sleep(max(0, 0.15 - (time.monotonic() - self.last_request)))
        self.last_request = time.monotonic()
        request_id = str(uuid.uuid4())
        raw = json.dumps(dict(protocolVersion=1, type=kind, requestId=request_id,
                              matchId=None, roundId=None, eventSeq=None, payload=payload),
                         ensure_ascii=False).encode("utf-8")
        if len(raw) > 65536:
            raise ValueError("Request exceeds protocol frame size")
        self.sock.sendall(struct.pack("!I", len(raw)) + raw)
        for _ in range(200):
            size = struct.unpack("!I", self.exact(4))[0]
            if not 0 < size <= 65536:
                raise RuntimeError("Invalid server frame")
            message = json.loads(self.exact(size))
            if message.get("requestId") != request_id:
                continue
            if message["type"] == "ERROR":
                raise RuntimeError(f"{kind}: {message['payload']}")
            if message["type"] != expected:
                raise RuntimeError(f"Unexpected response: {message['type']}")
            return message["payload"]
        raise RuntimeError("No correlated response")

    def author(self, action, quiz_id=0, data=None):
        return self.request("AUTHOR_REQUEST", dict(action=action, quizId=quiz_id,
                            data=data or {}), "AUTHOR_RESULT")["data"]


def convert(slide, playable):
    answers = slide.get("answers", [])
    correct = [a["text"].strip() for a in answers if a.get("isCorrect")]
    if not correct:
        raise ValueError(f"No source answer for slide {slide['index'] + 1}")
    options = []
    if slide["type"] == "Classic":
        # The source places its correct choice first. Shuffle deterministically
        # while keeping IDs and answer keys attached to the original choices.
        options = [dict(id=f"option-{i + 1}", text=a["text"].strip())
                   for i, a in enumerate(answers)]
        keys = [o["id"] for o, a in zip(options, answers) if a.get("isCorrect")]
        options.sort(key=lambda o: hashlib.sha256((slide["id"] + o["id"]).encode()).hexdigest())
        kind, key = ("SINGLE_CHOICE", keys[0]) if len(keys) == 1 else ("MULTIPLE_CHOICE", keys)
    elif slide["type"] == "TypeAnswer":
        kind, key = "SHORT_ANSWER", list(dict.fromkeys(correct))
    elif slide["type"] == "Range":
        if len(answers) != 3 or answers[2]["text"] != "exactly":
            raise ValueError("Unsupported source range scoring; review manually")
        minimum, maximum = map(int, answers[1]["text"].split(","))
        target = int(correct[0])
        kind, key = "RANGE", dict(min=minimum, max=maximum, step=1, unit="",
                                  acceptedMin=target, acceptedMax=target)
    else:
        raise ValueError(f"Unsupported slide type: {slide['type']}")
    media = slide.get("media")
    video_url = None
    if media and media.get("source", "").startswith("youtube/"):
        video_url = "https://www.youtube.com/watch?v=" + media["source"].split("/", 1)[1]
        start = (media.get("trim") or {}).get("start") or 0
        video_url += "&t=" + str(int(start)) + "s"
    explanation = slide.get("funFact") or ""
    if video_url:
        explanation += ("\n" if explanation else "") + "Video gốc: " + video_url
        trim = media.get("trim") or {}
        explanation += f" (bắt đầu {trim.get('start') or 0}s; độ dài {trim.get('duration')}s)"
    content = slide["question"].strip()
    if not playable:
        content += "\n[Cần video gốc để trả lời]"
        if video_url:
            content += "\n" + video_url
    question = dict(questionType=kind, content=content, options=options, answerKey=key,
                    explanation=explanation or None, questionAssetId=None, explanationAssetId=None)
    if len(content) > 500 or len(explanation) > 500:
        raise ValueError("Question exceeds application text limit")
    return dict(sourceSlideId=slide["id"], sourceSlideNumber=slide["index"] + 1,
                sourceType=slide["type"], sourceMedia=media,
                sourceFunFactMedia=slide.get("funFactMedia"), playable=playable, question=question)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("url")
    parser.add_argument("--review", type=Path, required=True)
    parser.add_argument("--host")
    parser.add_argument("--port", type=int, default=5555)
    parser.add_argument("--category-id", type=int)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    match = re.fullmatch(r"https://quiz\.com/([0-9a-f-]{36})/?", args.url)
    if not match:
        parser.error("Expected a Quiz.com UUID URL")
    source_id = str(uuid.UUID(match[1]))
    folder = ROOT / "data" / "quizcom" / source_id
    review = json.loads(args.review.read_text(encoding="utf-8"))
    if review["sourceId"] != source_id:
        raise ValueError("Review belongs to another quiz")
    raw_path = folder / "source.json"
    if not raw_path.exists():
        request = urllib.request.Request(f"https://quiz.com/api/quiz/public/{source_id}/",
                                         headers={"User-Agent": "QuizArenaImporter/1.0"})
        context = ssl.create_default_context()
        # python.org macOS installations may lack a populated Python CA bundle.
        if Path("/etc/ssl/cert.pem").is_file():
            context.load_verify_locations("/etc/ssl/cert.pem")
        with urllib.request.urlopen(request, timeout=30, context=context) as response:
            save_json(raw_path, json.load(response))
    source = json.loads(raw_path.read_text(encoding="utf-8"))
    if source["id"] != source_id or len(source["slides"]) != source["slideCount"]:
        raise ValueError("Incomplete or mismatched source data")
    publish_numbers = set(review["publishSlideNumbers"])
    questions, info = [], []
    for slide in sorted(source["slides"], key=lambda s: s["index"]):
        if slide["type"] == "InfoSlide":
            info.append(slide["index"] + 1)
        else:
            questions.append(convert(slide, slide["index"] + 1 in publish_numbers))
    if publish_numbers != {q["sourceSlideNumber"] for q in questions if q["playable"]}:
        raise ValueError("Review names missing or non-question slides")
    manifest = dict(sourceUrl=args.url, sourceId=source_id, title=source["name"],
                    author=source["owner"]["name"], skippedInfoSlideNumbers=info, questions=questions)
    save_json(folder / "questions.json", manifest)
    print(f"Archived {len(source['slides'])} slides: {len(questions)} questions, {len(info)} info slides.", flush=True)
    if not args.apply:
        return
    if not args.host or not args.category_id:
        parser.error("--apply requires --host and --category-id")
    digest = hashlib.sha256(json.dumps(manifest, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
    state_path = folder / f"import-{args.host}-{args.port}.json"
    state = json.loads(state_path.read_text()) if state_path.exists() else dict(
        host=args.host, port=args.port, sourceId=source_id, manifestSha256=digest, groups={})
    if state["manifestSha256"] != digest:
        raise ValueError("Source/review changed since import; inspect existing state before updating")
    # Saved outside version control; credentials never enter the public report.
    credentials_path = ROOT / "data" / "backups" / "quizcom-accounts" / f"{args.host}-{args.port}.json"
    fresh = not credentials_path.exists()
    credentials = json.loads(credentials_path.read_text()) if not fresh else dict(
        username="quizcom_" + secrets.token_hex(5), password=secrets.token_urlsafe(30))
    if fresh:
        save_json(credentials_path, credentials, private=True)
    client = Client(args.host, args.port)
    try:
        if fresh:
            client.request("REGISTER", dict(**credentials, displayName="Nhập từ Quiz.com", avatarId="avatar-01"),
                           "REGISTER_RESULT")
        profile = client.request("LOGIN", credentials, "LOGIN_RESULT")["profile"]
        state["ownerUserId"] = profile["userId"]
        for playable, label in [(True, "playable"), (False, "requires-video")]:
            selected = [q for q in questions if q["playable"] == playable]
            for offset in range(0, len(selected), 50):
                batch = selected[offset:offset + 50]
                group_key = f"{label}-{offset // 50 + 1}"
                group = state["groups"].get(group_key)
                title = source["name"] + ("" if playable else " — Cần video (bản nháp)")
                if len(selected) > 50:
                    title += f" — Phần {offset // 50 + 1}"
                description = f"Nguồn: {args.url}\nTác giả gốc: {source['owner']['name']}. "
                description += ("Các câu đủ nội dung chữ; giữ đáp án gốc. " if playable else
                                "Cần xem video YouTube theo mốc thời gian trong từng câu. App hiện chưa phát video. ")
                description += "Đã bỏ slide giới thiệu. Không tính xếp hạng."
                if group is None:
                    created = client.author("CREATE")
                    group = dict(quizId=created["quizId"], verified=False)
                    state["groups"][group_key] = group
                    save_json(state_path, state)
                quiz_id = group["quizId"]
                meta = client.author("GET", quiz_id)
                if group.get("verified"):
                    if meta["questionCount"] != len(batch) or (meta["status"] == "PUBLISHED") != playable:
                        raise ValueError("Imported quiz was edited; refusing automatic overwrite")
                else:
                    client.author("SAVE_META", quiz_id, dict(title=title, description=description,
                                  categoryId=args.category_id, shuffleQuestions=False))
                    for index, q in enumerate(batch):
                        client.author("SAVE_QUESTION", quiz_id, dict(index=index, question=q["question"]))
                # Verify every stored field, including accepted answer alternatives.
                for index, q in enumerate(batch):
                    loaded = client.author("GET_QUESTION", quiz_id, dict(index=index))["question"]
                    if loaded != q["question"]:
                        raise ValueError(f"Question read-back mismatch: {quiz_id}/{index}")
                if playable and not group.get("verified"):
                    client.author("PUBLISH", quiz_id)
                meta = client.author("GET", quiz_id)
                if meta["questionCount"] != len(batch) or (meta["status"] == "PUBLISHED") != playable:
                    raise ValueError("Unexpected final quiz status/count")
                if playable:
                    detail = client.request("QUIZ_DETAIL_REQUEST", dict(quizId=quiz_id), "QUIZ_DETAIL")
                    if detail["quiz"]["totalRounds"] != len(batch):
                        raise ValueError("Published round count mismatch")
                    group["detail"] = detail
                group.update(title=title, questionCount=len(batch), status=meta["status"],
                             sourceSlideNumbers=[q["sourceSlideNumber"] for q in batch], verified=True)
                save_json(state_path, state)
                print(f"Verified quiz {quiz_id}: {title}, {len(batch)} questions, {meta['status']}.", flush=True)
        visible, page = set(), 1
        while True:
            listing = client.request("QUIZ_LIST_REQUEST", dict(categoryId=args.category_id,
                                     page=page, pageSize=10), "QUIZ_LIST")
            visible.update(q["quizId"] for q in listing["items"])
            if page * 10 >= listing["totalItems"]:
                break
            page += 1
        for group in state["groups"].values():
            if (group["quizId"] in visible) != (group["status"] == "PUBLISHED"):
                raise ValueError("Lobby visibility mismatch")
        state["lobbyVerified"] = True
        save_json(state_path, state)
        client.request("LOGOUT", {}, "LOGOUT_ACK")
    finally:
        client.sock.close()
    print("Report: " + str(state_path))


if __name__ == "__main__":
    main()
