#!/usr/bin/env python3
"""Generate three Vietnamese quiz packs and optionally publish through the app API.

Each pack contains two questions of every supported type. Import state prevents
duplicate packs on reruns; credentials stay in ignored data/backups/.
"""

import argparse
import base64
from collections import Counter
import hashlib
import importlib.util
import json
from pathlib import Path
import secrets

ROOT = Path(__file__).resolve().parents[1]
FOLDER = ROOT / "data/generated-quizzes/student-bank-v1"
TYPES = {"SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "SHORT_ANSWER",
         "REORDER", "RANGE", "PINPOINT"}


def question(kind, content, key, explanation, choices=(), image=None):
    return dict(questionType=kind, content=content,
                options=[dict(id=chr(65+i), text=t) for i, t in enumerate(choices)],
                answerKey=key, explanation=explanation,
                questionAssetId=None, explanationAssetId=None, localImage=image)


def single(content, choices, key, why):
    return question("SINGLE_CHOICE", content, key, why, choices)


def multi(content, choices, keys, why):
    return question("MULTIPLE_CHOICE", content, keys, why, choices)


def boolean(content, key, why):
    return question("TRUE_FALSE", content, key, why)


def short(content, keys, why):
    return question("SHORT_ANSWER", content, keys, why)


def reorder(content, choices, why):
    return question("REORDER", content, list("ABCDEF"[:len(choices)]), why, choices)


def number(content, minimum, maximum, target, unit, why):
    return question("RANGE", content, dict(min=minimum, max=maximum, step=1,
                    unit=unit, acceptedMin=target, acceptedMax=target), why)


def pinpoint(content, image, rect, why):
    x, y, w, h = rect
    return question("PINPOINT", content, dict(x=round(x/1000, 6), y=round(y/600, 6),
                    width=round(w/1000, 6), height=round(h/600, 6)), why, image=image)


