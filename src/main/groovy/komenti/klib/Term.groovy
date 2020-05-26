package klib

class Term {
  def iri
  def label
  Annotation originalAnnotation

  Term(iri, label) {
    this.iri = iri
    this.label = label
  }

  Term(iri, label, originalAnnotation) {
    this(iri, label)
    this.originalAnnotation = originalAnnotation
  }

  static Term fromAnnotation(Annotation a) {
    new Term(a.termIri, a.conceptLabel, a)
  }

  String toString() {
    "$label<$iri>"
  }
}
