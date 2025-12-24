// Copyright (C) 2003-2009 by Object Mentor, Inc. All rights reserved.
// Released under the terms of the CPL Common Public License version 1.0.
package util;

import fitnesse.util.VertxWorkerPool;
import fitnesse.vertx.VertxFutures;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileUtil {

  private static final Logger LOG = Logger.getLogger(FileUtil.class.getName());
  private static final FileSystem FILE_SYSTEM = VertxWorkerPool.vertx().fileSystem();
  private static final long DEFAULT_IO_TIMEOUT_MS = 30000L;

  public static final String CHARENCODING = StandardCharsets.UTF_8.name();

  public static File createFile(String path, String content) throws IOException {
    return createFile(path, new ByteArrayInputStream(content.getBytes()));
  }

  public static File createFile(String path, InputStream content) throws IOException {
    File file = new File(path);
    ensureParentExists(file);
    return createFile(file, content);
  }

  public static File createFile(File file, String content) throws IOException {
    return createFile(file, content.getBytes(StandardCharsets.UTF_8));
  }


  public static File createFile(File file, byte[] bytes) throws IOException {
    return createFile(file, new ByteArrayInputStream(bytes));
  }

  public static File createFile(File file, InputStream content) throws IOException {
    ensureParentExists(file);
    byte[] bytes = readAllBytes(content);
    awaitVoid(FILE_SYSTEM.writeFile(file.getPath(), Buffer.buffer(bytes)));
    return file;
  }

  public static boolean makeDir(String path) {
    try {
      awaitVoid(FILE_SYSTEM.mkdirs(path));
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  public static void deleteFileSystemDirectory(String dirPath) throws IOException {
    deleteFileSystemDirectory(new File(dirPath));
  }

  public static void deleteFileSystemDirectory(File current) throws IOException {
    String path = current.getPath();
    if (!exists(path)) {
      return;
    }
    awaitVoid(FILE_SYSTEM.deleteRecursive(path));
  }

  public static void deleteFile(String filename) throws IOException {
    deleteFile(new File(filename));
  }

  public static void deleteFile(File file) throws IOException {
    String path = file.getPath();
    if (!exists(path)) {
      return;
    }
    awaitVoid(FILE_SYSTEM.delete(path));
  }

  public static String getFileContent(String path) throws IOException {
    File input = new File(path);
    return getFileContent(input);
  }

  public static String getFileContent(File input) throws IOException {
    Buffer buffer = await(FILE_SYSTEM.readFile(input.getPath()));
    return buffer.toString(StandardCharsets.UTF_8);
  }

  public static byte[] getFileBytes(File input) throws IOException {
    Buffer buffer = await(FILE_SYSTEM.readFile(input.getPath()));
    return buffer.getBytes();
  }

  public static List<String> getFileLines(File file) throws IOException {
    Buffer buffer = await(FILE_SYSTEM.readFile(file.getPath()));
    String content = buffer.toString(Charset.defaultCharset());
    List<String> results = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
      String line;
      while ((line = reader.readLine()) != null) {
        results.add(line);
      }
    }
    return results;
  }

  public static void writeLinesToFile(File file, List<String> lines) throws IOException {
    ensureParentExists(file);
    String separator = System.lineSeparator();
    StringBuilder content = new StringBuilder();
    for (int i = 0; i < lines.size(); i++) {
      if (i > 0) {
        content.append(separator);
      }
      content.append(lines.get(i));
    }
    awaitVoid(FILE_SYSTEM.writeFile(file.getPath(), Buffer.buffer(content.toString(), Charset.defaultCharset().name())));
  }

  public static void copyBytes(InputStream input, OutputStream output) throws IOException {
    StreamReader reader = new StreamReader(input);
    while (!reader.isEof())
      output.write(reader.readBytes(1000));
  }

  public static String toString(InputStream input) {
    try (Scanner s = new Scanner(input, CHARENCODING)) {
      s.useDelimiter("\\A");
      return s.hasNext() ? s.next() : "";
    }
  }

  public static File createDir(String path) {
    makeDir(path);
    return new File(path);
  }

  public static File[] getDirectoryListing(File dir) {
    File[] files = listFiles(dir);
    if (files.length > 0) {
      SortedSet<File> dirSet = new TreeSet<>();
      SortedSet<File> fileSet = new TreeSet<>();
      for (File file : files) {
        if (file.isDirectory())
          dirSet.add(file);
        else
          fileSet.add(file);
      }
      List<File> fileList = new ArrayList<>(files.length);
      fileList.addAll(dirSet);
      fileList.addAll(fileSet);
      files = fileList.toArray(new File[0]);
    }
    return files;
  }

  public static File[] listFiles(File dir) {
    return listFiles(dir, p -> true);
  }

  public static File[] listFiles(File dir, DirectoryStream.Filter<Path> visitPredicate) {
    try {
      if (!isDirectory(dir.getPath())) {
        return new File[0];
      }
    } catch (IOException e) {
      return new File[0];
    }
    List<File> fileList = new ArrayList<>();
    try {
      List<String> entries = await(FILE_SYSTEM.readDir(dir.getPath()));
      for (String entry : entries) {
        Path path = Paths.get(entry);
        try {
          File file = new File(dir, path.getFileName().toString());
          if (visitPredicate.accept(file.toPath())) {
            fileList.add(file);
          }
        } catch (IOException e) {
          // ignore filter failures
        }
      }
    } catch (Exception e) {
      // not expected, ignore
    }
    return fileList.toArray(new File[0]);
  }

  public static boolean isEmpty(File directory) throws IOException {
    if (!exists(directory.getPath())) {
      return true;
    }
    List<String> entries = await(FILE_SYSTEM.readDir(directory.getPath()));
    return entries.isEmpty();
  }

  public static void close(Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (IOException e) {
        LOG.log(Level.INFO, "Unable to close " + closeable, e);
      }
    }
  }

  private static void ensureParentExists(File file) throws IOException {
    File parent = file.getParentFile();
    if (parent != null) {
      awaitVoid(FILE_SYSTEM.mkdirs(parent.getPath()));
    }
  }

  private static byte[] readAllBytes(InputStream input) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    copyBytes(input, output);
    return output.toByteArray();
  }

  private static boolean exists(String path) throws IOException {
    return Boolean.TRUE.equals(await(FILE_SYSTEM.exists(path)));
  }

  private static boolean isDirectory(String path) throws IOException {
    if (!exists(path)) {
      return false;
    }
    return await(FILE_SYSTEM.props(path)).isDirectory();
  }

  private static <T> T await(Future<T> future) throws IOException {
    try {
      return VertxFutures.await(future, DEFAULT_IO_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      if (e instanceof IOException) {
        throw (IOException) e;
      }
      throw new IOException("File operation failed", e);
    }
  }

  private static void awaitVoid(Future<Void> future) throws IOException {
    await(future);
  }
}
