package klib

import groovy.transform.MapConstructor

@MapConstructor
class AnnotationList implements Iterable<Annotation> {
  List<Annotation> a = []
  def writeMode
  def annPath
  def outWriter

  // This constructor rather than the MapConstructor when you want write Mode
  AnnotationList(annPath, writeMode) {
    this.writeMode = writeMode
    this.annPath = annPath
    if(writeMode) {
      outWriter = new BufferedWriter(new FileWriter(annPath))
    }
  }

  def add(List<Annotation> ans) {
    ans.each { an ->
      a << an
      if(writeMode) {
        outWriter.write(an.toString() + '\n')
        if((a.size() % 500) == 0) { outWriter.flush() }
      }
    } 
  }

  def finishWrite() {
    outWriter.flush()
    outWriter.close()
    writeMode = false
  }

  def byGroup(g) {
    a.findAll { it.group == g }
  }

  def byDocument(d) {
    a.findAll { it.documentId == d }
  }

  def bySentence(s) {
    a.findAll { it.sentenceId == s }
  }

  @Override
  Iterator<Annotation> iterator() {
    a.iterator()
  }

  static def loadFile(fileName) {
    def ans = new File(fileName).text.split('\n').collect {
      it = it.split('\t')
      new Annotation(
        documentId: it[0],
        termIri: it[1],
        conceptLabel: it[2].replaceAll('\\\\',''),
        matchedText: it[3],
        group: it[4],
        tags: it[5] != "" ? it[5].split(',') : [],
        sentenceId: it[6],
        text: it[7]
      )
    }

    new AnnotationList(a: ans)
  }
}
