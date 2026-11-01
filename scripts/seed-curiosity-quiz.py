#!/usr/bin/env python3
"""Prepare one reviewed 20-question Vietnamese quiz; --apply publishes it via TCP.

Uses the existing authoring/media protocol. Saves resumable import state and
keeps credentials in ignored data/backups/, never in the quiz manifest.
Requires Pillow only for the original explanatory diagrams.
"""

import argparse
import base64
from collections import Counter
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import secrets

ROOT = Path(__file__).resolve().parents[1]
FOLDER = ROOT / "data/generated-quizzes/curiosity-v1"
TITLE = "Đi một vòng thế giới — 20 điều thú vị"
DESCRIPTION = "20 câu mức vừa về lịch sử, địa lý, khoa học và văn hóa. Sáu dạng câu hỏi, ảnh nhận diện và sơ đồ giải thích. Chơi khoảng 10 phút."


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / filename)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


seed = module("curiosity_seed", "seed-demo-quizzes.py")
api = module("curiosity_import", "import-quizcom.py")


def questions():
    s, m, b, r = seed.single, seed.multi, seed.boolean, seed.reorder
    bank = [
        s("Sydney và Melbourne rất nổi tiếng, nhưng thủ đô của Úc là thành phố nào?",
          ["Sydney", "Canberra", "Melbourne", "Perth"], "B",
          "Canberra là thủ đô Úc, được chọn trong quá trình tìm địa điểm đặt thủ đô liên bang giữa Sydney và Melbourne."),
        s("Vị vua nào dời đô từ Hoa Lư ra Đại La và đặt tên kinh đô là Thăng Long năm 1010?",
          ["Lý Thánh Tông", "Trần Thái Tông", "Lê Đại Hành", "Lý Thái Tổ"], "D",
          "Lý Thái Tổ (Lý Công Uẩn) dời đô năm 1010. Đừng nhầm với Lý Thánh Tông, vị vua đổi quốc hiệu thành Đại Việt năm 1054."),
        s("Hành tinh nào mất nhiều thời gian tự quay một vòng hơn thời gian đi một vòng quanh Mặt Trời?",
          ["Sao Kim", "Sao Hỏa", "Sao Mộc", "Sao Hải Vương"], "A",
          "Sao Kim tự quay một vòng khoảng 243 ngày Trái Đất, nhưng quay quanh Mặt Trời chỉ khoảng 225 ngày. Đây là chu kỳ tự quay, khác khoảng giữa hai lần Mặt Trời mọc."),
        s("Nhìn sơ đồ: Mặt Trăng che ánh sáng Mặt Trời tới một vùng trên Trái Đất. Người ở vùng đó quan sát hiện tượng gì?",
          ["Nguyệt thực", "Cực quang", "Nhật thực", "Mưa sao băng"], "C",
          "Nhật thực xảy ra khi Mặt Trăng nằm giữa Mặt Trời và Trái Đất, che khuất Mặt Trời với người ở vùng bóng. Nguyệt thực có Trái Đất ở giữa."),
        s("Kênh đào Suez giúp tàu thuyền đi tắt giữa hai vùng biển nào?",
          ["Biển Đen và Biển Caspi", "Địa Trung Hải và Biển Đỏ", "Đại Tây Dương và Thái Bình Dương", "Biển Baltic và Biển Bắc"], "B",
          "Suez ở Ai Cập nối Địa Trung Hải với Biển Đỏ. Kênh Panama mới là tuyến nối Đại Tây Dương với Thái Bình Dương."),
        s("Trong chiến thắng Bạch Đằng năm 1288, vị tướng nào chỉ huy quân Đại Việt đánh quân Nguyên bằng trận địa cọc?",
          ["Ngô Quyền", "Lê Hoàn", "Trần Hưng Đạo", "Lê Lợi"], "C",
          "Trần Hưng Đạo chỉ huy chiến thắng năm 1288. Cũng trên sông Bạch Đằng, Ngô Quyền thắng quân Nam Hán năm 938, còn Lê Hoàn thắng quân Tống năm 981."),
        s("Di sản nào ở Việt Nam được UNESCO ghi danh năm 2014 theo cả giá trị văn hóa lẫn thiên nhiên?",
          ["Quần thể danh thắng Tràng An", "Phố cổ Hội An", "Thánh địa Mỹ Sơn", "Hoàng thành Thăng Long"], "A",
          "Tràng An là di sản hỗn hợp văn hóa và thiên nhiên. Cảnh quan đá vôi, hang động và dấu tích cư trú của con người cùng tạo nên giá trị di sản."),
        s("Nếu tính cả sa mạc lạnh, sa mạc có diện tích lớn nhất thế giới nằm ở đâu?",
          ["Sahara", "Bán đảo Ả Rập", "Gobi", "Nam Cực"], "D",
          "Nam Cực là sa mạc lớn nhất khi tính cả sa mạc lạnh. Sa mạc được xác định chủ yếu bởi lượng mưa, tuyết ít, không phải cứ nóng và nhiều cát mới là sa mạc."),
        s("Lĩnh vực nào sau đây không có một hạng mục giải Nobel riêng?",
          ["Vật lý", "Hóa học", "Toán học", "Văn học"], "C",
          "Không có hạng mục Nobel Toán học. Các giải lớn của toán học gồm Huy chương Fields và giải Abel; một nhà toán học vẫn có thể nhận Nobel trong lĩnh vực khác."),
        s("Di tích Machu Picchu của nền văn minh Inca nằm ở quốc gia nào?",
          ["Mexico", "Peru", "Ai Cập", "Campuchia"], "B",
          "Machu Picchu nằm trên dãy Andes ở Peru, là một di tích tiêu biểu của nền văn minh Inca. Aztec là nền văn minh khác, gắn với Mexico."),
        s("Cây cà phê Arabica có nguồn gốc tự nhiên ở châu lục nào, trước khi được trồng rộng rãi ở Brazil và Việt Nam?",
          ["Nam Mỹ", "Châu Á", "Châu Âu", "Châu Phi"], "D",
          "Arabica có nguồn gốc ở châu Phi, đặc biệt gắn với vùng cao Ethiopia. Quốc gia trồng nhiều cà phê ngày nay không nhất thiết là nơi cây có nguồn gốc."),
        s("Lá cờ hai đuôi nheo trong hình là quốc kỳ của nước nào?",
          ["Nepal", "Bhutan", "Sri Lanka", "Mông Cổ"], "A",
          "Đây là quốc kỳ Nepal: hai đuôi nheo ghép lại, nền đỏ viền xanh, có biểu tượng Mặt Trăng và Mặt Trời."),
        m("Chọn TẤT CẢ những động vật có vú nhưng đẻ trứng trong danh sách.",
          ["Cá heo", "Thú mỏ vịt", "Thú lông nhím (echidna)", "Chim cánh cụt"], ["B", "C"],
          "Thú mỏ vịt và echidna là thú đơn huyệt, đẻ trứng nhưng vẫn nuôi con bằng sữa. Chim cánh cụt cũng đẻ trứng, nhưng thuộc lớp chim."),
        m("Ở 20°C và áp suất khí quyển bình thường, chọn TẤT CẢ các nguyên tố ở trạng thái lỏng.",
          ["Brom (Br)", "Sắt (Fe)", "Thủy ngân (Hg)", "Nhôm (Al)"], ["A", "C"],
          "Brom và thủy ngân đều lỏng ở 20°C. Brom là phi kim, thủy ngân là kim loại; sắt và nhôm ở trạng thái rắn trong điều kiện này."),
        b("Trong chân không lý tưởng, âm thanh vẫn truyền từ nguồn phát đến tai người nghe như trong không khí.", False,
          "Âm thanh là sóng cơ, cần môi trường vật chất để truyền. Trong chân không lý tưởng, ánh sáng vẫn truyền được còn âm thanh thì không."),
        b("Các mùa trên Trái Đất chủ yếu xảy ra vì khoảng cách từ Trái Đất đến Mặt Trời thay đổi trong năm.", False,
          "Nguyên nhân chính là trục Trái Đất nghiêng, làm thay đổi góc chiếu sáng và độ dài ban ngày. Khi Bắc bán cầu là mùa hè thì Nam bán cầu là mùa đông."),
        r("Xếp các sự kiện từ xảy ra SỚM NHẤT đến MUỘN NHẤT.",
          ["Columbus tới châu Mỹ", "Cách mạng Pháp bùng nổ", "Chiến tranh thế giới thứ nhất bắt đầu", "Con người lần đầu đặt chân lên Mặt Trăng"],
          "Thứ tự: Columbus tới châu Mỹ (1492) → Cách mạng Pháp (1789) → Thế chiến I (1914) → Apollo 11 đưa người lên Mặt Trăng (1969)."),
        r("Xếp bốn hành tinh theo khoảng cách trung bình tới Mặt Trời, từ GẦN đến XA.",
          ["Sao Thủy", "Sao Kim", "Trái Đất", "Sao Hỏa"],
          "Bốn hành tinh đá theo thứ tự từ Mặt Trời ra ngoài là Sao Thủy → Sao Kim → Trái Đất → Sao Hỏa. Vành đai tiểu hành tinh nằm xa hơn, trước Sao Mộc."),
        seed.number("Quang Trung đại phá quân Thanh trong chiến thắng Ngọc Hồi – Đống Đa vào năm nào?", 1750, 1820, 1789, "năm",
                    "Đáp án là năm 1789, tức Tết Kỷ Dậu. Chiến dịch đánh tan quân Thanh gắn liền với tên tuổi Hoàng đế Quang Trung."),
        seed.short("Hệ chữ nào là nền tảng của chữ Quốc ngữ tiếng Việt? (Nhập tên hệ chữ.)",
                   ["Latinh", "La tinh", "La-tinh", "Latin", "Latin alphabet", "chữ Latinh", "chữ La tinh", "chữ La-tinh", "chu Latinh", "chu La tinh", "hệ chữ Latinh", "he chu Latinh"],
                   "Chữ Quốc ngữ dùng hệ chữ Latinh, bổ sung dấu thanh và các chữ như ă, â, ê, ô, ơ, ư, đ để ghi âm tiếng Việt."),
    ]
    images = {
        3: (None, "venus-answer.png"),
        4: ("eclipse-question.png", "eclipse-answer.png"),
        5: (None, "suez-answer.png"),
        8: (None, "desert-answer.png"),
        12: ("nepal-question.png", None),
        14: (None, "elements-answer.png"),
        17: (None, "timeline-answer.png"),
        18: (None, "planets-answer.png"),
    }
    for index, q in enumerate(bank, 1):
        q.pop("localImage")
        q["localQuestionImage"], q["localExplanationImage"] = images.get(index, (None, None))
    # The initial drag order must not itself be the answer.
    for q in bank:
        if q["questionType"] == "REORDER":
            q["options"] = [q["options"][2], q["options"][0], q["options"][3], q["options"][1]]
    return bank


