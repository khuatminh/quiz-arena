import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.lang.reflect.Field;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Server cục bộ để kiểm tra toàn bộ quá trình trao đổi UDP. */
public class StudentClientTest {
    public static void main(String[] args) throws Exception {
        Class<?> studentClass;
        Class<?> clientClass;
        try {
            studentClass = Class.forName("UDP.Student");
            clientClass = Class.forName("UDP.StudentClient");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Chưa có lớp UDP.Student hoặc UDP.StudentClient", e);
        }
        check(ObjectStreamClass.lookup(studentClass).getSerialVersionUID() == 20171107L,
                "Sai serialVersionUID");
        Object codeOnly = studentClass.getConstructor(String.class).newInstance("B15DCCN001");
        check("B15DCCN001".equals(field(studentClass, codeOnly, "code")),
                "Constructor một tham số phải gán code");

        runCase(studentClass, clientClass, "  nGUYEN\t van   TUAN nam  ",
                "Nguyen Van Tuan Nam", "namnvt@ptit.edu.vn");
        runCase(studentClass, clientClass, "nAM", "Nam", "nam@ptit.edu.vn");
        runCase(studentClass, clientClass, "ĐẶNG văn NAM", "Đặng Văn Nam", "namdv@ptit.edu.vn");
        System.out.println("PASS: 3 trường hợp trao đổi UDP, serialization và requestId.");
    }

    private static void runCase(Class<?> studentClass, Class<?> clientClass,
                                String inputName, String expectedName, String expectedEmail)
            throws Exception {
        InetAddress address = InetAddress.getByName("127.0.0.1");
        try (DatagramSocket server = new DatagramSocket(2209, address)) {
            server.setSoTimeout(5000);
            FutureTask<Void> client = new FutureTask<>(() -> {
                clientClass.getMethod("main", String[].class).invoke(null,
                        (Object) new String[]{"127.0.0.1", "B15DCCN001", "EE29C059"});
                return null;
            });
            Thread thread = new Thread(client, "student-client-test");
            thread.setDaemon(true);
            thread.start();

            DatagramPacket initial = new DatagramPacket(new byte[65507], 65507);
            server.receive(initial);
            String message = new String(initial.getData(), initial.getOffset(),
                    initial.getLength(), StandardCharsets.UTF_8);
            check(";B15DCCN001;EE29C059".equals(message), "Sai thông điệp mở đầu");

            byte[] requestId = "REQ00001".getBytes(StandardCharsets.US_ASCII);
            Object original = studentClass
                    .getConstructor(String.class, String.class, String.class, String.class)
                    .newInstance("student-id-123", null, inputName, null);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(requestId);
            try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
                output.writeObject(original);
            }
            byte[] payload = bytes.toByteArray();
            server.send(new DatagramPacket(payload, payload.length,
                    initial.getAddress(), initial.getPort()));

            DatagramPacket reply = new DatagramPacket(new byte[65507], 65507);
            server.receive(reply);
            check(reply.getLength() > 8, "Thiếu đối tượng trong gói trả lời");
            check(Arrays.equals(requestId, Arrays.copyOfRange(reply.getData(),
                    reply.getOffset(), reply.getOffset() + 8)), "requestId bị thay đổi");
            try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(
                    reply.getData(), reply.getOffset() + 8, reply.getLength() - 8))) {
                Object updated = input.readObject();
                check(studentClass.isInstance(updated), "Sai kiểu đối tượng trả về");
                check(expectedName.equals(field(studentClass, updated, "name")), "Sai name");
                check(expectedEmail.equals(field(studentClass, updated, "email")), "Sai email");
                check("student-id-123".equals(field(studentClass, updated, "id")), "id bị thay đổi");
                check(field(studentClass, updated, "code") == null, "code bị thay đổi");
            }
            client.get(5, TimeUnit.SECONDS);
        }
    }

    private static Object field(Class<?> type, Object object, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
