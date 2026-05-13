import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class LineCounter {

    /**
     * 统计指定文本文件的总行数
     *
     * @param filePath 文件的路径 (可以是绝对路径或相对路径)
     * @return 文件的总行数。如果发生异常则返回 -1
     */
    public static long countTextFileLines(String filePath) {
        Path path = Paths.get(filePath);

        // 使用 try-with-resources 确保流被正确关闭
        // Files.lines 方法在内部使用了 BufferedReader，效率较高且适用于大文件处理
        try (Stream<String> lines = Files.lines(path)) {
            return lines.count();
        } catch (IOException e) {
            System.err.println("读取文件时发生错误: " + e.getMessage());
            e.printStackTrace();
            return -1; /* 表示获取行数失败 */
        }
    }

    public static void main(String[] args) {
        // 测试示例
        String testFilePath = "C:\\Users\\damn\\Downloads\\0406_009 (2).txt"; // 请替换为实际的 .txt 文件路径

        long lineCount = countTextFileLines(testFilePath);

        if (lineCount >= 0) {
            System.out.println("文件 '" + testFilePath + "' 一共有: " + lineCount + " 行。");
        } else {
            System.out.println("无法统计文件行数。");
        }
    }
}