def packs():
    return [dict(key="networks", categoryId=3, title="Mạng máy tính — 7 dạng câu hỏi", questions=[
        single("Giao thức nào cung cấp luồng byte tin cậy, có thứ tự giữa hai ứng dụng?",
               ["UDP", "TCP", "IP", "ARP"], "B", "TCP cung cấp luồng byte có thứ tự và cơ chế truyền lại khi mất dữ liệu."),
        single("IPv4 có độ dài địa chỉ là bao nhiêu bit?", ["16 bit", "64 bit", "128 bit", "32 bit"], "D", "Địa chỉ IPv4 gồm 32 bit, thường biểu diễn bằng bốn số thập phân."),
        multi("Chọn tất cả giao thức thuộc tầng ứng dụng trong mô hình TCP/IP.",
              ["HTTP", "TCP", "DNS", "Ethernet"], ["A", "C"], "HTTP và DNS thuộc tầng ứng dụng; TCP thuộc tầng giao vận, Ethernet thuộc tầng liên kết."),
        multi("Để đọc một thông điệp trên TCP, ứng dụng có thể dùng những cách phân định nào?",
              ["Prefix chứa độ dài", "Ký tự phân cách có quy tắc escape", "Mặc định mỗi lần read là một thông điệp", "Thông điệp có độ dài cố định"],
              ["A", "B", "D"], "TCP không giữ ranh giới thông điệp. Có thể dùng độ dài, dấu phân cách hoặc kích thước cố định để phân khung."),
        boolean("Một lần gọi read() trên socket TCP luôn nhận đủ một thông điệp do phía gửi gửi đi.", False, "TCP là luồng byte: một thông điệp có thể bị chia qua nhiều lần đọc hoặc nhiều thông điệp đến cùng lần đọc."),
        boolean("Hai kết nối TCP khác nhau có thể cùng kết nối tới một cổng lắng nghe trên server.", True, "Server phân biệt các kết nối bằng các đầu mút địa chỉ IP và cổng; nhiều client có thể dùng chung cổng server."),
        short("Viết tên viết tắt của hệ thống phân giải tên miền sang địa chỉ IP.", ["DNS", "Domain Name System"], "DNS là Domain Name System, dùng để phân giải tên miền và nhiều loại bản ghi khác."),
        short("Trong địa chỉ 127.0.0.1, máy đích chính là máy đang chạy ứng dụng. Địa chỉ này được gọi là địa chỉ gì? (Nhập tiếng Anh.)",
              ["loopback", "loopback address", "localhost"], "127.0.0.1 là địa chỉ loopback IPv4, thường được ánh xạ bởi tên localhost."),
        reorder("Sắp xếp các bước của một TCP client đơn giản theo thứ tự thực hiện.",
                ["Tạo socket", "Kết nối tới server", "Trao đổi dữ liệu", "Đóng socket"], "Client tạo socket, kết nối, trao đổi dữ liệu rồi giải phóng kết nối."),
        reorder("Sắp xếp các lớp sau theo thứ tự từ gần ứng dụng nhất tới gần đường truyền nhất.",
                ["Ứng dụng", "Giao vận", "Internet", "Liên kết"], "Mô hình TCP/IP bốn tầng theo thứ tự từ trên xuống: ứng dụng, giao vận, Internet, liên kết."),
        number("Một frame gồm prefix 4 byte và JSON dài 120 byte. Tổng kích thước frame là bao nhiêu byte?", 0, 200, 124, "byte", "Tổng kích thước bằng 4 + 120 = 124 byte."),
        number("Một địa chỉ IPv4 gồm 32 bit. Nếu chia thành 4 nhóm bằng nhau, mỗi nhóm có bao nhiêu bit?", 0, 32, 8, "bit", "32 / 4 = 8 bit, tức một octet."),
        pinpoint("Ghim vào phần giữa thiết bị Switch trong sơ đồ mạng hình sao.", "network-star.png", (410, 245, 180, 90), "Switch nằm ở trung tâm, nối các máy trạm trong sơ đồ."),
        pinpoint("Ghim vào vùng PREFIX 4 BYTE trong hình minh họa frame TCP của ứng dụng.", "network-frame.png", (75, 245, 180, 110), "Prefix chứa độ dài phần JSON, cho phép bên nhận xác định ranh giới thông điệp.")]),
        dict(key="java", categoryId=3, title="Java cơ bản — 7 dạng câu hỏi", questions=[
        single("Trong Java, từ khóa nào dùng để tạo một đối tượng bằng constructor?", ["class", "return", "new", "static"], "C", "Toán tử new tạo đối tượng mới và gọi constructor tương ứng."),
        single("Biểu thức Java 7 / 2 có giá trị nào khi cả hai toán hạng đều là int?", ["3", "3.5", "4", "2"], "A", "Phép chia hai số nguyên trả về phần nguyên; với số dương, 7 / 2 = 3."),
        multi("Chọn tất cả kiểu dữ liệu nguyên thủy của Java.", ["int", "String", "boolean", "double"], ["A", "C", "D"], "int, boolean và double là kiểu nguyên thủy; String là một lớp."),
        multi("Chọn tất cả từ khóa truy cập hợp lệ trong Java.", ["public", "internal", "private", "protected"], ["A", "C", "D"], "Java có public, private, protected và mức truy cập package khi không ghi từ khóa."),
        boolean("Mảng Java đánh chỉ số phần tử đầu tiên từ 0.", True, "Mảng có n phần tử sử dụng các chỉ số từ 0 đến n - 1."),
        boolean("Đối với hai đối tượng String, toán tử == luôn so sánh nội dung ký tự.", False, "== so sánh tham chiếu đối tượng; thường dùng equals() để so sánh nội dung chuỗi."),
        short("Nhập tên phương thức dùng để lấy độ dài một String trong Java (có thể kèm dấu ngoặc).", ["length", "length()"], "String.length() trả về số đơn vị mã UTF-16 của chuỗi; đây là phương thức, không phải thuộc tính length của mảng."),
        short("Từ khóa nào dùng để kế thừa một lớp trong Java?", ["extends"], "Một lớp dùng extends để khai báo lớp cha, ví dụ class Dog extends Animal."),
        reorder("Sắp xếp các bước chạy chương trình Java từ mã nguồn.", ["Viết file .java", "Biên dịch bằng javac", "Tạo bytecode .class", "JVM thực thi bytecode"], "javac biên dịch mã nguồn thành bytecode; JVM nạp và thực thi bytecode."),
        reorder("Sắp xếp các bước của thuật toán tìm giá trị lớn nhất trong một mảng không rỗng.", ["Gán max bằng phần tử đầu", "Duyệt các phần tử còn lại", "Nếu phần tử lớn hơn max thì cập nhật max", "Trả về max sau khi duyệt xong"], "Khởi tạo từ phần tử đầu giúp xử lý đúng cả mảng toàn số âm."),
        number("int sum = 0; for (int i = 1; i <= 5; i++) sum += i; Sau vòng lặp, sum bằng bao nhiêu?", 0, 30, 15, "", "sum = 1 + 2 + 3 + 4 + 5 = 15."),
        number("Một mảng Java có length = 8. Chỉ số hợp lệ lớn nhất là bao nhiêu?", 0, 12, 7, "", "Chỉ số cuối là length - 1 = 7."),
        pinpoint("Ghim vào giữa khối điều kiện x > 0 trong lưu đồ.", "java-flow.png", (445, 255, 110, 90), "Khối hình thoi kiểm tra điều kiện và chọn nhánh đúng hoặc sai."),
        pinpoint("Với int[] a = {10, 20, 30, 40, 50}, ghim vào ô chứa a[3].", "java-array.png", (620, 250, 120, 90), "Mảng bắt đầu từ chỉ số 0 nên a[3] = 40.")]),
        dict(key="math", categoryId=1, title="Toán và logic — 7 dạng câu hỏi", questions=[
        single("Tổng ba góc trong một tam giác trên mặt phẳng bằng bao nhiêu độ?", ["90", "180", "270", "360"], "B", "Trong hình học phẳng Euclid, tổng ba góc của tam giác là 180 độ."),
        single("Số nào sau đây là số nguyên tố?", ["21", "27", "29", "33"], "C", "29 chỉ có hai ước dương là 1 và 29; các số còn lại đều chia hết cho 3."),
        multi("Chọn tất cả số chia hết cho 3.", ["12", "14", "21", "25"], ["A", "C"], "12 = 3 × 4 và 21 = 3 × 7."),
        multi("Chọn tất cả số chính phương.", ["16", "18", "25", "36"], ["A", "C", "D"], "16 = 4², 25 = 5², 36 = 6²; 18 không phải số chính phương."),
        boolean("Số 1 là số nguyên tố.", False, "Số nguyên tố phải lớn hơn 1 và có đúng hai ước dương phân biệt."),
        boolean("Nếu cả P và Q đều đúng thì mệnh đề P AND Q đúng.", True, "Phép hội AND đúng khi và chỉ khi cả hai mệnh đề thành phần đều đúng."),
        short("Đa giác có ba cạnh được gọi là hình gì?", ["tam giác", "hình tam giác", "tam giac", "hinh tam giac"], "Tam giác có ba cạnh và ba đỉnh."),
        short("Số nào là phần tử trung hòa của phép cộng các số nguyên?", ["0", "không", "khong", "số 0", "so 0"], "Với mọi số nguyên a, a + 0 = 0 + a = a."),
        reorder("Sắp xếp các số theo thứ tự tăng dần.", ["-3", "0", "2", "5"], "Trên trục số, -3 < 0 < 2 < 5."),
        reorder("Sắp xếp các phân số theo thứ tự tăng dần.", ["1/4", "1/2", "3/4", "1"], "Các giá trị tương ứng là 0,25; 0,5; 0,75; 1."),
        number("Hình chữ nhật dài 8 cm, rộng 5 cm có diện tích bao nhiêu cm²?", 0, 80, 40, "cm²", "Diện tích hình chữ nhật bằng dài × rộng = 8 × 5 = 40 cm²."),
        number("Có 4 viên bi đỏ và 6 viên bi xanh. Xác suất lấy ngẫu nhiên một viên bi đỏ là bao nhiêu phần trăm?", 0, 100, 40, "%", "Có 10 viên bi; xác suất lấy bi đỏ là 4/10 = 40%."),
        pinpoint("Ghim vào điểm có tọa độ (-2; 2) trên mặt phẳng tọa độ.", "math-plane.png", (275, 155, 50, 50), "Điểm (-2; 2) nằm bên trái trục tung và phía trên trục hoành, tại vị trí A."),
        pinpoint("Ghim vào phần giữa hình tròn trong ảnh.", "math-shapes.png", (685, 365, 70, 70), "Hình tròn nằm phía dưới bên phải; các hình còn lại là vuông, tam giác và chữ nhật.")])]


