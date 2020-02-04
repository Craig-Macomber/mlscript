package simplesub

import org.scalatest._
import fastparse._
import Parser.expr
import fastparse.Parsed.Failure
import fastparse.Parsed.Success

class TypingTests extends FunSuite {
  
  def doTest(str: String, expected: String = ""): Unit = {
    if (expected.isEmpty) println(s">>> $str")
    val Success(term, index) = parse(str, expr(_), verboseFailures = true)
    
    val typing = new Typing
    val tyv = typing.inferType(term)
    
    // println("inferred: " + tyv)
    // println(" where " + tyv.showBounds)
    
    val ty = typing.expandType(tyv, true)
    if (expected.isEmpty) println("T " + ty.show)
    val res = ty.normalize.show
    if (expected.isEmpty) println("N " + res)
    
    val res2 = typing.expandPosType(tyv, true).show
    if (expected.nonEmpty)
      assert(res2 =:= expected)
    else {
      println(res2)
      println("---")
    }
    
    ()
  }
  def error(str: String, msg: String): Unit = {
    assert(intercept[TypeError](doTest(str, "<none>")).msg =:= msg)
    ()
  }
  
  test("basic") {
    doTest("42", "Int")
    doTest("fun x -> 42", "⊤ -> Int")
    doTest("fun x -> x", "'a -> 'a")
    doTest("fun x -> x 42", "(Int -> 'a) -> 'a")
    doTest("(fun x -> x) 42", "Int")
    doTest("fun f -> fun x -> f (f x)  // twice", "('a ∨ 'b -> 'b ∧ 'c) -> 'a -> 'c")
  }
  
  test("booleans") {
    doTest("true", "Bool")
    doTest("not true", "Bool")
    doTest("fun x -> not x", "Bool -> Bool")
    doTest("(fun x -> not x) true", "Bool")
    doTest("fun x -> fun y -> fun z -> if x then y else z",
      "Bool -> 'a -> 'a -> 'a")
    doTest("fun x -> fun y -> if x then y else x",
      "'a ∧ Bool -> 'a -> 'a")
    
    error("succ true",
      "cannot constrain Bool <: Int")
    error("fun x -> succ (not x)",
      "cannot constrain Bool <: Int")
    error("(fun x -> not x.f) { f = 123 }",
      "cannot constrain Int <: Bool")
    error("(fun f -> fun x -> not (f x.u)) false",
      "cannot constrain Bool <: 'a -> 'b")
  }
  
  test("records") {
    doTest("fun x -> x.f", "{f: 'a} -> 'a")
    doTest("{}", "⊤")
    doTest("{ f = 42 }", "{f: Int}")
    doTest("{ f = 42 }.f", "Int")
    doTest("(fun x -> x.f) { f = 42 }", "Int")
    doTest("if true then { a = 1; b = true } else { b = false; c = 42 }", "{b: Bool}")
    
    error("{ a = 123; b = true }.c",
      "missing field: c in {a: Int, b: Bool}")
    error("fun x -> { a = x }.b",
      "missing field: b in {a: 'a}")
  }
  
  test("self-app") {
    doTest("fun x -> x x", "(a -> 'b) as a -> 'b")
    doTest("fun x -> x x x", "(a -> a -> 'b) as a -> 'b")
    doTest("fun x -> fun y -> x y x", "('b -> a -> 'c) as a -> 'b -> 'c")
    doTest("fun x -> fun y -> x x y", "(a -> 'b -> 'c) as a -> 'b -> 'c")
  }
  
  test("let-poly") {
    doTest("let f = fun x -> x in {a = f 0; b = f true}", "{a: Int, b: Bool}")
    doTest("fun y -> let f = fun x -> x in {a = f y; b = f true}",
      "'a -> {a: 'a, b: Bool}")
    doTest("fun y -> let f = fun x -> y x in {a = f 0; b = f true}",
      "(Int ∨ Bool -> 'a ∧ 'b) -> {a: 'a, b: 'b}")
    doTest("fun y -> let f = fun x -> x y in {a = f (fun z -> z); b = f (fun z -> true)}",
      "'a -> {a: 'a, b: Bool}")
    doTest("fun y -> let f = fun x -> x y in {a = f (fun z -> z); b = f (fun z -> succ z)}",
      "'a ∧ Int -> {a: 'a, b: Int}")
  }
  
  test("recursion") {
    doTest("let rec f = fun x -> f x.u in f",
      "{u: a} as a -> ⊥")
    
    // from https://www.cl.cam.ac.uk/~sd601/mlsub/
    doTest("let rec recursive_monster = fun x -> { thing = x; self = recursive_monster x } in recursive_monster",
      "'a -> {self: {self: b, thing: 'a} as b, thing: 'a}")
  }
  
}
