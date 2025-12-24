// Copyright (C) 2003-2009 by Object Mentor, Inc. All rights reserved.
// Released under the terms of the CPL Common Public License version 1.0.
package fitnesse.fixtures;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;

import fit.Fixture;
import fitnesse.authentication.Authenticator;
import fitnesse.responders.editing.SaveRecorder;
import fitnesse.socketservice.PlainServerSocketFactory;
import fitnesse.testutil.FitNesseUtil;
import util.FileUtil;

import static fitnesse.fixtures.FitnesseFixtureContext.context;

public class SetUp extends Fixture {
  public SetUp() throws Exception {
    this(new Properties());
  }

  public SetUp(String configuration) throws Exception {
    this(asProperties(configuration));
  }

  private static final int DEFAULT_INTERNAL_PORT = 9123;

  private SetUp(Properties properties) throws Exception {
    final int port = selectInternalPort();
    properties.setProperty("FITNESSE_PORT", String.valueOf(port));
    System.setProperty("INTERNAL_PORT", String.valueOf(port));
    context = FitNesseUtil.makeTestContext(port, new Authenticator() {
      @Override public boolean isAuthenticated(String username, String password) {
        return FitnesseFixtureContext.authenticator == null || FitnesseFixtureContext.authenticator.isAuthenticated(username, password);
      }
    }, properties);
    File historyDirectory = context.getTestHistoryDirectory();
    if (historyDirectory.exists())
      FileUtil.deleteFileSystemDirectory(historyDirectory);
    historyDirectory.mkdirs();
    SaveRecorder.clear();
    FitNesseUtil.startFitnesseWithContext(context);
  }

  private static int selectInternalPort() {
    if (isPortAvailable(DEFAULT_INTERNAL_PORT)) {
      return DEFAULT_INTERNAL_PORT;
    }
    int fallbackPort = findFreePort();
    return fallbackPort > 0 ? fallbackPort : DEFAULT_INTERNAL_PORT;
  }

  private static boolean isPortAvailable(int port) {
    try (ServerSocket socket = new PlainServerSocketFactory().createServerSocket(port)) {
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private static int findFreePort() {
    try (ServerSocket socket = new PlainServerSocketFactory().createServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      return -1;
    }
  }

  private static Properties asProperties(String configuration) throws Exception {
    Properties properties = new Properties();
    properties.load(new ByteArrayInputStream(configuration.getBytes(FileUtil.CHARENCODING)));
    return properties;
  }
}
