package UDP;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

public class StudentClient {
    private static final int SERVER_PORT = 2209;
    private static final int REQUEST_ID_LENGTH = 8;

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Cách chạy: java -cp out UDP.StudentClient "
                    + "<serverHost> <studentCode> <qCode>");
            return;
        }

        InetAddress serverAddress = InetAddress.getByName(args[0]);
        String studentCode = args[1];
        String questionCode = args[2];

        // Socket tự đóng khi kết thúc, kể cả khi có lỗi.
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(10000);

            // a. Gửi chuỗi ;studentCode;qCode.
            byte[] request = (";" + studentCode + ";" + questionCode)
                    .getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(request, request.length,
                    serverAddress, SERVER_PORT));

            // b. Nhận 8 byte requestId và đối tượng Student được serialize.
            byte[] buffer = new byte[65507];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);
            if (response.getLength() <= REQUEST_ID_LENGTH) {
                throw new IllegalArgumentException("Gói tin không chứa đối tượng Student");
            }

            Student student;
            try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(
                    response.getData(), response.getOffset() + REQUEST_ID_LENGTH,
                    response.getLength() - REQUEST_ID_LENGTH))) {
                student = (Student) input.readObject();
            }

            // c. Chỉ cập nhật name và email; giữ nguyên id, code.
            student.name = normalizeName(student.name);
            student.email = createEmail(student.name);

            // Giữ nguyên 8 byte requestId, đặt đối tượng ngay phía sau.
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(response.getData(), response.getOffset(), REQUEST_ID_LENGTH);
            try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
                output.writeObject(student);
            }
            byte[] result = bytes.toByteArray();
            socket.send(new DatagramPacket(result, result.length,
                    response.getAddress(), response.getPort()));

            System.out.println("Đã gửi: " + student.name + " | " + student.email);
        }
    }

    private static String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Tên sinh viên không được để trống");
        }
        String[] words = name.trim().toLowerCase(Locale.ROOT).split("\\s+");
        for (int i = 0; i < words.length; i++) {
            words[i] = words[i].substring(0, 1).toUpperCase(Locale.ROOT)
                    + words[i].substring(1);
        }
        return String.join(" ", words);
    }

    private static String createEmail(String name) {
        // Bỏ dấu khi tên có tiếng Việt, ví dụ Đặng -> dang.
        String plainName = Normalizer.normalize(name.toLowerCase(Locale.ROOT),
                Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace('đ', 'd');
        String[] words = plainName.split("\\s+");
        StringBuilder email = new StringBuilder(words[words.length - 1]);
        for (int i = 0; i < words.length - 1; i++) {
            email.append(words[i].charAt(0));
        }
        return email.append("@ptit.edu.vn").toString();
    }
}
