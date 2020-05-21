package komenti

import spock.lang.Specification
import spock.lang.Shared

import klib.*

class AppTest extends Specification {
  @Shared buffer

  def setupSpec() {
    buffer = new ByteArrayOutputStream()
    System.out = new PrintStream(buffer)
  }

  def toArg(q) { // thanks i hate it
    q.toArray(new String[0])
  }

  def "startup"() {
    when:
      App.main("--help")
    then: 
      buffer.toString().indexOf("usage: komenti <command> [<options>]") != -1
  }

  def "query"() {
    when:
      def fName = "labels.txt"
      def q = ["query", "-q", "'part of' some 'apoptotic process'", "-o", "GO", "--out", fName]
      App.main(toArg(q))
    then:
      buffer.toString() =~ "Saved \\d+ labels for \\d+ terms to $fName"
    then:
      new File(fName).exists()
  }
}
