#!/usr/bin/env python3
"""Create and publish light general-knowledge quizzes using the shared importer."""

import importlib.util
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("quiz_seed", ROOT / "scripts/seed-demo-quizzes.py")
seed = importlib.util.module_from_spec(spec)
spec.loader.exec_module(seed)
FOLDER = ROOT / "data/generated-quizzes/fun-bank-v1"
single, multi, boolean = seed.single, seed.multi, seed.boolean
short, reorder, number, pinpoint = seed.short, seed.reorder, seed.number, seed.pinpoint


def packs():
    return [dict(key="everyday", categoryId=4, title="Tạp hóa kiến thức — Chơi là biết", questions=[
        single("Nếu Doraemon đi mua bánh khoái khẩu, cậu ấy sẽ chọn món nào?",
               ["Bánh chưng", "Bánh rán dorayaki", "Bánh mì tỏi", "Bánh tráng trộn"], "B", "Món khoái khẩu quen thuộc của Doraemon là bánh rán dorayaki."),
        single("Trong truyện cổ tích, cô Tấm đánh rơi thứ gì để nhà vua tìm ra mình?",
               ["Điện thoại", "Chiếc nón", "Chiếc giày", "Chìa khóa xe"], "C", "Chiếc giày đánh rơi là chi tiết giúp nhà vua tìm được Tấm."),
        multi("Đi chợ mua nguyên liệu gói bánh chưng truyền thống: chọn những món phù hợp.",
              ["Gạo nếp", "Đậu xanh", "Thịt lợn", "Mì spaghetti"], ["A", "B", "C"], "Bánh chưng truyền thống có gạo nếp, đậu xanh và thịt lợn; spaghetti để dành cho bữa khác."),
        multi("Chọn những nhạc cụ có dây trong danh sách này.", ["Đàn guitar", "Trống", "Đàn violin", "Sáo trúc"],
              ["A", "C"], "Guitar và violin tạo âm bằng dây; trống và sáo thuộc các nhóm nhạc cụ khác."),
        boolean("Một ki-lô-gam bông nhẹ hơn một ki-lô-gam sắt.", False, "Cả hai đều có khối lượng 1 kg; bông chỉ chiếm nhiều chỗ hơn."),
        boolean("Ngày 29/2 chỉ xuất hiện trong năm nhuận theo lịch Gregory.", True, "Năm thường có tháng Hai 28 ngày; năm nhuận có thêm ngày 29/2."),
        short("Điền từ còn thiếu: 'Có công mài sắt, có ngày nên ...'.", ["kim", "cái kim", "cai kim"], "Câu tục ngữ khuyên kiên trì: Có công mài sắt, có ngày nên kim."),
        short("Trong truyện cổ tích, ai có câu thần chú 'Khắc nhập, khắc nhập'? (Nêu nhân vật, không nêu tên truyện.)",
              ["anh Khoai", "Khoai", "anh khoai", "anh nông dân", "anh nong dan", "chàng trai", "chang trai"], "Trong Cây tre trăm đốt, anh Khoai được Bụt chỉ cho câu thần chú để ghép các đốt tre."),
        reorder("Đi từ sáng đến đêm: xếp các thời điểm theo thứ tự trong một ngày.",
                ["Bình minh", "Buổi trưa", "Hoàng hôn", "Nửa đêm"], "Bình minh mở đầu ban ngày, tiếp đến trưa, hoàng hôn và nửa đêm."),
        reorder("Xếp các ngày lễ theo thứ tự xuất hiện trong cùng một năm dương lịch.",
                ["Tết Dương lịch 1/1", "Quốc tế Phụ nữ 8/3", "Quốc tế Thiếu nhi 1/6", "Giáng sinh 25/12"], "Theo thứ tự tháng: tháng 1, tháng 3, tháng 6, tháng 12."),
        number("Bạn mua 3 ly trà sữa, mỗi ly 25 nghìn đồng. Tổng cộng bao nhiêu nghìn đồng, chưa tính phí giao hàng?",
               0, 150, 75, "nghìn đồng", "3 × 25 = 75 nghìn đồng. Chưa cần gọi hội bạn chia tiền!"),
        number("Một năm thường có 365 ngày. Vậy có bao nhiêu tháng có ÍT NHẤT 28 ngày?",
               0, 12, 12, "tháng", "Cả 12 tháng đều có ít nhất 28 ngày; câu hỏi không nói 'đúng 28 ngày'."),
        pinpoint("Ghim vào phần ruột miếng dưa hấu trong quầy trái cây.", "fruit-stall.png", (650, 420, 90, 35),
                 "Miếng dưa hấu ở ô dưới bên phải, ruột đỏ và vỏ xanh."),
        pinpoint("Mặt Trời mọc ở hướng Đông. Trên la bàn này, ghim vào chữ chỉ hướng đó.", "compass.png", (720, 260, 150, 80),
                 "Đông nằm bên phải khi la bàn đặt Bắc ở trên.")]),
        dict(key="animals", categoryId=4, title="Muôn thú có gì lạ?", questions=[
        single("Mang tên 'cá' nhưng con nào dưới đây là động vật có vú?",
               ["Cá chép", "Cá ngừ", "Cá heo", "Cá vàng"], "C", "Cá heo là động vật có vú, thở bằng phổi và nuôi con bằng sữa."),
        single("Loài nào là động vật có vú có khả năng bay chủ động thực sự?",
               ["Dơi", "Chim sẻ", "Sóc bay", "Bướm"], "A", "Dơi là động vật có vú biết bay chủ động. Sóc bay chỉ lượn nhờ màng da."),
        multi("Chọn các loài thuộc nhóm côn trùng.", ["Bướm", "Nhện", "Ong", "Kiến"], ["A", "C", "D"],
              "Bướm, ong và kiến là côn trùng; nhện thuộc nhóm hình nhện."),
        multi("Chọn các loài chim trong danh sách. Đừng để chuyện có bay được hay không đánh lừa bạn!",
              ["Chim cánh cụt", "Dơi", "Đà điểu", "Vịt"], ["A", "C", "D"], "Chim cánh cụt, đà điểu và vịt đều là chim; dơi là động vật có vú."),
        boolean("Cá heo phải ngoi lên mặt nước để hít thở không khí.", True, "Cá heo thở không khí bằng phổi qua lỗ thở; chúng không có mang như cá."),
        boolean("Nhện trưởng thành bình thường có sáu chân giống côn trùng.", False, "Nhện có tám chân; côn trùng trưởng thành có sáu chân."),
        short("Con gì thường được gọi là 'chúa sơn lâm' trong cách nói quen thuộc ở Việt Nam?",
              ["hổ", "ho", "con hổ", "con ho", "cọp", "cop", "con cọp", "con cop"], "Trong cách nói quen thuộc ở Việt Nam, hổ (cọp) được gọi là chúa sơn lâm."),
        short("Nhắc tới chiếc túi trước bụng để nuôi con, người ta thường nghĩ ngay tới con gì? (Tên quen thuộc ở Úc.)",
              ["kangaroo", "kanguru", "kangourou", "chuột túi", "chuot tui", "con kangaroo"], "Kangaroo, thường gọi là chuột túi, có túi để con non phát triển."),
        reorder("Một chú bướm lớn lên ra sao? Xếp vòng đời theo đúng thứ tự.",
                ["Trứng", "Sâu bướm", "Nhộng", "Bướm trưởng thành"], "Bướm biến thái hoàn toàn qua trứng, ấu trùng (sâu bướm), nhộng rồi trưởng thành."),
        reorder("Xếp quá trình lớn lên của ếch theo thứ tự.",
                ["Trứng ếch", "Nòng nọc chưa có chân", "Ếch con còn đuôi", "Ếch trưởng thành"], "Ếch đi từ trứng đến nòng nọc, mọc chân và tiêu giảm đuôi trước khi trưởng thành."),
        number("Một con bạch tuộc bình thường có bao nhiêu cánh tay?", 0, 12, 8, "cánh tay", "Bạch tuộc có tám cánh tay, không phải mười như cách dễ nhầm với mực."),
        number("Bạch tuộc có tới bao nhiêu trái tim?", 0, 10, 3, "trái tim", "Bạch tuộc có ba tim: hai tim bơm máu qua mang và một tim bơm máu đi khắp cơ thể."),
        pinpoint("Tai dài, hay gắn với hình ảnh cà rốt: ghim vào mặt bạn thỏ.", "animal-friends.png", (620, 155, 150, 115),
                 "Bạn thỏ có đôi tai dài ở ô trên bên phải."),
        pinpoint("Ai có tám chân? Ghim vào thân con nhện trong hình.", "tiny-creatures.png", (260, 195, 60, 50),
                 "Con nhện nằm ở ô trên bên trái; tám chân tỏa ra quanh thân.")]),
        dict(key="riddles", categoryId=4, title="Đố vui — Dễ mà dễ lú", questions=[
        single("Bạn đang chạy đua và vượt người đứng thứ hai. Ngay lúc đó bạn đứng thứ mấy?",
               ["Thứ nhất", "Thứ hai", "Thứ ba", "Ban tổ chức"], "B", "Bạn chiếm vị trí của người vừa vượt, tức vị trí thứ hai."),
        single("Bác sĩ đưa 3 viên kẹo, dặn ăn ngay 1 viên rồi cứ 30 phút ăn thêm 1 viên. Sau bao lâu bạn ăn hết?",
               ["30 phút", "60 phút", "90 phút", "Đợi hết phim"], "B", "Ăn viên đầu ở phút 0, viên hai ở phút 30 và viên ba ở phút 60."),
        multi("Chọn những thứ có 'răng' theo cách gọi tiếng Việt nhưng không nhai cơm.",
              ["Cái lược", "Cái cưa", "Con mèo", "Bánh răng"], ["A", "B", "D"], "Lược, cưa và bánh răng đều có răng theo tên gọi, nhưng không dùng để nhai."),
        multi("Chọn những thứ có 'mắt' theo cách gọi tiếng Việt nhưng không dùng để nhìn.",
              ["Cây kim", "Quả dứa", "Con cú", "Lưới đánh cá"], ["A", "B", "D"], "Mắt kim, mắt dứa và mắt lưới là tên gọi hình dạng hoặc bộ phận; chúng không có chức năng nhìn."),
        boolean("Nếu hôm qua là thứ Hai thì ngày mai là thứ Tư.", True, "Hôm qua thứ Hai nghĩa là hôm nay thứ Ba; ngày mai là thứ Tư."),
        boolean("Một gia đình có 4 chị em gái. Mỗi người đều có cùng 1 em trai. Gia đình đó có tổng cộng 8 người con.",
                False, "Bốn chị em cùng có một em trai, nên tổng cộng là 5 người con, không phải mỗi chị một em trai riêng."),
        short("Cái gì càng lau khô đồ vật thì bản thân càng ướt? (Vật dụng bằng vải.)",
              ["khăn", "khan", "cái khăn", "cai khan", "khăn lau", "khan lau", "khăn tắm", "khan tam"], "Khăn thấm nước từ vật được lau nên bản thân khăn càng ướt."),
        short("Cái gì có cổ nhưng không có đầu, dùng để đựng nước?",
              ["chai", "cái chai", "cai chai", "chai nước", "chai nuoc", "lọ", "lo", "cái lọ", "cai lo", "bình", "binh", "cái bình", "cai binh"], "Chai, lọ hoặc bình có phần được gọi là cổ, nhưng không có đầu."),
        reorder("Từ bé đến lớn: xếp các đơn vị thời gian theo độ dài tăng dần.",
                ["Giây", "Phút", "Giờ", "Ngày"], "60 giây = 1 phút; 60 phút = 1 giờ; 24 giờ = 1 ngày."),
        reorder("Xếp các nhân vật theo tuổi tăng dần. Lưu ý: đọc đúng số tuổi nhé!",
                ["Bé Na: 5 tuổi", "Anh Bo: 12 tuổi", "Mẹ: 36 tuổi", "Ông: 70 tuổi"], "5 < 12 < 36 < 70. Tên gọi chỉ để tạo bối cảnh; số tuổi mới quyết định thứ tự."),
        number("Trên cành có 10 con chim. 3 con bay đi, không có con nào khác bay đi hay bay đến. Còn mấy con trên cành?",
               0, 15, 7, "con", "Đề nói rõ không có thay đổi nào khác, nên còn 10 - 3 = 7 con."),
        number("Bạn có 5 quả táo và lấy ra 2 quả để cầm trên tay. Trên tay bạn đang có mấy quả?",
               0, 10, 2, "quả", "Câu hỏi hỏi số quả đang cầm trên tay: 2, chứ không hỏi số quả còn lại."),
        pinpoint("Trong đám hình tròn, có một ngôi sao đi lạc. Ghim vào giữa ngôi sao đó.", "odd-star.png", (715, 345, 40, 40),
                 "Ngôi sao nằm ở hàng thứ hai, cột ngoài cùng bên phải."),
        pinpoint("Kim dài chỉ 12, kim ngắn chỉ 3: ghim vào tâm chiếc đồng hồ đang chỉ 3 giờ đúng.", "clock-party.png", (665, 190, 60, 60),
                 "Đồng hồ ở ô trên bên phải chỉ 3 giờ: kim phút ở 12 và kim giờ ở 3.")])]