SOURCES = {
    "1": "https://www.nca.gov.au/education/canberras-history/siting-and-naming-canberra",
    "2": "https://scov.gov.vn/dat-nuoc-con-nguoi/tom-tat-bien-nien-su-vn/xiii.-nha-ly-1010-1225-215-nam-quoc-hieu-dai-co-viet-kinh-do-hoa-lu.-nam-1010-doi-do-ve-thang-long.-tu-1054-doi-quoc-hie2.html",
    "3": "https://science.nasa.gov/venus/venus-facts/",
    "4": "https://science.nasa.gov/eclipses/geometry/",
    "5": "https://www.suezcanal.gov.eg/English/About/SuezCanal/Pages/AboutSuezCanal.aspx",
    "6": "https://baotanglichsu.vn/vi/Articles/1002/28186/coc-bach-djang.html",
    "7": "https://whc.unesco.org/en/list/1438/",
    "8": "https://education.nationalgeographic.org/resource/desert/",
    "9": "https://www.nobelprize.org/prizes/list/all-nobel-prizes/",
    "10": "https://whc.unesco.org/en/list/274/",
    "11": "https://www.mnhn.fr/en/arabica-coffee",
    "12": "https://www.moljpa.gov.np/public/uploads/238f7219-492b-40af-a919-c94c35f9c269.pdf",
    "13": "https://australian.museum/learn/species-identification/ask-an-expert/what-is-a-monotreme/",
    "14": ["https://periodic-table.rsc.org/element/35/bromine", "https://periodic-table.rsc.org/element/80/mercury"],
    "15": "https://howthingsfly.si.edu/ask-an-explainer/what-speed-sound-space",
    "16": "https://spaceplace.nasa.gov/seasons/en/",
    "17": ["https://loc.gov/exhibits/exploring-the-early-americas/columbus-and-the-taino.html", "https://en.chateauversailles.fr/discover/history/key-dates/versailles-heart-french-revolution", "https://www.iwm.org.uk/history/how-the-world-went-to-war-in-1914", "https://www.nasa.gov/mission/apollo-11/"],
    "18": "https://science.nasa.gov/solar-system/planets/",
    "19": "https://baochinhphu.vn/ky-niem-chien-thang-ngoc-hoi-dong-da-bai-hoc-doi-ngoai-cua-hoang-de-quang-trung-102179382.htm",
    "20": "https://scov.gov.vn/ban-sac-van-hoa/tu-dien-van-hoa/ngon-ngu-chu-viet.html",
}


