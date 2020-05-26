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
      if(!a.any { it.toString() == an.toString() }) {
        a << an
        if(writeMode) {
          //if((a.size() % 500) == 0) { write() }
          //write()
        }
      }
    } 
  }

  def write() {
    new File(annPath).text = new JsonBuilder(a).toPrettyString()
  }

  def finishWrite() { write() }

  static def loadFile(fileName) {
    def ans = new JsonSlurper().parse(new File(fileName)).each {
      new TermTriple(it)
    }

    new TermTripleList(a: ans, annPath: fileName)
  }
}
