package com.meituan.flutter_logan;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealSendLogRunnableTest {
  @Test
  public void acceptsLegacySuccessResponse() throws Exception {
    assertTrue(RealSendLogRunnable.isSuccessfulResponse("{\"success\":true}"));
  }

  @Test
  public void acceptsLoganServerCodeResponse() throws Exception {
    assertTrue(RealSendLogRunnable.isSuccessfulResponse("{\"code\":200,\"data\":\"/logan/downing\"}"));
  }

  @Test
  public void rejectsFailedResponse() throws Exception {
    assertFalse(RealSendLogRunnable.isSuccessfulResponse("{\"code\":500,\"success\":false}"));
  }
}
