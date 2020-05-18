package klib

class AnnotationList implements Iterable<Annotation> {
  def a = []

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
        tags: it[5].split(','),
        sentenceId: it[6],
        text: it[7]
      )
    }

    new AnnotationList(a: ans)
  }
}
