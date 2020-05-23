package klib

import groovy.json.*
import groovy.transform.MapConstructor

@MapConstructor
class AnnotationTripleList implements Iterable<AnnotationTriple> {
  List<AnnotationTriple> a = []
  def annPath
  def writeMode

  AnnotationTripleList(annPath, writeMode) {
    this.writeMode = writeMode
    this.annPath = annPath
  }

  @Override
  Iterator<Annotation> iterator() {
    a.iterator()
  }
  
  def add(List<AnnotationTriple> ans) {
    ans.each { an ->
      if(!a.any { 
          it.subject.conceptLabel == an.subject.conceptLabel && 
          it.relation.conceptLabel == an.relation.conceptLabel && 
          it.object.conceptLabel == an.object.conceptLabel }) {
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
      new AnnotationTriple(it)
    }

    new AnnotationTripleList(a: ans, annPath: fileName)
  }
}
