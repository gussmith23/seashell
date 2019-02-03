package fuselang

import Utils._
import Syntax._
import org.scalatest.FunSuite

class SimpleTypePositive extends FunSuite {

  test("Adding sized int to static int") {
    typeCheck("decl x: bit<64>; let y = 1; x + y;")
  }

  test("Adding floats") {
    typeCheck("decl f: float; let y = 1.5; f + y")
  }

  // XXX(rachit): @adrian check Subtyping behavior
  test("Subtyping for reassign") {
    // Assignment to static ints loses information
    val e1 = typeCheck("let x = 1; x := 2;")
    assert(e1("x").typ === TSizedInt(32), "assiging static := static")

    val e2 = typeCheck("decl y: bit<64>; let x = 1; x := y;")
    assert(e2("x").typ === TSizedInt(64), "assigning static := dynamic")

    val e3 = typeCheck("decl x: bit<32>; decl y: bit<16>; x := y")
    assert(e3("x").typ === TSizedInt(32), "assigning dynamic := dynamic")
    assert(e3("y").typ === TSizedInt(16), "assigning dynamic := dynamic")
  }

  // XXX(rachit): @adrian check Subtyping behavior
  test("Binary operations") {
    val e1 = typeCheck("let x = 1; let y = 2; let z = x + y;")
    assert(e1("z").typ === TStaticInt(3))

    val e2 = typeCheck("decl z: bit<32>; let x = 1; let y = 2; z := x + y;")
    assert(e2("z").typ === TSizedInt(32))

    val e3 = typeCheck("decl x: bit<32>; decl y: bit<16>; let z = x + y")
    assert(e3("z").typ === TSizedInt(32))
  }

  test("Static index into array") {
    val e1 = typeCheck("decl a: bit<64>[10]; let x = a[1];")
    assert(e1("x").typ === TSizedInt(64))
    assert(e1("a").conBanks(0) === Set(0))
  }

  test("for loop") {
    val e1 = typeCheck("""
      decl a: bit<64>[10];
      for (let i = 0..10) {
        let x = a[i]
      }
    """ )
    assert(e1("a").conBanks(0) === Set(0))

    val e2 = typeCheck("""
      decl a: bit<64>[10 bank 5];
      for (let i = 0..10) unroll 5 {
        let x = a[i]
      }
    """ )
    assert(e2("a").conBanks(0) === 0.until(5).toSet)
  }

  test("refresh banks") {
    typeCheck("""
      decl a: bit<64>[10 bank 5];
      for (let i = 0..10) unroll 5 {
        let x = a[i];
        ---
        let y = a[i];
      }
    """ )
  }

}

class FileTypePositive extends FunSuite {

  import java.nio.file.{Files, Paths}
  import collection.JavaConverters._

  val shouldCompile = Paths.get("src/test/resources/should-compile")

  for (file <- Files.newDirectoryStream(shouldCompile).asScala) {
    test(file.toString) {
      val prog = new String(Files.readAllBytes(file))
      typeCheck(prog)
    }
  }

}