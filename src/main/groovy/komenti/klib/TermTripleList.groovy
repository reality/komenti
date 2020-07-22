package klib

import groovy.json.*
import groovy.transform.MapConstructor

@MapConstructor
class TermTripleList implements Iterable<TermTriple> {
  List<TermTriple> a = []
  def annPath
  def writeMode

  TermTripleList(annPath, writeMode) {
    this.writeMode = writeMode
    this.annPath = annPath
  }

  @Override
  Iterator<Annotation> iterator() {
    a.iterator()
  }
  
  def add(List<TermTriple> ans) {
    ans.each { an ->
      add(an)
    } 
  }

  def add(TermTriple an) {
    // kind of inefficient; for concurrency purposes
    if(!a.asImmutable().any { it.toString() == an.toString() }) {
      a << an
      if(writeMode) {
        //if((a.size() % 500) == 0) { write() }
        //write()
      }
    }
  }

  def write() {
    new File(annPath).text = new JsonBuilder(a).toPrettyString()
    new File("string_$annPath".replace('json','txt')).text = a.collect { it.toString() }.join('\n')
  }

  def finishWrite() { write() }

  static def loadFile(fileName) {
    def processTerm // done this way to support recursion
    // TODO currently ignoring the originalAnnotation, will just involve some more casting
    processTerm = { t -> // this is kind of a pain, i don't really get why it can't do it iself
      Annotation a 
      if(t.originalAnnotation) {
        a = new Annotation(t.originalAnnotation)
      }
      if(t.parentTerm) {
        t.parentTerm = processTerm(t.parentTerm)
        new Term(t.parentTerm, t.iri, t.specificLabel, a)
      } else {
        new Term(t.iri, t.specificLabel, a)
      }
    }
    def ans = new JsonSlurper().parse(new File(fileName)).collect {
      new TermTriple(
        subject: processTerm(it.subject),
        relation: processTerm(it.relation),
        object: processTerm(it.object),
      )
    }

    new TermTripleList(a: ans, annPath: fileName)
  }
}
