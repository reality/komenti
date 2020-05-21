package komenti

import spock.lang.Specification
import spock.lang.Shared

import klib.*

class AppTest extends Specification {
  @Shared buffer
  @Shared labelFile

  def setupSpec() {
    buffer = new ByteArrayOutputStream()
    System.out = new PrintStream(buffer)
    labelFile = "labels.txt"
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
      def q = ["query", "-q", "'part of' some 'apoptotic process'", "-o", "GO", "--out", labelFile]
      App.main(toArg(q))
    then:
      buffer.toString() =~ "Saved \\d+ labels for \\d+ terms to $labelFile"
    then:
      new File(labelFile).exists()
  }

  def "check_query_output"() {
    true
  }

  def "annotate"() {
    def outFile = 'annotation_file.txt'
    def textFile = 'to_annotate.txt'

    when:
      def q = ["annotate", "-l", labelFile, "-t", textFile, "--out", outFile, "--threads", "1"]
      App.main(toArg(q))
    then:
      buffer.toString() =~ "Annotation complete"
    then:
      new File(outFile).exists()
  }

  def "check_annotate_output"() {
    true
  }
}
