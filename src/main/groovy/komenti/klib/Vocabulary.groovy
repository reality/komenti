package klib

import java.util.concurrent.*

class Vocabulary implements Iterable<Label> {
  ConcurrentHashMap entities = [:]
  def labelPath

  Vocabulary(lbls, labelPath) {
    this(labelPath)
    entities = lbls.groupBy {it.iri }
            .collectEntries({ [(it.getKey()): it.getValue()] })
  }

  Vocabulary(labelPath) {
    this.labelPath = labelPath;
  }

  def add(iri, Label label) {
    if(label.label == '') { return; }
    if(!entities.containsKey(iri)) { entities[iri] = [] }
    if(!entities[iri].contains(label)) {
      entities[iri] << label
    }
  }

  def add(iri, List<Label> labels) {
    labels.each { label -> add(iri, label) } 
  }

  def termGroup(iri) {
    entities[iri][0].group
  }

  def termLabel(iri) {
    entities[iri][0].label
  }

  def labelIri(label) {
    def e = entities.find { k, v -> v.find { it.label == label }  }
    if(e) { return e.getKey() }
  }

  def entityLabels(iri) {
    entities[iri].collect { it.label }
  }

  @Override
  Iterator<Annotation> iterator() {
    entities.iterator()
  }

  def write(append) {
    def newText = entities.collect { iri, labels ->
      labels.collect { l -> l.toString() }
    }.flatten().join('\n')

    if(labelPath) {
      def labelFile = new File(labelPath)
      if(append) {
        labelFile.text += '\n' + newText
      } else {
        labelFile.text = newText
      }
    } else {
      println newText
    }
  }

  def labelSize() {
    entities.collect { it.getValue().size() }.sum()
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

    new Vocabulary(lbls, fileName)
  }
}
