package klib

class Vocabulary implements Iterable<Label> {
  def entities = [:]
  File labelFile

  Vocabulary(lbls, labelFile) {
    entities = lbls.groupBy {it.iri }
            .collectEntries({ [(it.getKey()): it.getValue()] })
    this.labelFile = labelFile
  }

  def termGroup(iri) {
    entities[iri][0].group
  }

  def entityLabels(iri) {
    entities[iri].collect { it.label }
  }

  @Override
  Iterator<Annotation> iterator() {
    entities.iterator()
  }

  static def loadFile(fileName) {
    def lbls = new File(fileName).text.split('\n').collect {
      it = it.split('\t')
      new Label(
        label: it[0].replaceAll('\\\\',''),
        iri: it[1],
        group: it[2],
        ontology: it[3],
        priority: it[4]
      )
    }

    new Vocabulary(lbls, new File(fileName))
  }
}
