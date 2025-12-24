// Copyright (C) 2003-2009 by Object Mentor, Inc. All rights reserved.
// Released under the terms of the CPL Common Public License version 1.0.
package fitnesse.components;

import fitnesse.util.VertxWorkerPool;
import fitnesse.vertx.VertxFutures;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import util.FileUtil;

public class ContentBuffer {
  private static final long DEFAULT_IO_TIMEOUT_MS = 30000L;
  private static final FileSystem FILE_SYSTEM = VertxWorkerPool.vertx().fileSystem();
  private static final OpenOptions APPEND_OPTIONS = new OpenOptions().setCreate(true).setWrite(true).setAppend(true);

  private File tempFile;
  private final OutputStream outputStream;
  private int size = 0;

  public ContentBuffer() throws IOException {
    this(".tmp");
  }

  public ContentBuffer(String ext) throws IOException {
    tempFile = File.createTempFile("FitNesse-", ext);
    tempFile.deleteOnExit();
    outputStream = new ContentBufferOutputStream();
  }

  public ContentBuffer append(String value) throws IOException {
    byte[] bytes = value.getBytes(FileUtil.CHARENCODING);
    return append(bytes);
  }

  public ContentBuffer append(byte[] bytes) throws IOException {
    size += bytes.length;
    appendToFile(bytes);
    return this;
  }

  public String getContent() throws IOException {
    return FileUtil.getFileContent(tempFile);
  }

  public int getSize() {
    //close();
    return size;
  }

  public InputStream getInputStream() throws IOException {
    byte[] bytes = FileUtil.getFileBytes(tempFile);
    return new ByteArrayInputStream(bytes) {
      @Override
      public void close() throws IOException {
        try {
          super.close();
        } finally {
          delete();
        }
      }
    };
  }

  public InputStream getNonDeleteingInputStream() throws IOException {
    byte[] bytes = FileUtil.getFileBytes(tempFile);
    return new ByteArrayInputStream(bytes);
  }

  public OutputStream getOutputStream() {
    return outputStream;
  }

  protected File getFile() {
    return tempFile;
  }

  public void delete() {
    try {
      FileUtil.deleteFile(tempFile);
    } catch (IOException e) {
      // Best-effort cleanup.
    }
  }

  private void appendToFile(byte[] bytes) throws IOException {
    Buffer buffer = Buffer.buffer(bytes);
    awaitVoid(FILE_SYSTEM.open(tempFile.getPath(), APPEND_OPTIONS)
      .compose(file -> file.write(buffer).onComplete(ar -> file.close())));
  }

  private static <T> T await(Future<T> future) throws IOException {
    try {
      return VertxFutures.await(future, DEFAULT_IO_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      if (e instanceof IOException) {
        throw (IOException) e;
      }
      throw new IOException("Content buffer operation failed", e);
    }
  }

  private static void awaitVoid(Future<Void> future) throws IOException {
    await(future);
  }

  private final class ContentBufferOutputStream extends OutputStream {
    @Override
    public void write(int b) throws IOException {
      appendToFile(new byte[] {(byte) b});
      size += 1;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      byte[] slice = new byte[len];
      System.arraycopy(b, off, slice, 0, len);
      append(slice);
    }
  }
}