def make_images():
    from PIL import Image, ImageDraw, ImageFont
    FOLDER.mkdir(parents=True, exist_ok=True)
    path = "/System/Library/Fonts/Supplemental/Arial.ttf"
    font = ImageFont.truetype(path, 32)
    title_font = ImageFont.truetype(path, 40)
    ink = "#28304d"
    centers = [(290, 220), (695, 220), (290, 435), (695, 435)]

    def canvas(title, cards=True):
        im = Image.new("RGB", (1000, 600), "#fff9ef")
        d = ImageDraw.Draw(im)
        d.text((50, 30), title, font=title_font, fill=ink)
        if cards:
            for x, y in centers:
                d.rounded_rectangle((x-165, y-95, x+165, y+95), 25, fill="#eef1ff", outline="#d9ddf4", width=3)
        return im, d

    def eye(d, x, y, r=5):
        d.ellipse((x-r, y-r, x+r, y+r), fill=ink)

    im, d = canvas("Quầy trái cây vui vẻ")
    d.ellipse((220, 170, 300, 275), fill="#ed6576", outline=ink, width=3)
    d.ellipse((270, 170, 350, 275), fill="#ed6576", outline=ink, width=3)
    d.line((285, 175, 290, 140), fill="#855538", width=7)
    d.ellipse((293, 142, 330, 158), fill="#6bb58c")
    # A banana, grape cluster and watermelon wedge provide distinct silhouettes.
    d.arc((605, 125, 785, 270), 5, 170, fill="#e7ac28", width=33)
    for x, y in [(265, 389), (295, 389), (325, 389), (280, 415), (310, 415), (295, 442)]:
        d.ellipse((x-18, y-18, x+18, y+18), fill="#9571c8", outline=ink, width=2)
    d.pieslice((595, 320, 795, 500), 0, 180, fill="#69b586", outline=ink, width=3)
    d.pieslice((610, 329, 780, 485), 0, 180, fill="#f07680")
    for x, y in [(650, 433), (695, 450), (741, 433)]:
        d.ellipse((x-3, y-5, x+3, y+5), fill=ink)
    im.save(FOLDER / "fruit-stall.png")

    im, d = canvas("La bàn: Bắc ở trên", False)
    d.ellipse((300, 100, 700, 500), fill="#edf0fd", outline="#818bc4", width=5)
    d.polygon([(500, 130), (545, 300), (500, 470), (455, 300)], fill="#eb7385", outline=ink)
    d.polygon([(330, 300), (500, 255), (670, 300), (500, 345)], fill="#9fbbdf", outline=ink)
    for text, xy in [("BẮC", (500, 100)), ("ĐÔNG", (795, 300)), ("NAM", (500, 535)), ("TÂY", (200, 300))]:
        d.text(xy, text, font=font, fill=ink, anchor="mm")
    im.save(FOLDER / "compass.png")

    im, d = canvas("Hội bạn tai xinh")
    # Cat upper left, rabbit upper right, bear lower left, pig lower right.
    for index, (x, y) in enumerate(centers):
        color = ["#eab778", "#fafcff", "#c59a7f", "#efaabb"][index]
        if index == 0:
            d.polygon([(x-70, y-15), (x-70, y-75), (x-20, y-45)], fill=color, outline=ink)
            d.polygon([(x+70, y-15), (x+70, y-75), (x+20, y-45)], fill=color, outline=ink)
        elif index == 1:
            for dx in [-35, 35]:
                d.ellipse((x+dx-16, y-110, x+dx+16, y-18), fill=color, outline=ink, width=3)
                d.ellipse((x+dx-8, y-98, x+dx+8, y-30), fill="#efaabb")
        else:
            for dx in [-56, 56]:
                d.ellipse((x+dx-23, y-66, x+dx+23, y-20), fill=color, outline=ink, width=3)
        d.ellipse((x-75, y-48, x+75, y+55), fill=color, outline=ink, width=3)
        eye(d, x-28, y-8); eye(d, x+28, y-8)
        if index == 3:
            d.ellipse((x-30, y+8, x+30, y+40), fill="#e57f9c", outline=ink)
            eye(d, x-10, y+24, 3); eye(d, x+10, y+24, 3)
        else:
            d.polygon([(x-8, y+12), (x+8, y+12), (x, y+22)], fill=ink)
            d.arc((x-18, y+13, x+18, y+40), 0, 180, fill=ink, width=2)
    im.save(FOLDER / "animal-friends.png")

    im, d = canvas("Biệt đội bé xíu")
    x, y = centers[0]
    for sign in [-1, 1]:
        for k in range(4):
            d.line((x+sign*30, y-20+k*15, x+sign*(70+k*4), y-65+k*38, x+sign*95, y-60+k*40), fill=ink, width=5)
    d.ellipse((x-42, y-40, x+42, y+45), fill="#8a88b3", outline=ink, width=3)
    eye(d, x-13, y-12); eye(d, x+13, y-12)
    x, y = centers[1]
    for dx in [-45, 45]:
        d.ellipse((x+dx-40, y-65, x+dx+40, y+5), fill="#c1a4df", outline=ink, width=3)
        d.ellipse((x+dx-35, y-5, x+dx+35, y+60), fill="#e4a7c3", outline=ink, width=3)
    d.ellipse((x-12, y-52, x+12, y+55), fill=ink)
    x, y = centers[2]
    for dx in [-25, 25]:
        d.ellipse((x+dx-25, y-65, x+dx+25, y-10), fill="#cde7ef", outline=ink, width=2)
    d.ellipse((x-70, y-25, x+70, y+35), fill="#f1c55c", outline=ink, width=3)
    for dx in [-28, 0, 28]: d.line((x+dx, y-20, x+dx, y+30), fill=ink, width=9)
    x, y = centers[3]
    d.ellipse((x-60, y-55, x+60, y+55), fill="#e97777", outline=ink, width=3)
    d.line((x, y-53, x, y+53), fill=ink, width=3)
    d.ellipse((x-25, y-75, x+25, y-35), fill=ink)
    for dx, dy in [(-28, -18), (28, -18), (-32, 22), (32, 22)]: eye(d, x+dx, y+dy, 9)
    im.save(FOLDER / "tiny-creatures.png")

    im, d = canvas("Tìm ngôi sao đi lạc", False)
    for row in range(3):
        for col in range(4):
            x, y = 225+col*170, 205+row*160
            if (row, col) == (1, 3):
                points=[]
                for i in range(10):
                    angle=-math.pi/2+i*math.pi/5; r=64 if i%2==0 else 29
                    points.append((x+r*math.cos(angle), y+r*math.sin(angle)))
                d.polygon(points, fill="#f3c45d", outline=ink, width=3)
            else:
                d.ellipse((x-50, y-50, x+50, y+50), fill="#b4c9ef", outline=ink, width=3)
    im.save(FOLDER / "odd-star.png")

    im, d = canvas("Đồng hồ nào đúng giờ?")
    for (x, y), hour in zip(centers, [9, 3, 6, 12]):
        d.ellipse((x-80, y-80, x+80, y+80), fill="#fffdf6", outline=ink, width=4)
        for label, dx, dy in [("12", 0, -60), ("3", 61, 0), ("6", 0, 61), ("9", -61, 0)]:
            d.text((x+dx, y+dy), label, font=ImageFont.truetype(path, 19), fill=ink, anchor="mm")
        angle=hour*math.pi/6-math.pi/2
        d.line((x, y, x+42*math.cos(angle), y+42*math.sin(angle)), fill="#df6f83", width=9)
        d.line((x, y, x, y-53), fill=ink, width=5)
        eye(d, x, y, 6)
    im.save(FOLDER / "clock-party.png")

    sources = {
        "octopus": "https://www.nhm.ac.uk/discover/octopuses-keep-surprising-us-here-are-eight-examples-how.html",
        "bats": "https://www.si.edu/spotlight/bats/batfacts",
        "dolphins": "https://oceantoday.noaa.gov/dolphinanatomy/",
    }
    (FOLDER / "sources.json").write_text(json.dumps(sources, ensure_ascii=False, indent=2)+"\n")


if __name__ == "__main__":
    seed.FOLDER = FOLDER
    seed.packs = packs
    seed.make_images = make_images
    seed.main()
