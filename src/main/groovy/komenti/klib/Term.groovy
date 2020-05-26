package klib

class Term {
  Term parentTerm
  def iri
  def label
  Annotation originalAnnotation

  Term(String iri, String label) {
    this.iri = iri
    this.label = label
  }

  Term(Term parentTerm, Term specifiedTerm) {
    this(specifiedTerm.iri, specifiedTerm.label)
    this.parentTerm = parentTerm 
  }

  Term(Term parentTerm, String iri, String label) {
    this(parentTerm, new Term(label, iri))
  }

  Term(String iri, String label, Annotation originalAnnotation) {
    this(iri, label)
    this.originalAnnotation = originalAnnotation
  }

  static Term fromAnnotation(Annotation a) {
    new Term(a.termIri, a.conceptLabel, a)
  }

  String toString() {
    if(parentTerm) {
      parentTerm.toString() + " $label<$iri>"
    } else {
      "$label<$iri>"
    }
  }

  String getLabel() {
    if(parentTerm) {
      parentTerm.getLabel() + " " + label
    } else {
      label
    }
  }
}