def make_images():
    from PIL import Image, ImageDraw, ImageFont
    font_path = "/System/Library/Fonts/Supplemental/Arial.ttf"
    font = ImageFont.truetype(font_path, 27)
    small = ImageFont.truetype(font_path, 22)
    title_font = ImageFont.truetype(font_path, 36)
    FOLDER.mkdir(parents=True, exist_ok=True)

    def canvas(title):
        im = Image.new("RGB", (1000, 600), "#f4f7fc")
        draw = ImageDraw.Draw(im)
        draw.text((45, 35), title, font=title_font, fill="#17243a")
        return im, draw

    def box(d, rect, label, color="#dceaff"):
        d.rounded_rectangle(rect, radius=16, fill=color, outline="#294d78", width=3)
        d.text(((rect[0]+rect[2])/2, (rect[1]+rect[3])/2), label, font=font, fill="#17243a", anchor="mm")

    im, d = canvas("Mạng hình sao")
    for x, y in [(180, 170), (820, 170), (180, 450), (820, 450)]:
        d.line((500, 290, x, y), fill="#7c94b5", width=6)
    box(d, (390, 220, 610, 360), "Switch")
    for i, (x, y) in enumerate([(180, 170), (820, 170), (180, 450), (820, 450)]):
        box(d, (x-100, y-45, x+100, y+45), f"Máy {i+1}")
    im.save(FOLDER / "network-star.png")

    im, d = canvas("Khung dữ liệu ứng dụng trên TCP")
    box(d, (70, 240, 260, 360), "PREFIX", "#ffe3ba")
    box(d, (275, 240, 930, 360), "JSON UTF-8")
    d.text((165, 400), "4 BYTE", font=font, fill="#17243a", anchor="mm")
    d.text((602, 400), "ĐỘ DÀI THAY ĐỔI", font=font, fill="#17243a", anchor="mm")
    im.save(FOLDER / "network-frame.png")

    im, d = canvas("Lưu đồ kiểm tra dấu của x")
    d.line((500, 170, 500, 200), fill="#7c94b5", width=5)
    d.line((350, 300, 190, 300, 190, 440), fill="#7c94b5", width=5)
    d.line((650, 300, 810, 300, 810, 440), fill="#7c94b5", width=5)
    box(d, (390, 105, 610, 170), "Nhập x")
    d.polygon([(500, 200), (650, 300), (500, 400), (350, 300)], fill="#ffe3ba", outline="#294d78", width=3)
    d.text((500, 300), "x > 0 ?", font=font, fill="#17243a", anchor="mm")
    box(d, (60, 440, 320, 520), "In: không dương")
    box(d, (690, 440, 930, 520), "In: số dương")
    d.text((240, 265), "Sai", font=small, fill="#17243a")
    d.text((725, 265), "Đúng", font=small, fill="#17243a")
    im.save(FOLDER / "java-flow.png")

    im, d = canvas("int[] a = {10, 20, 30, 40, 50}")
    for i in range(5):
        left = 70 + i*180
        box(d, (left, 240, left+160, 360), str((i+1)*10))
        d.text((left+80, 415), f"index {i}", font=font, fill="#17243a", anchor="mm")
    im.save(FOLDER / "java-array.png")

    im, d = canvas("Mặt phẳng tọa độ Oxy")
    for value in range(-3, 4):
        x, y = 500+value*100, 300-value*60
        d.line((x, 120, x, 480), fill="#d3deed", width=2)
        d.line((200, y, 800, y), fill="#d3deed", width=2)
        if value:
            d.text((x, 320), str(value), font=small, fill="#17243a", anchor="mt")
            d.text((480, y), str(value), font=small, fill="#17243a", anchor="rm")
    d.line((180, 300, 830, 300), fill="#294d78", width=4)
    d.line((500, 500, 500, 100), fill="#294d78", width=4)
    d.text((850, 300), "x", font=font, fill="#17243a", anchor="mm")
    d.text((500, 90), "y", font=font, fill="#17243a", anchor="mm")
    for label, x, y in [("A", 300, 180), ("B", 700, 180), ("C", 300, 420), ("D", 700, 420)]:
        d.ellipse((x-11, y-11, x+11, y+11), fill="#ce563c")
        d.text((x+18, y-22), label, font=font, fill="#17243a")
    im.save(FOLDER / "math-plane.png")

    im, d = canvas("Nhận biết hình học")
    d.rectangle((170, 140, 330, 300), fill="#b9d5fa", outline="#294d78", width=4)
    d.polygon([(720, 130), (620, 300), (820, 300)], fill="#ffe3ba", outline="#294d78", width=4)
    d.rectangle((130, 370, 370, 470), fill="#c8e8d8", outline="#294d78", width=4)
    d.ellipse((635, 315, 805, 485), fill="#e0ccf7", outline="#294d78", width=4)
    im.save(FOLDER / "math-shapes.png")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="180.93.38.185")
    parser.add_argument("--port", type=int, default=5555)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location("quiz_import", ROOT / "scripts/import-quizcom.py")
    api = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(api)
    make_images()
    bank = packs()
    for pack in bank:
        assert Counter(q["questionType"] for q in pack["questions"]) == Counter({t: 2 for t in TYPES})
        for q in pack["questions"]:
            assert len(q["content"]) <= 500 and len(q["explanation"]) <= 500
            assert all(len(o["text"]) <= 120 for o in q["options"])
    api.save_json(FOLDER / "questions.json", bank)
    digest = hashlib.sha256(json.dumps(bank, ensure_ascii=False, sort_keys=True).encode()).hexdigest()
    print("Prepared 3 packs, 42 questions, 6 PNG illustrations.", flush=True)
    if not args.apply:
        return
    state_path = FOLDER / f"import-{args.host}-{args.port}.json"
    state = json.loads(state_path.read_text()) if state_path.exists() else dict(manifestSha256=digest, groups={}, media={})
    if state["manifestSha256"] != digest:
        raise ValueError("Question manifest changed; inspect previous import before continuing")
    credentials_path = ROOT / f"data/backups/generated-quiz-accounts/{args.host}-{args.port}.json"
    fresh = not credentials_path.exists()
    credentials = json.loads(credentials_path.read_text()) if not fresh else dict(
        username="quizbank_"+secrets.token_hex(5), password=secrets.token_urlsafe(30))
    if fresh:
        api.save_json(credentials_path, credentials, private=True)
    client = api.Client(args.host, args.port)
    try:
        if fresh:
            client.request("REGISTER", dict(**credentials, displayName="Ngân hàng câu hỏi", avatarId="avatar-01"), "REGISTER_RESULT")
        profile = client.request("LOGIN", credentials, "LOGIN_RESULT")["profile"]
        state["ownerUserId"] = profile["userId"]
        for image in sorted({q["localImage"] for p in bank for q in p["questions"] if q["localImage"]}):
            if image in state["media"]:
                continue
            raw = (FOLDER / image).read_bytes()
            started = client.request("MEDIA_UPLOAD_START", dict(mimeType="image/png", byteSize=len(raw), sha256=hashlib.sha256(raw).hexdigest()), "MEDIA_UPLOAD_STARTED")
            for offset in range(0, len(raw), 24*1024):
                chunk = raw[offset:offset+24*1024]
                ack = client.request("MEDIA_UPLOAD_CHUNK", dict(transferId=started["transferId"], offset=offset, data=base64.b64encode(chunk).decode()), "MEDIA_UPLOAD_ACK")
                assert ack["nextOffset"] == offset+len(chunk)
            assert ack["mediaId"]
            state["media"][image] = ack["mediaId"]
            api.save_json(state_path, state)
        for pack in bank:
            group = state["groups"].get(pack["key"])
            if group is None:
                created = client.author("CREATE")
                group = dict(quizId=created["quizId"], verified=False)
                state["groups"][pack["key"]] = group
                api.save_json(state_path, state)
            quiz_id = group["quizId"]
            stored = []
            for q in pack["questions"]:
                item = {k: v for k, v in q.items() if k != "localImage"}
                if q["localImage"]:
                    item["questionAssetId"] = state["media"][q["localImage"]]
                stored.append(item)
            if not group["verified"]:
                client.author("SAVE_META", quiz_id, dict(title=pack["title"], description="14 câu tiếng Việt, đủ 7 dạng; mỗi dạng 2 câu. Có giải thích đáp án và ảnh cho câu ghim vị trí. Nội dung tự biên soạn để luyện tập và demo đồ án.", categoryId=pack["categoryId"], shuffleQuestions=False))
                for i, q in enumerate(stored):
                    client.author("SAVE_QUESTION", quiz_id, dict(index=i, question=q))
            for i, q in enumerate(stored):
                loaded = client.author("GET_QUESTION", quiz_id, dict(index=i))["question"]
                if loaded != q:
                    raise ValueError(f"Read-back mismatch: {quiz_id}/{i}")
            meta = client.author("GET", quiz_id)
            if meta["status"] != "PUBLISHED":
                client.author("PUBLISH", quiz_id)
            detail = client.request("QUIZ_DETAIL_REQUEST", dict(quizId=quiz_id), "QUIZ_DETAIL")
            assert detail["quiz"]["totalRounds"] == 14
            assert detail["quiz"]["availability"] == "AVAILABLE", detail
            group.update(verified=True, title=pack["title"], questionCount=14, status="PUBLISHED", types=dict(Counter(q["questionType"] for q in stored)), detail=detail)
            api.save_json(state_path, state)
            print(f"Published and verified: {quiz_id} — {pack['title']}", flush=True)
        # Check full PNG download, not only metadata, after storage on the server.
        for filename, media_id in state["media"].items():
            received = bytearray()
            while True:
                part = client.request("MEDIA_REQUEST", dict(mediaId=media_id, offset=len(received)), "MEDIA_CHUNK")
                received.extend(base64.b64decode(part["data"]))
                if len(received) == part["totalSize"]:
                    break
            assert received == (FOLDER / filename).read_bytes()
        visible, page = set(), 1
        while True:
            listing = client.request("QUIZ_LIST_REQUEST", dict(categoryId=None, page=page, pageSize=10), "QUIZ_LIST")
            visible.update(q["quizId"] for q in listing["items"])
            if page*10 >= listing["totalItems"]:
                break
            page += 1
        assert all(g["quizId"] in visible for g in state["groups"].values())
        state.update(lobbyVerified=True, mediaDownloadVerified=True)
        api.save_json(state_path, state)
        client.request("LOGOUT", {}, "LOGOUT_ACK")
    finally:
        client.sock.close()
    print("Verified all 42 stored questions, 6 image downloads and lobby visibility.", flush=True)


if __name__ == "__main__":
    main()
