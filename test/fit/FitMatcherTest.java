// Modified or written by Object Mentor, Inc. for inclusion with FitNesse.
// Copyright (c) 2002 Cunningham & Cunningham, Inc.
// Released under the terms of the GNU General Public License version 2 or later.
package fit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FitMatcherTest {

  private void assertMatch(String expression, Number parameter) throws Exception {
    FitMatcher matcher = new FitMatcher(expression, parameter);
    assertTrue(matcher.matches());
  }

  private void assertNoMatch(String expression, Number parameter) throws Exception {
    FitMatcher matcher = new FitMatcher(expression, parameter);
    assertFalse(matcher.matches());
  }

  private void assertException(String expression, Object parameter) {
    FitMatcher matcher = new FitMatcher(expression, parameter);
    try {
      matcher.matches();
    }
    catch (Exception e) {
    }
  }

  @Test
  public void testSimpleMatches() throws Exception {
    assertMatch("_<3", Integer.valueOf(2));
    assertNoMatch("_<3", Integer.valueOf(3));
    assertMatch("_<4", Integer.valueOf(3));
    assertMatch("_ < 9", Integer.valueOf(4));
    assertMatch("<3", Integer.valueOf(2));
    assertMatch(">4", Integer.valueOf(5));
    assertMatch(">-3", Integer.valueOf(-2));
    assertMatch("<3.2", Double.valueOf(3.1));
    assertNoMatch("<3.2", Double.valueOf(3.3));
    assertMatch("<=3", Double.valueOf(3));
    assertMatch("<=3", Double.valueOf(2));
    assertNoMatch("<=3", Double.valueOf(4));
    assertMatch(">=2", Double.valueOf(2));
    assertMatch(">=2", Double.valueOf(3));
    assertNoMatch(">=2", Double.valueOf(1));
  }

  @Test
  public void testExceptions() throws Exception {
    assertException("X", Integer.valueOf(1));
    assertException("<32", "xxx");
  }

  @Test
  public void testMessage() throws Exception {
    FitMatcher matcher = new FitMatcher("_>25", Integer.valueOf(3));
    assertEquals("<b>3</b>>25", matcher.message());
    matcher = new FitMatcher(" < 32", Integer.valueOf(5));
    assertEquals("<b>5</b> < 32", matcher.message());
  }

  @Test
  public void testTrichotomy() throws Exception {
    assertMatch("5<_<32", Integer.valueOf(8));
    assertNoMatch("5<_<32", Integer.valueOf(5));
    assertNoMatch("5<_<32", Integer.valueOf(32));
    assertMatch("10>_>5", Integer.valueOf(6));
    assertNoMatch("10>_>5", Integer.valueOf(10));
    assertNoMatch("10>_>5", Integer.valueOf(5));
    assertMatch("10>=_>=5", Integer.valueOf(10));
    assertMatch("10>=_>=5", Integer.valueOf(5));
  }

}
