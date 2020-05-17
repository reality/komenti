package klib

class AnnotationLoader {
  static def loadFile(fileName) {
    new File(fileName).text.split('\n').collect {
      it = it.split('\t')
      new Annotation(
        documentId: it[0],
        conceptIri: it[1],
        conceptLabel: it[2].replaceAll('\\\\',''),
        matchedText: it[3],
        group: it[4],
        tags: it[5].split(','),
        sentenceId: it[6],
        text: it[7]
      )
    }
  }
}
