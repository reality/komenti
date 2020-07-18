package klib

class Term implements Serializable {
  Term parentTerm
  def iri
  def label
  def specificLabel
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
    this(iri, label)
    this.parentTerm = parentTerm
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
      parentTerm.getLabel() + " " + label.toString()
    } else {
      label.toString()
    }
  }

  String getSpecificLabel() {
    label
  }
}
