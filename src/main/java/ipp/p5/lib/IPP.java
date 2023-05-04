import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SocketIppImagePrint {
    private static byte[] ippVersion() {
        return new byte[]{1, 1};
    }

    private static byte[] ippTag(int tag) {
        return new byte[]{(byte) tag};
    }

    private static byte[] ippAttribute(int tag, String name, String value) {
        byte[] tagName = name.getBytes();
        byte[] tagValue = value.getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(1 + 1 + tagName.length + 1 + tagValue.length);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) tag);
        buffer.put((byte) tagName.length);
        buffer.put(tagName);
        buffer.put((byte) tagValue.length);
        buffer.put(tagValue);
        return buffer.array();
    }

    private static byte[] ippOperationId(int operationId) {
        ByteBuffer buffer = ByteBuffer.allocate(2);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putShort((short) operationId);
        return buffer.array();
    }

    private static byte[] ippRequestId(int requestId) {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(requestId);
        return buffer.array();
    }

    private static void printImageUsingSocketIpp(PImage canvasImage, String printerAddress) {
        printImageUsingSocketIpp(canvasImage, printerAddress, 631);
    }

    private static void printImageUsingSocketIpp(PImage canvasImage, String printerAddress, int printerPort) {
        try {
            // Read image data
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try {
                canvasImage.loadPixels();
                for (int i = 0; i < canvasImage.pixels.length; i++) {
                    int pixel = canvasImage.pixels[i];
                    byte[] pixelBytes = {
                        (byte) ((pixel >> 16) & 0xFF), // Red
                        (byte) ((pixel >> 8) & 0xFF),  // Green
                        (byte) (pixel & 0xFF),         // Blue
                        (byte) ((pixel >> 24) & 0xFF)  // Alpha
                    };
                    byteArrayOutputStream.write(pixelBytes);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            byte[] imageData = byteArrayOutputStream.toByteArray();

            // Build the IPP message
            ByteArrayOutputStream ippMessageStream = new ByteArrayOutputStream();
            ippMessageStream.write(ippVersion());
            ippMessageStream.write(ippOperationId(2));
            ippMessageStream.write(ippRequestId(1));
            ippMessageStream.write(ippAttribute(0x47, "attributes-charset", "utf-8"));
            ippMessageStream.write(ippAttribute(0x48, "attributes-natural-language", "en"));
            ippMessageStream.write(ippAttribute(0x45, "printer-uri", "ipp://" + printerAddress + "/ipp/print"));
            ippMessageStream.write(ippAttribute(0x42, "requesting-user-name", "anonymous"));
            ippMessageStream.write(ippAttribute(0x41, "job-name", imageFile.getName()));
            ippMessageStream.write(ippAttribute(0x44, "document-format", "application/octet-stream"));
            ippMessageStream.write(ippTag(0x03));

            byte[] ippMessage = ippMessageStream.toByteArray();

            // Connect to the printer
            Socket sock = new Socket(printerAddress, printerPort);

            // Send the HTTP POST request
            String httpRequest = "POST /ipp/print HTTP/1.1\r\n" +
                                    "Host: " + printerAddress + "\r\n" +
                                    "Content-Type: application/ipp\r\n" +
                                    "Content-Length: " + (ippMessage.length + imageData.length) + "\r\n";
            OutputStream outputStream = sock.getOutputStream();
            outputStream.write(httpRequest.getBytes());
            outputStream.write(ippMessage);
            outputStream.write(imageData);

            // Receive the HTTP response
            BufferedReader reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            String response = reader.readLine();

            if (!response.startsWith("HTTP/1.1 200 OK")) {
                System.out.println("Error: The IPP server responded with a non-200 status code.");
                System.exit(1);
            }

            System.out.println("Image sent to the printer successfully.");
            sock.close();

            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                System.exit(1);
            }
    }
}