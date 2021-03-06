/*
 * Copyright 2011 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.j2objc.gen;

import com.google.devtools.j2objc.GenerationTest;
import com.google.devtools.j2objc.Options;
import com.google.devtools.j2objc.util.ErrorUtil;
import com.google.devtools.j2objc.util.NameTable;

import java.io.IOException;

/**
 * Tests for {@link ObjectiveCSourceFileGenerator}.
 *
 * @author Tom Ball
 */
public class ObjectiveCSourceFileGeneratorTest extends GenerationTest {

  public void testCamelCaseQualifiedName() {
    String camelCaseName = NameTable.camelCaseQualifiedName("java.lang.Object");
    assertEquals("JavaLangObject", camelCaseName);
    camelCaseName = NameTable.camelCaseQualifiedName("java.util.logging.Level");
    assertEquals("JavaUtilLoggingLevel", camelCaseName);
    camelCaseName = NameTable.camelCaseQualifiedName("java");
    assertEquals("Java", camelCaseName);
    camelCaseName = NameTable.camelCaseQualifiedName("Level");
    assertEquals("Level", camelCaseName);
    camelCaseName = NameTable.camelCaseQualifiedName("");
    assertEquals("", camelCaseName);
  }

  public void testCapitalize() {
    assertEquals("Test", NameTable.capitalize("test"));
    assertEquals("123", NameTable.capitalize("123"));
    assertEquals("", NameTable.capitalize(""));
  }

  public void testJsniDelimiters() throws IOException {
    String source =
        "/*-{ jsni-comment }-*/ " +
        "class Example { " +
        "  native void test1() /*-[ ocni(); ]-*/; " +
        "  native void test2() /*-{ jsni(); }-*/; " +
        "}";

    // First test with defaults, to see if warnings are reported.
    assertTrue(Options.jsniWarnings());
    String translation = translateSourceFile(source, "Example", "Example.h");
    assertWarningCount(2);

    // Verify JSNI method is declared in a native methods category,
    // the ocni method is implemented and the jsni method is not implemented.
    assertTranslation(translation, "@interface Example (NativeMethods)\n- (void)test2");
    translation = getTranslatedFile("Example.m");
    assertTranslation(translation, "ocni();");
    assertNotInTranslation(translation, "jsni();");
    assertNotInTranslation(translation, "jsni-comment;");

    // Now rebuild with warnings disabled.
    Options.setJsniWarnings(false);
    ErrorUtil.reset();
    translation = translateSourceFile(source, "Example", "Example.h");
    assertWarningCount(0);

    // Verify JSNI method is still declared in a native methods category,
    // and implementation wasn't affected.
    assertTranslation(translation, "@interface Example (NativeMethods)\n- (void)test2");
    translation = getTranslatedFile("Example.m");
    assertTranslation(translation, "ocni();");
    assertFalse(translation.contains("jsni();"));
    assertNotInTranslation(translation, "jsni-comment;");
  }

  public void testStaticAccessorsAdded() throws IOException {
    String header = translateSourceFile("class Test {" +
        " private static int foo;" +
        " private static final int finalFoo = 12;" +
        " private static String bar;" +
        " private static final String finalBar = \"test\";" +
        " }", "Test", "Test.h");
    assertTranslation(header, "#define Test_finalFoo 12");
    assertTranslation(header, "J2OBJC_STATIC_FIELD_GETTER(Test, foo_, int)");
    assertTranslation(header, "J2OBJC_STATIC_FIELD_REF_GETTER(Test, foo_, int)");
    assertTranslation(header, "J2OBJC_STATIC_FIELD_GETTER(Test, bar_, NSString *)");
    assertTranslation(header, "J2OBJC_STATIC_FIELD_SETTER(Test, bar_, NSString *)");
    assertTranslation(header, "J2OBJC_STATIC_FIELD_GETTER(Test, finalBar_, NSString *)");
    assertNotInTranslation(header, "J2OBJC_STATIC_FIELD_SETTER(Test, finalBar_, NSString *)");
  }

  public void testStaticReaderAddedWhenSameMethodNameExists() throws IOException {
    String translation = translateSourceFile(
        "class Test { private static int foo; void foo(String s) {}}", "Test", "Test.h");
    assertTranslation(translation, "J2OBJC_STATIC_FIELD_GETTER(Test, foo__, int)");
    assertTranslation(translation, "J2OBJC_STATIC_FIELD_REF_GETTER(Test, foo__, int)");
    assertTranslation(translation, "- (void)fooWithNSString:(NSString *)s;");
  }

  /**
   * Verify that a static reader method is not added to a class that already
   * has one.
   */
  public void testExistingStaticReaderDetected() throws IOException {
    String translation = translateSourceFile(
        "class Test { private static int foo; public static int foo() { return foo; }}", "Test",
        "Test.h");
    assertOccurrences(translation, "+ (int)foo;", 1);
  }

  public void testTypeVariableReturnType() throws IOException {
    String translation = translateSourceFile(
        "interface I<T extends Runnable> { T test(); }", "Test", "Test.h");
    assertTranslation(translation, "- (id)test;");
  }

  public void testOverriddenGenericConstructor() throws IOException {
    addSourceFile("class A<T> { A(T t) {} }", "A.java");
    String translation = translateSourceFile(
        "class B extends A<String> { B(String s) { super(s); } }", "B", "B.h");
    assertTranslation(translation, "- (id)initWithNSString:(NSString *)s;");
    assertNotInTranslation(translation, "initWithId");
  }
}
