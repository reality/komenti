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
        tags: it[4].split(','),
        sentenceId: it[5],
        text: it[6]
      )
    }
  }
}