def make_images():
    from PIL import Image, ImageDraw, ImageFont
    FOLDER.mkdir(parents=True, exist_ok=True)
    font_path = next((p for p in [Path("/System/Library/Fonts/Supplemental/Arial.ttf"),
                               Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")] if p.exists()), None)
    if font_path is None:
        raise RuntimeError("Install a Unicode TrueType font to render Vietnamese diagrams")
    def font(size): return ImageFont.truetype(str(font_path), size)
    bg, panel, ink, dim = "#10182b", "#1b2942", "#f2f5fc", "#b5c5dd"
    gold, mint, blue, red = "#f8cb75", "#77ddc0", "#82baff", "#fa8e8a"

    def canvas(title, subtitle):
        im = Image.new("RGB", (1200, 675), bg)
        d = ImageDraw.Draw(im)
        d.rounded_rectangle((42, 36, 1158, 639), radius=30, fill=panel)
        d.text((80, 65), title, font=font(42), fill=ink)
        d.text((80, 125), subtitle, font=font(25), fill=dim)
        return im, d

    def text(d, xy, value, size=30, color=ink, anchor="mm"):
        d.text(xy, value, font=font(size), fill=color, anchor=anchor)

    def arrow(d, start, end, color=mint, width=5):
        d.line((start, end), fill=color, width=width)
        angle = math.atan2(end[1]-start[1], end[0]-start[0])
        pts = [end] + [(end[0]-18*math.cos(angle+a), end[1]-18*math.sin(angle+a)) for a in [-0.45, 0.45]]
        d.polygon(pts, fill=color)

    im, d = canvas("Sao Kim: quay chậm đến bất ngờ", "Hai chuyển động, hai khoảng thời gian khác nhau")
    for y, length, color, label, value in [(265, 850, gold, "Tự quay 1 vòng", "243 ngày"),
                                           (440, 787, mint, "Quanh Mặt Trời 1 vòng", "225 ngày")]:
        text(d, (90, y-40), label, 30, anchor="lm")
        d.rounded_rectangle((90, y, 90+length, y+55), 20, fill=color)
        text(d, (90+length, y+85), value, 35, color, anchor="rm")
    text(d, (600, 595), "Đơn vị: ngày Trái Đất • số liệu làm tròn", 24, dim)
    im.save(FOLDER / "venus-answer.png")

    for reveal in [False, True]:
        im, d = canvas("Nhật thực" if reveal else "Điều gì đang xảy ra?",
                       "Mặt Trăng ở giữa • bóng tối chạm Trái Đất" if reveal else "Quan sát vị trí của ba thiên thể và vùng bóng")
        d.polygon([(565, 315), (565, 365), (943, 340)], fill="#070b14")
        d.polygon([(565, 315), (565, 365), (943, 455), (943, 225)], fill="#334158")
        d.polygon([(565, 315), (565, 365), (943, 340)], fill="#070b14")
        for x, radius, color, label in [(210, 115, gold, "Mặt Trời"), (565, 35, dim, "Mặt Trăng"), (945, 90, blue, "Trái Đất")]:
            d.ellipse((x-radius, 340-radius, x+radius, 340+radius), fill=color)
            text(d, (x, 510), label, 32)
        d.ellipse((855, 326, 876, 354), fill="#263650")
        if reveal:
            arrow(d, (755, 440), (862, 350), red)
            text(d, (690, 465), "Vùng thấy nhật thực", 26, red)
        text(d, (600, 592), "Sơ đồ minh họa • kích thước và khoảng cách không theo tỷ lệ", 23, dim)
        im.save(FOLDER / ("eclipse-answer.png" if reveal else "eclipse-question.png"))

    im, d = canvas("Kênh đào Suez", "Tuyến đường thủy nối hai biển qua Ai Cập")
    for rect, label in [((110, 215, 490, 475), "Địa Trung Hải"), ((710, 215, 1090, 475), "Biển Đỏ")]:
        d.rounded_rectangle(rect, radius=30, fill="#244d72")
        text(d, ((rect[0]+rect[2])/2, 345), label, 39)
    arrow(d, (500, 345), (697, 345), gold, 10)
    text(d, (600, 275), "SUEZ", 32, gold)
    text(d, (600, 545), "Suez ≠ Panama", 36, mint)
    text(d, (600, 592), "Sơ đồ kết nối • không phải bản đồ địa lý", 24, dim)
    im.save(FOLDER / "suez-answer.png")

    im, d = canvas("Sa mạc không nhất thiết phải nóng", "Điểm chung: lượng giáng thủy thấp")
    d.polygon([(85, 465), (300, 215), (470, 465)], fill="#e3edf9")
    d.polygon([(110, 465), (300, 270), (430, 465)], fill="#b3d3f0")
    d.polygon([(675, 465), (790, 320), (1120, 465)], fill=gold)
    d.polygon([(675, 465), (930, 270), (1120, 465)], fill="#ddb16b")
    text(d, (300, 520), "Nam Cực", 39, blue)
    text(d, (300, 575), "Lớn nhất nếu tính sa mạc lạnh", 25, dim)
    text(d, (885, 520), "Sahara", 39, gold)
    text(d, (885, 575), "Sa mạc nóng lớn nhất", 25, dim)
    im.save(FOLDER / "desert-answer.png")

    im, d = canvas("Nhận diện quốc kỳ", "Hãy chú ý hình dáng, màu viền và các biểu tượng")
    d.line((445, 205, 445, 587), fill=dim, width=9)
    outer = [(460, 192), (790, 390), (605, 390), (790, 570), (460, 570)]
    inner = [(474, 217), (741, 377), (570, 377), (755, 555), (474, 555)]
    d.polygon(outer, fill="#254b9b")
    d.polygon(inner, fill="#dc2446")
    d.ellipse((502, 290, 571, 350), fill=ink)
    d.ellipse((498, 276, 575, 328), fill="#dc2446")
    def star(cx, cy, outer_r, inner_r, tips):
        return [(cx+(outer_r if i%2 == 0 else inner_r)*math.cos(-math.pi/2+i*math.pi/tips),
                 cy+(outer_r if i%2 == 0 else inner_r)*math.sin(-math.pi/2+i*math.pi/tips)) for i in range(tips*2)]
    d.polygon(star(537, 334, 21, 13, 8), fill=ink)
    d.polygon(star(538, 470, 38, 25, 12), fill=ink)
    text(d, (600, 610), "Minh họa nhận diện • không phải bản dựng theo kích thước pháp định", 21, dim)
    im.save(FOLDER / "nepal-question.png")

    im, d = canvas("Hai nguyên tố lỏng ở 20°C", "Áp suất khí quyển bình thường")
    for x, symbol, name, note, color in [(335, "Br", "Brom", "Phi kim", red), (865, "Hg", "Thủy ngân", "Kim loại", blue)]:
        d.rounded_rectangle((x-180, 210, x+180, 570), 25, outline=color, width=4)
        text(d, (x, 320), symbol, 100, color)
        text(d, (x, 425), name, 39)
        text(d, (x, 510), note, 28, dim)
    im.save(FOLDER / "elements-answer.png")

    im, d = canvas("Bốn cột mốc, đúng thứ tự", "Từ chuyến đi qua Đại Tây Dương tới bước chân trên Mặt Trăng")
    d.line((150, 350, 1050, 350), fill=mint, width=7)
    for x, year, lines in [(160, "1492", ["Columbus", "tới châu Mỹ"]), (450, "1789", ["Cách mạng", "Pháp"]),
                           (740, "1914", ["Thế chiến I", "bắt đầu"]), (1030, "1969", ["Apollo 11", "lên Mặt Trăng"])]:
        d.ellipse((x-14, 336, x+14, 364), fill=gold)
        text(d, (x, 285), year, 46, gold)
        for i, line in enumerate(lines): text(d, (x, 420+i*42), line, 28)
    im.save(FOLDER / "timeline-answer.png")

    im, d = canvas("Bốn hành tinh đá gần Mặt Trời", "Xếp theo khoảng cách trung bình tăng dần")
    arrow(d, (140, 510), (1090, 510), mint)
    for x, size, color, label in [(165, 66, gold, "Mặt Trời"), (400, 24, dim, "Sao Thủy"),
                                (615, 43, "#ddae74", "Sao Kim"), (825, 45, blue, "Trái Đất"), (1040, 32, red, "Sao Hỏa")]:
        d.ellipse((x-size, 330-size, x+size, 330+size), fill=color)
        text(d, (x, 435), label, 29)
    text(d, (600, 585), "Kích thước và khoảng cách trong hình không theo tỷ lệ", 24, dim)
    im.save(FOLDER / "planets-answer.png")


def prepare():
    make_images()
    bank = questions()
    assert len(bank) == 20
    for q in bank:
        assert 0 < len(q["content"]) <= 500 and 0 < len(q["explanation"]) <= 500
        assert all(0 < len(o["text"]) <= 120 for o in q["options"])
        for field in ["localQuestionImage", "localExplanationImage"]:
            if q[field]: assert (FOLDER / q[field]).is_file()
    api.save_json(FOLDER / "questions.json", dict(title=TITLE, description=DESCRIPTION, categoryId=4, shuffleQuestions=False, questions=bank))
    api.save_json(FOLDER / "sources.json", dict(reviewedAt="2026-11-02", questions=SOURCES, illustrations="Original diagrams generated by scripts/seed-curiosity-quiz.py; factual sources listed per question."))
    lines = [f"# {TITLE}", "", DESCRIPTION, "", "Đáp án bên dưới; nên chơi trong app trước khi đọc.", ""]
    for i, q in enumerate(bank, 1):
        lines += [f"## {i}. {q['content']}", ""]
        if q["localQuestionImage"]: lines += [f"![Ảnh câu hỏi]({q['localQuestionImage']})", ""]
        lines += [f"- {o['id']}. {o['text']}" for o in q["options"]]
        key, kind = q["answerKey"], q["questionType"]
        choices = {o["id"]: o["text"] for o in q["options"]}
        if kind == "SINGLE_CHOICE": answer = f"{key}. {choices[key]}"
        elif kind == "MULTIPLE_CHOICE": answer = "; ".join(f"{k}. {choices[k]}" for k in key)
        elif kind == "TRUE_FALSE": answer = "Đúng" if key else "Sai"
        elif kind == "REORDER": answer = " → ".join(choices[k] for k in key)
        elif kind == "RANGE": answer = str(key["acceptedMin"])
        else: answer = key[0]
        lines += ["", f"**Đáp án:** {answer}", "", q["explanation"], ""]
        if q["localExplanationImage"]: lines += [f"![Giải thích]({q['localExplanationImage']})", ""]
    (FOLDER / "README.md").write_text("\n".join(lines), encoding="utf-8")
    return bank


def publish(bank, host, port):
    filenames = sorted({q[f] for q in bank for f in ["localQuestionImage", "localExplanationImage"] if q[f]})
    digest = hashlib.sha256((FOLDER / "questions.json").read_bytes() + b"".join((FOLDER / f).read_bytes() for f in filenames)).hexdigest()
    state_path = FOLDER / f"import-{host}-{port}.json"
    state = json.loads(state_path.read_text()) if state_path.exists() else dict(manifestSha256=digest, media={}, quizId=None)
    if state["manifestSha256"] != digest:
        raise ValueError("Content changed since last import; review the existing quiz before republishing")
    credentials_path = ROOT / f"data/backups/generated-quiz-accounts/{host}-{port}.json"
    fresh = not credentials_path.exists()
    credentials = json.loads(credentials_path.read_text()) if not fresh else dict(username="quizbank_"+secrets.token_hex(5), password=secrets.token_urlsafe(30))
    if fresh: api.save_json(credentials_path, credentials, private=True)
    client = api.Client(host, port)
    try:
        if fresh:
            client.request("REGISTER", dict(**credentials, displayName="Ngân hàng câu hỏi", avatarId="avatar-01"), "REGISTER_RESULT")
        state["ownerUserId"] = client.request("LOGIN", credentials, "LOGIN_RESULT")["profile"]["userId"]
        for filename in filenames:
            if filename in state["media"]: continue
            raw = (FOLDER / filename).read_bytes()
            started = client.request("MEDIA_UPLOAD_START", dict(mimeType="image/png", byteSize=len(raw), sha256=hashlib.sha256(raw).hexdigest()), "MEDIA_UPLOAD_STARTED")
            for offset in range(0, len(raw), 24*1024):
                chunk = raw[offset:offset+24*1024]
                ack = client.request("MEDIA_UPLOAD_CHUNK", dict(transferId=started["transferId"], offset=offset, data=base64.b64encode(chunk).decode()), "MEDIA_UPLOAD_ACK")
                assert ack["nextOffset"] == offset+len(chunk)
            state["media"][filename] = ack["mediaId"]
            api.save_json(state_path, state)
        if state["quizId"] is None:
            state["quizId"] = client.author("CREATE")["quizId"]
            api.save_json(state_path, state)
        quiz_id = state["quizId"]
        stored = []
        for q in bank:
            item = {k: v for k, v in q.items() if not k.startswith("local")}
            for local, target in [("localQuestionImage", "questionAssetId"), ("localExplanationImage", "explanationAssetId")]:
                if q[local]: item[target] = state["media"][q[local]]
            stored.append(item)
        if not state.get("verified"):
            client.author("SAVE_META", quiz_id, dict(title=TITLE, description=DESCRIPTION, categoryId=4, shuffleQuestions=False))
            for i, q in enumerate(stored): client.author("SAVE_QUESTION", quiz_id, dict(index=i, question=q))
        for i, q in enumerate(stored):
            assert client.author("GET_QUESTION", quiz_id, dict(index=i))["question"] == q, f"Read-back mismatch at question {i+1}"
        if client.author("GET", quiz_id)["status"] != "PUBLISHED": client.author("PUBLISH", quiz_id)
        detail = client.request("QUIZ_DETAIL_REQUEST", dict(quizId=quiz_id), "QUIZ_DETAIL")
        assert detail["quiz"]["totalRounds"] == 20 and detail["quiz"]["availability"] == "AVAILABLE"
        for filename, media_id in state["media"].items():
            received = bytearray()
            while True:
                part = client.request("MEDIA_REQUEST", dict(mediaId=media_id, offset=len(received)), "MEDIA_CHUNK")
                received.extend(base64.b64decode(part["data"]))
                if len(received) == part["totalSize"]: break
            assert received == (FOLDER / filename).read_bytes(), filename
        visible, page = set(), 1
        while True:
            listing = client.request("QUIZ_LIST_REQUEST", dict(categoryId=None, page=page, pageSize=10), "QUIZ_LIST")
            visible.update(q["quizId"] for q in listing["items"])
            if page*10 >= listing["totalItems"]: break
            page += 1
        assert quiz_id in visible
        state.update(verified=True, title=TITLE, questionCount=20, imageCount=len(filenames), types=dict(Counter(q["questionType"] for q in stored)), detail=detail, lobbyVerified=True, mediaDownloadVerified=True)
        api.save_json(state_path, state)
        client.request("LOGOUT", {}, "LOGOUT_ACK")
        print(f"Published and read back quiz #{quiz_id} on {host}:{port}: 20 questions, {len(filenames)} images; lobby visible.")
    finally:
        client.sock.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=5555)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    bank = prepare()
    print(f"Prepared {TITLE}: {len(bank)} questions, {len(list(FOLDER.glob('*.png')))} images.", flush=True)
    if args.apply: publish(bank, args.host, args.port)


if __name__ == "__main__":
    main()
