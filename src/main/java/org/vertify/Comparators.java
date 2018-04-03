package org.vertify;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * 文件比较器
 * 
 * @author Yukai
 *
 */
public class Comparators {
    public static boolean isFileAreEqual(File originFile, File targetFile) throws IOException {
        if (!originFile.isFile() || !targetFile.isFile()) {
            return false;
        }
        if (originFile.length() != targetFile.length()) {
            return false;
        }

        try (FileInputStream originStream = new FileInputStream(originFile);
                FileInputStream targetStream = new FileInputStream(targetFile)) {
            final int bufSize = 128 * 1024;// 128k
            byte[] oriBuf = new byte[bufSize];
            byte[] tarBuf = new byte[bufSize];
            while (true) {
                int oriSize = originStream.read(oriBuf);
                int tarSize = targetStream.read(tarBuf);
                if (oriSize == -1 && tarSize == -1) {
                    break;
                }
                if (oriSize != tarSize || !isBytesAreEqual(oriBuf, tarBuf)) {
                    return false;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw e;
        }

        return true;
    }

    private static boolean isBytesAreEqual(byte[] ori, byte[] tar) {
        if (ori.length != tar.length) {
            return false;
        }
        for (int i = 0; i < ori.length; i++) {
            if (ori[i] != tar[i]) {
                return false;
            }
        }
        return true;
    }
    
    public static void isDirsAreEqual(File oneFile, File otherFile) throws IOException {
        checkDirsAreEqual(oneFile, otherFile);
        checkDirsAreEqual(otherFile, oneFile);
    }

    public static void checkDirsAreEqual(File oneFile, File otherFile) throws IOException {
        Path one = oneFile.toPath();
        Path other = otherFile.toPath();
        Files.walkFileTree(one, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (".git".equals(dir.toFile().getName())) {// 排除.git目录
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return super.preVisitDirectory(dir, attrs);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                FileVisitResult result = super.visitFile(file, attrs);

                // get the relative file name from path "one"
                Path relativize = one.relativize(file);
                // construct the path for the counterpart file in "other"
                Path fileInOther = other.resolve(relativize);

                /* 文件过大可能造成内存溢出 */
                // byte[] otherBytes = Files.readAllBytes(fileInOther);
                // byte[] thisBytes = Files.readAllBytes(file);
                // if (!Arrays.equals(otherBytes, thisBytes)) {
                // throw new AssertionFailedError(file + " is not equal to " +
                // fileInOther);
                // }
                if (!isFileAreEqual(file.toFile(), fileInOther.toFile())) {
                    throw new IOException(file + " is not equal to " + fileInOther);
                }
                return result;
            }
        });
    }

    public static void main(String[] args) {
        String one = "C:\\Users\\Yukai\\Desktop\\gitlab-ce";
        String other = "C:\\Users\\Yukai\\Desktop\\gitlab-ee\\gitlab-ce";
        File file1 = new File(one);
        File file2 = new File(other);

        try {
            isDirsAreEqual(file1, file2);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
