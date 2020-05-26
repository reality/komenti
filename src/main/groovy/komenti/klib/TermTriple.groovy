package klib

class TermTriple {
  Term subject
  Term relation
  Term object

  String toString() {
    subject.label + " -> " + relation.label + " -> " + object.label
  }
}
